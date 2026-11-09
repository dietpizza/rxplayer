package com.dietpizza.rxplayer.core.netfs.webdav

import com.dietpizza.rxplayer.core.netfs.RemotePath
import com.dietpizza.rxplayer.core.netfs.fs.BaseRemoteFileSystem
import com.dietpizza.rxplayer.core.netfs.fs.NetErrorKind
import com.dietpizza.rxplayer.core.netfs.fs.NetFsException
import com.dietpizza.rxplayer.core.netfs.fs.RemoteRandomAccess
import com.dietpizza.rxplayer.core.netfs.fs.StreamingRandomAccess
import com.dietpizza.rxplayer.core.netfs.model.ConnectionSpec
import com.dietpizza.rxplayer.core.netfs.model.RemoteEntry
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * WebDAV over OkHttp: `PROPFIND` (Depth 1) to list, `GET` + `Range` to read, `MOVE` / `DELETE`.
 * Basic auth is sent preemptively (one round trip per request, not two). OkHttp's connection
 * pool keeps the TCP/TLS connection warm between requests.
 *
 * Digest auth isn't supported (OkHttp has no built-in digest authenticator).
 */
internal class WebDavFileSystem(spec: ConnectionSpec, baseClient: OkHttpClient) : BaseRemoteFileSystem() {

    private val https = spec.options.https
    private val host = spec.host
    private val port = spec.port

    private val http: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .apply {
            val user = spec.username
            if (!spec.options.anonymous && !user.isNullOrEmpty()) {
                val header = Credentials.basic(user, spec.password.orEmpty(), Charsets.UTF_8)
                addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("Authorization", header).build())
                }
            }
        }
        .build()

    override fun ensureConnected(): Boolean = false // stateless; OkHttp pools connections

    override fun disconnect() = Unit

    override fun close() {
        super.close()
        http.connectionPool.evictAll()
    }

    // OkHttp already retried connection-level failures where it was safe to.
    override fun isConnectionLost(e: Throwable): Boolean = false

    fun url(path: String, directory: Boolean = false): HttpUrl {
        val b = HttpUrl.Builder()
            .scheme(if (https) "https" else "http")
            .host(host)
            .port(port)
        for (segment in RemotePath.segments(path)) b.addPathSegment(segment)
        if (directory) b.addPathSegment("")
        return b.build()
    }

    private fun propfind(path: String, depth: Int, directory: Boolean): List<DavResource> {
        val request = Request.Builder()
            .url(url(path, directory))
            .header("Depth", depth.toString())
            .method("PROPFIND", Propfind.BODY.toRequestBody(XML))
            .build()
        http.newCall(request).execute().use { response ->
            check(response, path)
            val body = response.body.byteStream()
            return Propfind.parse(body)
        }
    }

    override fun doList(path: String): List<RemoteEntry> {
        val self = RemotePath.normalize(path)
        return propfind(path, depth = 1, directory = true).mapNotNull { res ->
            val resPath = RemotePath.normalize(res.path)
            if (resPath == self) return@mapNotNull null
            // Build the child path from the listed folder + the href's last segment, so servers
            // whose hrefs carry a different prefix (proxies, mounts) still map back correctly.
            val name = RemotePath.name(resPath)
            if (name.isEmpty()) return@mapNotNull null
            RemoteEntry(
                name = name,
                path = RemotePath.join(self, name),
                isDirectory = res.isCollection,
                size = res.contentLength,
                modifiedMs = res.lastModifiedMs,
            )
        }
    }

    override fun doStat(path: String): RemoteEntry {
        val res = propfind(path, depth = 0, directory = false).firstOrNull()
            ?: throw FileNotFoundException(path)
        return RemoteEntry(RemotePath.name(path), path, res.isCollection, res.contentLength, res.lastModifiedMs)
    }

    override fun doOpen(path: String): RemoteRandomAccess {
        val length = runCatching { doStat(path).size }.getOrElse { e ->
            if (e is FileNotFoundException || (e is NetFsException && e.kind != NetErrorKind.OTHER)) throw e
            -1L
        }
        return DavRandomAccess(path, length)
    }

    override fun doRename(path: String, newPath: String) {
        val request = Request.Builder()
            .url(url(path))
            .header("Destination", url(newPath).toString())
            .header("Overwrite", "F")
            .method("MOVE", null)
            .build()
        http.newCall(request).execute().use { check(it, path) }
    }

    override fun doDeleteFile(path: String) = davDelete(path, directory = false)

    // DELETE on a collection is recursive by spec.
    override fun doDeleteDirectory(path: String) = davDelete(path, directory = true)

    override fun doRemoveEmptyDirectory(path: String) = davDelete(path, directory = true)

    private fun davDelete(path: String, directory: Boolean) {
        val request = Request.Builder().url(url(path, directory)).delete().build()
        http.newCall(request).execute().use { check(it, path) }
    }

    private fun check(response: Response, path: String) {
        if (response.isSuccessful) return
        val msg = "${response.code} ${response.message}".trim()
        throw when (response.code) {
            401 -> NetFsException(NetErrorKind.AUTH, msg)
            403 -> NetFsException(NetErrorKind.PERMISSION, msg)
            404, 410 -> FileNotFoundException("$path: $msg")
            412 -> NetFsException(NetErrorKind.PERMISSION, "A file with that name already exists")
            else -> IOException(msg)
        }
    }

    /**
     * Open-ended `Range: bytes=<offset>-` requests; a seek cancels the response and issues a new one.
     * A server that ignores Range (200) is handled by skipping up to the offset.
     */
    private inner class DavRandomAccess(private val path: String, initialLength: Long) : StreamingRandomAccess() {
        @Volatile
        private var knownLength = initialLength
        private var response: Response? = null

        override val length: Long get() = knownLength

        override fun openStream(offset: Long): InputStream {
            response?.close()
            response = null
            val request = Request.Builder()
                .url(url(path))
                .header("Range", "bytes=$offset-")
                .header("Accept-Encoding", "identity")
                .get()
                .build()
            val r = http.newCall(request).execute()
            try {
                when (r.code) {
                    206 -> parseTotal(r.header("Content-Range"))?.let { knownLength = it }
                    200 -> {
                        r.body.contentLength().takeIf { it >= 0 }?.let { knownLength = it }
                        if (offset > 0) r.body.source().skip(offset) // EOFException if short
                    }
                    416 -> {
                        r.close()
                        return InputStream.nullInputStream()
                    }
                    else -> check(r, path)
                }
            } catch (e: Exception) {
                r.close()
                throw e
            }
            response = r
            return r.body.byteStream()
        }

        override fun closeStream(stream: InputStream) {
            response?.close()
            response = null
        }

        override fun release() {
            response?.close()
            response = null
        }
    }

    companion object {
        private val XML = "application/xml; charset=utf-8".toMediaType()

        /** `bytes 0-99/1234` → 1234; `*` or malformed → null. */
        fun parseTotal(contentRange: String?): Long? =
            contentRange?.substringAfterLast('/', "")?.trim()?.toLongOrNull()
    }
}
