package com.dietpizza.rxplayer.core.netfs.ftp

import com.dietpizza.rxplayer.core.netfs.RemotePath
import com.dietpizza.rxplayer.core.netfs.fs.BaseRemoteFileSystem
import com.dietpizza.rxplayer.core.netfs.fs.NetErrorKind
import com.dietpizza.rxplayer.core.netfs.fs.NetFsException
import com.dietpizza.rxplayer.core.netfs.fs.RemoteRandomAccess
import com.dietpizza.rxplayer.core.netfs.fs.StreamingRandomAccess
import com.dietpizza.rxplayer.core.netfs.model.ConnectionSpec
import com.dietpizza.rxplayer.core.netfs.model.NetProtocol
import com.dietpizza.rxplayer.core.netfs.model.RemoteEntry
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.time.Duration
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPConnectionClosedException
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient

/**
 * FTP / explicit FTPS (AUTH TLS) via commons-net, passive by default.
 *
 * An FTP control connection does one thing at a time, so browsing uses one serialised client and
 * every open file gets its own connection (see [FtpRandomAccess]). Seeking uses `REST <offset>`
 * before `RETR`; a backwards or long jump aborts the current transfer and restarts it.
 */
internal class FtpFileSystem(private val spec: ConnectionSpec) : BaseRemoteFileSystem() {

    override val serializeOperations: Boolean = true

    private var client: FTPClient? = null
    private var mlsd: Boolean? = null

    override fun ensureConnected(): Boolean {
        val c = client
        if (c != null && c.isConnected) return false
        disconnect()
        val fresh = connect(spec)
        client = fresh
        mlsd = runCatching { fresh.hasFeature("MLSD") }.getOrDefault(false)
        return true
    }

    override fun disconnect() {
        client?.let(::quietly)
        client = null
    }

    override fun isConnectionLost(e: Throwable): Boolean =
        e is FTPConnectionClosedException || (e is IOException && e !is NetFsException && e !is FileNotFoundException)

    private fun c(): FTPClient = client ?: throw IOException("Not connected")

    override fun doList(path: String): List<RemoteEntry> {
        val c = c()
        if (!c.changeWorkingDirectory(path)) throw notFound(c, path)
        val files: Array<FTPFile> = if (mlsd == true) c.mlistDir() else c.listFiles()
        if (!FTPReply.isPositiveCompletion(c.replyCode)) throw IOException(c.replyString?.trim())
        return files.mapNotNull { f ->
            val name = f.name ?: return@mapNotNull null
            if (name.isBlank() || name == "." || name == ".." || '/' in name) return@mapNotNull null
            RemoteEntry(
                name = name,
                path = RemotePath.join(path, name),
                isDirectory = f.isDirectory,
                size = if (f.isDirectory) -1 else f.size,
                modifiedMs = f.timestamp?.timeInMillis ?: 0L,
            )
        }
    }

    override fun doStat(path: String): RemoteEntry {
        val c = c()
        val parent = RemotePath.parent(path) ?: return RemoteEntry("", RemotePath.ROOT, true, -1, 0)
        val name = RemotePath.name(path)
        return doList(parent).firstOrNull { it.name == name } ?: throw notFound(c, path)
    }

    override fun doOpen(path: String): RemoteRandomAccess {
        val size = c().let { c ->
            runCatching { c.getSize(path)?.trim()?.toLongOrNull() }.getOrNull()
                ?: runCatching { doStat(path).size }.getOrDefault(-1L)
        }
        return FtpRandomAccess(spec, path, size)
    }

    override fun doRename(path: String, newPath: String) {
        val c = c()
        if (!c.rename(path, newPath)) throw failed(c)
    }

    override fun doDeleteFile(path: String) {
        val c = c()
        if (!c.deleteFile(path)) throw failed(c)
    }

    override fun doRemoveEmptyDirectory(path: String) {
        val c = c()
        if (!c.removeDirectory(path)) throw failed(c)
    }

    private fun notFound(c: FTPClient, path: String) =
        FileNotFoundException("$path: ${c.replyString?.trim().orEmpty()}")

    private fun failed(c: FTPClient): IOException {
        val reply = c.replyString?.trim().orEmpty()
        return when (c.replyCode) {
            550 -> NetFsException(NetErrorKind.PERMISSION, reply)
            530 -> NetFsException(NetErrorKind.AUTH, reply)
            else -> IOException(reply)
        }
    }

    /**
     * One file on its own control connection. `REST` + `RETR` opens the data stream at an offset;
     * leaving a transfer early closes the data socket and reads the 426/226 reply. If the server
     * gets confused (some do), the connection is dropped and the next read reconnects.
     */
    private class FtpRandomAccess(
        private val spec: ConnectionSpec,
        private val path: String,
        override val length: Long,
    ) : StreamingRandomAccess() {

        private var client: FTPClient? = null

        override fun openStream(offset: Long): InputStream {
            repeat(2) { attempt ->
                val c = client?.takeIf { it.isConnected } ?: connect(spec).also { client = it }
                c.setRestartOffset(offset)
                val stream = try {
                    c.retrieveFileStream(path)
                } catch (e: IOException) {
                    if (attempt == 1) throw e
                    null
                }
                if (stream != null) return stream
                val code = c.replyCode
                if (attempt == 1 || code == 550) {
                    throw if (code == 550) FileNotFoundException(c.replyString?.trim()) else IOException(c.replyString?.trim())
                }
                // Stale reply or dropped control connection: start over on a fresh one.
                quietly(c)
                client = null
            }
            throw IOException("Unreachable")
        }

        override fun closeStream(stream: InputStream) {
            val c = client
            try {
                stream.close()
                if (c != null && !c.completePendingCommand()) {
                    // 426 "transfer aborted" is the normal answer to closing early; anything odd
                    // and the next command could read a stale reply, so drop the connection.
                    if (c.replyCode != 426 && c.replyCode != 451 && c.replyCode != 226) dropClient()
                }
            } catch (_: IOException) {
                dropClient()
            }
        }

        private fun dropClient() {
            client?.let(::quietly)
            client = null
        }

        override fun release() = dropClient()
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 20_000

        fun connect(spec: ConnectionSpec): FTPClient {
            val tls = spec.protocol == NetProtocol.FTPS
            val c = if (tls) FTPSClient(false) else FTPClient()
            c.connectTimeout = CONNECT_TIMEOUT_MS
            c.defaultTimeout = READ_TIMEOUT_MS
            c.setDataTimeout(Duration.ofMillis(READ_TIMEOUT_MS.toLong()))
            c.controlEncoding = "UTF-8"
            c.setAutodetectUTF8(true)
            try {
                c.connect(spec.host, spec.port)
                if (!FTPReply.isPositiveCompletion(c.replyCode)) throw IOException(c.replyString?.trim())
                c.soTimeout = READ_TIMEOUT_MS
                val anonymous = spec.options.anonymous || spec.username.isNullOrEmpty()
                val user = if (anonymous) "anonymous" else spec.username
                val pass = if (anonymous) "rx@" else spec.password.orEmpty()
                if (!c.login(user, pass)) throw NetFsException(NetErrorKind.AUTH, c.replyString?.trim() ?: "Login failed")
                if (c is FTPSClient) {
                    c.execPBSZ(0)
                    c.execPROT("P")
                }
                if (!c.setFileType(FTP.BINARY_FILE_TYPE)) throw IOException(c.replyString?.trim())
                if (spec.options.passive) c.enterLocalPassiveMode() else c.enterLocalActiveMode()
                return c
            } catch (e: Exception) {
                quietly(c)
                throw e
            }
        }

        /** Closes the sockets without a polite QUIT, which could block on a confused server. */
        fun quietly(c: FTPClient) {
            runCatching { c.disconnect() }
        }
    }
}
