package com.dietpizza.rxplayer.core.netfs.fs

import com.dietpizza.rxplayer.core.netfs.RemotePath
import com.dietpizza.rxplayer.core.netfs.model.RemoteEntry
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/**
 * One server, one protocol. Implementations keep their connection open between calls (browsing
 * and seeking must not reconnect each time) and reconnect transparently once if it dropped.
 *
 * Suspending calls run on [Dispatchers.IO]. [openRandomAccess] is blocking: it's called from
 * Media3 loader threads.
 */
interface RemoteFileSystem : Closeable {
    suspend fun list(path: String): List<RemoteEntry>

    suspend fun stat(path: String): RemoteEntry

    /** Opens [path] for reads at arbitrary offsets. Blocking; the caller closes the handle. */
    fun openRandomAccess(path: String): RemoteRandomAccess

    /** Renames within the same folder; returns the new path. */
    suspend fun rename(path: String, newName: String): String

    /** Permanently deletes a file, or a folder and everything in it. */
    suspend fun delete(path: String, isDirectory: Boolean)

    /** Connects (fresh), authenticates and lists the base path; returns its entry count. */
    suspend fun testConnection(basePath: String): Int
}

/**
 * A read-at-offset handle on one remote file. Not thread-safe: one reader at a time (each Media3
 * DataSource gets its own). Reads at the position right after the previous read are cheap
 * sequential reads; any other position repositions (protocol-specific cost).
 */
interface RemoteRandomAccess : Closeable {
    /** File length in bytes, or -1 if the server didn't say. */
    val length: Long

    /**
     * Reads up to [length] bytes at file offset [position] into [buffer] at [offset].
     * Returns the count read (> 0), or -1 at end of file. May return fewer bytes than asked.
     */
    fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int
}

/**
 * Shared plumbing: a lazily (re)connected session, with every operation retried once on a fresh
 * connection when the old one turned out to be dead (servers drop idle sessions).
 *
 * Subclasses implement blocking `do*` methods and the connection lifecycle.
 */
abstract class BaseRemoteFileSystem : RemoteFileSystem {

    /** Guards connect/disconnect, and every operation when [serializeOperations] is true. */
    protected val lock = ReentrantLock()

    /** FTP can only do one thing at a time on its control connection. */
    protected open val serializeOperations: Boolean = false

    @Volatile
    private var closed = false

    /** Connect if not connected (called under [lock]). Returns true if it just connected. */
    protected abstract fun ensureConnected(): Boolean

    /** Drop the connection quietly (called under [lock]). */
    protected abstract fun disconnect()

    /** True when [e] means the connection is gone and a reconnect may help. */
    protected abstract fun isConnectionLost(e: Throwable): Boolean

    protected abstract fun doList(path: String): List<RemoteEntry>
    protected abstract fun doStat(path: String): RemoteEntry
    protected abstract fun doOpen(path: String): RemoteRandomAccess
    protected abstract fun doRename(path: String, newPath: String)
    protected abstract fun doDeleteFile(path: String)

    /** Default: depth-first delete of children, then the folder. Override where the protocol recurses. */
    protected open fun doDeleteDirectory(path: String) {
        for (child in doList(path)) {
            if (child.isDirectory) doDeleteDirectory(child.path) else doDeleteFile(child.path)
        }
        doRemoveEmptyDirectory(path)
    }

    protected abstract fun doRemoveEmptyDirectory(path: String)

    final override suspend fun list(path: String): List<RemoteEntry> = io { doList(RemotePath.normalize(path)) }

    final override suspend fun stat(path: String): RemoteEntry = io { doStat(RemotePath.normalize(path)) }

    final override fun openRandomAccess(path: String): RemoteRandomAccess = blocking { doOpen(RemotePath.normalize(path)) }

    final override suspend fun rename(path: String, newName: String): String {
        require(RemotePath.isValidName(newName)) { "Invalid name" }
        val from = RemotePath.normalize(path)
        val parent = RemotePath.parent(from) ?: throw IOException("Can't rename the root")
        val to = RemotePath.join(parent, newName)
        if (to == from) return from
        io { doRename(from, to) }
        return to
    }

    final override suspend fun delete(path: String, isDirectory: Boolean) = io {
        val p = RemotePath.normalize(path)
        if (isDirectory) doDeleteDirectory(p) else doDeleteFile(p)
    }

    final override suspend fun testConnection(basePath: String): Int = io {
        lock.withLock { disconnect() }
        doList(RemotePath.normalize(basePath)).size
    }

    override fun close() {
        closed = true
        lock.withLock { disconnect() }
    }

    protected suspend fun <T> io(block: () -> T): T = runInterruptible(Dispatchers.IO) { blocking(block) }

    protected fun <T> blocking(block: () -> T): T {
        if (closed) throw IOException("Connection closed")
        var fresh = false
        return try {
            attempt(block) { fresh = it }
        } catch (e: Exception) {
            // Retry only a connection that was reused and turned out dead; a fresh connection
            // failing (wrong host, auth) would just fail again, twice as slowly.
            if (fresh || !isConnectionLost(e)) throw e
            lock.withLock { disconnect() }
            attempt(block) {}
        }
    }

    private inline fun <T> attempt(block: () -> T, onConnected: (fresh: Boolean) -> Unit): T =
        if (serializeOperations) {
            lock.withLock {
                onConnected(ensureConnected())
                block()
            }
        } else {
            onConnected(lock.withLock { ensureConnected() })
            block()
        }
}
