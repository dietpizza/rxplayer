package com.dietpizza.rxplayer.core.netfs

import com.dietpizza.rxplayer.core.netfs.fs.NetErrorKind
import com.dietpizza.rxplayer.core.netfs.fs.NetFsException
import com.dietpizza.rxplayer.core.netfs.fs.RemoteFileSystem
import com.dietpizza.rxplayer.core.netfs.fs.RemoteRandomAccess
import com.dietpizza.rxplayer.core.netfs.ftp.FtpFileSystem
import com.dietpizza.rxplayer.core.netfs.model.ConnectionSpec
import com.dietpizza.rxplayer.core.netfs.model.NetProtocol
import com.dietpizza.rxplayer.core.netfs.sftp.SftpFileSystem
import com.dietpizza.rxplayer.core.netfs.smb.SmbFileSystem
import com.dietpizza.rxplayer.core.netfs.webdav.WebDavFileSystem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** Builds a client for a [ConnectionSpec]. */
@Singleton
class RemoteFileSystemFactory @Inject constructor() {
    private val okHttp by lazy { OkHttpClient() }

    /** @param onNewHostKey SFTP only: called with the fingerprint trusted on first use. */
    fun create(spec: ConnectionSpec, onNewHostKey: (String) -> Unit = {}): RemoteFileSystem = when (spec.protocol) {
        NetProtocol.SMB -> SmbFileSystem(spec)
        NetProtocol.SFTP -> SftpFileSystem(spec, onNewHostKey)
        NetProtocol.FTP, NetProtocol.FTPS -> FtpFileSystem(spec)
        NetProtocol.WEBDAV -> WebDavFileSystem(spec, okHttp)
    }
}

/**
 * One live [RemoteFileSystem] per saved server, shared by the browser and playback, so browsing
 * and seeking reuse the connection. Clients are dropped when their server is edited or deleted.
 *
 * Also keeps recently released file handles for a few seconds: Media3 closes and reopens its
 * DataSource on every seek, and reusing the handle (open SMB file / SFTP handle / FTP session)
 * makes that cheap.
 */
@Singleton
class NetFsPool @Inject constructor(
    private val servers: ServerRepository,
    private val factory: RemoteFileSystemFactory,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val systems = HashMap<Long, RemoteFileSystem>()
    private val idle = ArrayDeque<IdleHandle>()
    private var evictJob: Job? = null

    init {
        scope.launch { servers.changes.collect { invalidate(it) } }
    }

    suspend fun get(serverId: Long): RemoteFileSystem {
        synchronized(this) { systems[serverId] }?.let { return it }
        val spec = servers.connectionSpec(serverId) ?: throw serverGone(serverId)
        return install(serverId, spec)
    }

    /** Blocking [get] for loader threads. */
    fun getBlocking(serverId: Long): RemoteFileSystem {
        synchronized(this) { systems[serverId] }?.let { return it }
        val spec = servers.connectionSpecBlocking(serverId) ?: throw serverGone(serverId)
        return install(serverId, spec)
    }

    private fun install(serverId: Long, spec: ConnectionSpec): RemoteFileSystem {
        val created = factory.create(spec) { fingerprint ->
            scope.launch { servers.rememberHostKey(serverId, fingerprint) }
        }
        val winner = synchronized(this) { systems.getOrPut(serverId) { created } }
        if (winner !== created) created.close()
        return winner
    }

    /** Closes and forgets the server's client and idle handles (config changed or deleted). */
    fun invalidate(serverId: Long) {
        val (fs, handles) = synchronized(this) {
            val handles = idle.filter { it.serverId == serverId }
            idle.removeAll { it.serverId == serverId }
            systems.remove(serverId) to handles
        }
        handles.forEach { runCatching { it.handle.close() } }
        fs?.let { runCatching { it.close() } }
    }

    /** A handle on [path], reusing a recently released one if there is. Blocking. */
    fun acquireHandle(serverId: Long, path: String): RemoteRandomAccess {
        synchronized(this) {
            val i = idle.indexOfFirst { it.serverId == serverId && it.path == path }
            if (i >= 0) return idle.removeAt(i).handle
        }
        return getBlocking(serverId).openRandomAccess(path)
    }

    /** Returns a healthy handle for reuse; pass `reusable = false` after an I/O error. */
    fun releaseHandle(serverId: Long, path: String, handle: RemoteRandomAccess, reusable: Boolean) {
        if (!reusable) {
            runCatching { handle.close() }
            return
        }
        val evicted = synchronized(this) {
            idle.addLast(IdleHandle(serverId, path, handle, System.nanoTime()))
            val over = ArrayList<IdleHandle>()
            while (idle.size > MAX_IDLE_HANDLES) over += idle.removeFirst()
            if (evictJob?.isActive != true) {
                evictJob = scope.launch {
                    while (true) {
                        delay(IDLE_HANDLE_MS / 2)
                        if (evictExpired()) break
                    }
                }
            }
            over
        }
        evicted.forEach { runCatching { it.handle.close() } }
    }

    /** Closes expired idle handles; true when none are left. */
    private fun evictExpired(): Boolean {
        val now = System.nanoTime()
        val (expired, empty) = synchronized(this) {
            val expired = idle.filter { now - it.releasedAt > IDLE_HANDLE_MS * 1_000_000 }
            idle.removeAll(expired)
            expired to idle.isEmpty()
        }
        expired.forEach { runCatching { it.handle.close() } }
        return empty
    }

    private fun serverGone(id: Long) = NetFsException(NetErrorKind.CONFIG, "Server $id no longer exists")

    private class IdleHandle(val serverId: Long, val path: String, val handle: RemoteRandomAccess, val releasedAt: Long)

    private companion object {
        const val MAX_IDLE_HANDLES = 4
        const val IDLE_HANDLE_MS = 15_000L
    }
}
