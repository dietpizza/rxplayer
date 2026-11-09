package com.dietpizza.rxplayer.core.netfs.smb

import com.dietpizza.rxplayer.core.netfs.RemotePath
import com.dietpizza.rxplayer.core.netfs.fs.BaseRemoteFileSystem
import com.dietpizza.rxplayer.core.netfs.fs.NetErrorKind
import com.dietpizza.rxplayer.core.netfs.fs.NetFsException
import com.dietpizza.rxplayer.core.netfs.fs.RemoteRandomAccess
import com.dietpizza.rxplayer.core.netfs.model.ConnectionSpec
import com.dietpizza.rxplayer.core.netfs.model.RemoteEntry
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileStandardInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.protocol.transport.TransportException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.common.SMBRuntimeException
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import java.io.IOException
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * SMB2/3 via smbj. Paths are `/<share>/<path inside share>`; the first segment picks the share
 * (smbj can't enumerate shares, so the base path must name one). One TCP connection + session is
 * shared by browsing and every open file; smbj multiplexes requests over it.
 */
internal class SmbFileSystem(private val spec: ConnectionSpec) : BaseRemoteFileSystem() {

    private val client = SMBClient(
        SmbConfig.builder()
            .withTimeout(OP_TIMEOUT_S, TimeUnit.SECONDS)
            .withSoTimeout(SO_TIMEOUT_S, TimeUnit.SECONDS)
            .build(),
    )
    private var connection: Connection? = null
    private var session: Session? = null
    private val shares = ConcurrentHashMap<String, DiskShare>()

    override fun ensureConnected(): Boolean {
        val c = connection
        if (c != null && c.isConnected && session != null) return false
        disconnect()
        val conn = client.connect(spec.host, spec.port)
        connection = conn
        session = authenticate(conn)
        return true
    }

    private fun authenticate(conn: Connection): Session {
        if (spec.options.anonymous || spec.username.isNullOrEmpty()) {
            return try {
                conn.authenticate(AuthenticationContext.anonymous())
            } catch (e: SMBApiException) {
                // Samba usually maps anonymous to guest; Windows needs the explicit guest login.
                conn.authenticate(AuthenticationContext.guest())
            }
        }
        val user = spec.username
        val password = (spec.password ?: "").toCharArray()
        // DOMAIN\user and user@domain both work without a separate domain field.
        val (domain, name) = when {
            !spec.options.domain.isNullOrBlank() -> spec.options.domain to user
            '\\' in user -> user.substringBefore('\\') to user.substringAfter('\\')
            else -> null to user
        }
        return try {
            conn.authenticate(AuthenticationContext(name, password, domain))
        } finally {
            password.fill('\u0000')
        }
    }

    override fun disconnect() {
        shares.values.forEach { runCatching { it.close() } }
        shares.clear()
        runCatching { session?.close() }
        session = null
        runCatching { connection?.close(true) }
        connection = null
    }

    override fun close() {
        super.close()
        runCatching { client.close() }
    }

    override fun isConnectionLost(e: Throwable): Boolean = when (e) {
        is SMBApiException -> e.status?.name in RECONNECT_STATUSES
        is TransportException, is SMBRuntimeException -> true
        is IOException -> e !is NetFsException
        is IllegalStateException -> true // smbj: "Connection is not connected" / session closed
        else -> false
    }

    /** Splits `/share/a/b` into the share and its `a\b` path. */
    private fun locate(path: String): Pair<DiskShare, String> {
        val segments = RemotePath.segments(path)
        if (segments.isEmpty()) {
            throw NetFsException(NetErrorKind.CONFIG, "Add a share name to the path, e.g. /Media")
        }
        val shareName = segments.first()
        val share = shares[shareName] ?: run {
            val s = session ?: throw IOException("Not connected")
            val connected = s.connectShare(shareName) as? DiskShare
                ?: throw NetFsException(NetErrorKind.CONFIG, "$shareName is not a disk share")
            shares[shareName] = connected
            connected
        }
        if (!share.isConnected) {
            shares.remove(shareName)
            throw TransportException("Share disconnected")
        }
        return share to segments.drop(1).joinToString("\\")
    }

    override fun doList(path: String): List<RemoteEntry> {
        val (share, p) = locate(path)
        return share.list(p).mapNotNull { info ->
            val name = info.fileName
            if (name == "." || name == "..") return@mapNotNull null
            val attrs = info.fileAttributes
            val dir = attrs and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
            RemoteEntry(
                name = name,
                path = RemotePath.join(path, name),
                isDirectory = dir,
                size = if (dir) -1 else info.endOfFile,
                modifiedMs = info.lastWriteTime.toEpochMillis(),
                hidden = name.startsWith('.') || attrs and FileAttributes.FILE_ATTRIBUTE_HIDDEN.value != 0L,
            )
        }
    }

    override fun doStat(path: String): RemoteEntry {
        val (share, p) = locate(path)
        val info = share.getFileInformation(p)
        val dir = info.standardInformation.isDirectory
        return RemoteEntry(
            name = RemotePath.name(path),
            path = path,
            isDirectory = dir,
            size = if (dir) -1 else info.standardInformation.endOfFile,
            modifiedMs = info.basicInformation.lastWriteTime.toEpochMillis(),
        )
    }

    override fun doOpen(path: String): RemoteRandomAccess {
        val (share, p) = locate(path)
        val file = share.openFile(
            p,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null,
        )
        return try {
            SmbRandomAccess(file, file.getFileInformation(FileStandardInformation::class.java).endOfFile)
        } catch (e: Exception) {
            runCatching { file.close() }
            throw e
        }
    }

    override fun doRename(path: String, newPath: String) {
        val (share, p) = locate(path)
        val (_, target) = locate(newPath)
        share.open(
            p,
            EnumSet.of(AccessMask.DELETE, AccessMask.FILE_READ_ATTRIBUTES),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null,
        ).use { it.rename(target, false) }
    }

    override fun doDeleteFile(path: String) {
        val (share, p) = locate(path)
        share.rm(p)
    }

    override fun doDeleteDirectory(path: String) {
        val (share, p) = locate(path)
        if (p.isEmpty()) throw NetFsException(NetErrorKind.PERMISSION, "Can't delete a share")
        share.rmdir(p, true)
    }

    override fun doRemoveEmptyDirectory(path: String) {
        val (share, p) = locate(path)
        share.rmdir(p, false)
    }

    private class SmbRandomAccess(private val file: File, override val length: Long) : RemoteRandomAccess {
        override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (position >= this.length) return -1
            // One SMB READ per call, capped by the negotiated max read size (64 KB – 8 MB).
            return file.read(buffer, position, offset, length)
        }

        override fun close() {
            runCatching { file.closeNoWait() }
        }
    }

    private companion object {
        const val OP_TIMEOUT_S = 20L
        const val SO_TIMEOUT_S = 30L
        val RECONNECT_STATUSES = setOf(
            "STATUS_NETWORK_NAME_DELETED",
            "STATUS_USER_SESSION_DELETED",
            "STATUS_NETWORK_SESSION_EXPIRED",
            "STATUS_CONNECTION_DISCONNECTED",
            "STATUS_CONNECTION_RESET",
            "STATUS_FILE_CLOSED",
        )
    }
}
