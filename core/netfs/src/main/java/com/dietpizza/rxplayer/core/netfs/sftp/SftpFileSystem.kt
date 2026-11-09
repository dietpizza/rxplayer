package com.dietpizza.rxplayer.core.netfs.sftp

import com.dietpizza.rxplayer.core.netfs.RemotePath
import com.dietpizza.rxplayer.core.netfs.fs.BaseRemoteFileSystem
import com.dietpizza.rxplayer.core.netfs.fs.HostKeyMismatchException
import com.dietpizza.rxplayer.core.netfs.fs.NetErrorKind
import com.dietpizza.rxplayer.core.netfs.fs.NetFsException
import com.dietpizza.rxplayer.core.netfs.fs.RemoteRandomAccess
import com.dietpizza.rxplayer.core.netfs.fs.StreamingRandomAccess
import com.dietpizza.rxplayer.core.netfs.model.ConnectionSpec
import com.dietpizza.rxplayer.core.netfs.model.RemoteEntry
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Security
import java.util.Base64
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.connection.ConnectionException
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.RemoteFile
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive
import net.schmizz.sshj.userauth.method.AuthPassword
import net.schmizz.sshj.userauth.method.PasswordResponseProvider
import net.schmizz.sshj.userauth.password.PasswordUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider

/**
 * SFTP via sshj. One SSH connection + SFTP channel per server, shared by browsing and every open
 * file (SFTP multiplexes requests by id).
 *
 * Host keys are trust-on-first-use: with no stored fingerprint the key is accepted and reported
 * through [onNewHostKey]; with one stored, any other key fails with [HostKeyMismatchException].
 */
internal class SftpFileSystem(
    private val spec: ConnectionSpec,
    private val onNewHostKey: (fingerprint: String) -> Unit,
) : BaseRemoteFileSystem() {

    private var ssh: SSHClient? = null
    private var sftp: SFTPClient? = null

    @Volatile
    private var trustedKey: String? = spec.options.hostKey

    override fun ensureConnected(): Boolean {
        val s = ssh
        if (s != null && s.isConnected && s.isAuthenticated && sftp != null) return false
        disconnect()
        BouncyCastle.install()
        val client = SSHClient(DefaultConfig())
        client.connectTimeout = CONNECT_TIMEOUT_MS
        client.timeout = READ_TIMEOUT_MS
        var mismatch: HostKeyMismatchException? = null
        client.addHostKeyVerifier(
            object : HostKeyVerifier {
                override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                    val actual = fingerprint(key)
                    val expected = trustedKey
                    return when {
                        expected == null -> {
                            trustedKey = actual
                            onNewHostKey(actual)
                            true
                        }
                        expected == actual -> true
                        else -> {
                            mismatch = HostKeyMismatchException(expected, actual)
                            false
                        }
                    }
                }

                override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
            },
        )
        try {
            client.connect(spec.host, spec.port)
        } catch (e: TransportException) {
            runCatching { client.close() }
            throw mismatch ?: e
        } catch (e: Exception) {
            runCatching { client.close() }
            throw e
        }
        try {
            val user = spec.username
            if (user.isNullOrEmpty()) throw NetFsException(NetErrorKind.CONFIG, "SFTP needs a username")
            val password = spec.password.orEmpty()
            // Many servers only offer keyboard-interactive for passwords (PAM); try both.
            client.auth(
                user,
                AuthPassword(PasswordUtils.createOneOff(password.toCharArray())),
                AuthKeyboardInteractive(PasswordResponseProvider(PasswordUtils.createOneOff(password.toCharArray()))),
            )
            sftp = client.newSFTPClient()
            ssh = client
        } catch (e: Exception) {
            runCatching { client.close() }
            throw e
        }
        return true
    }

    override fun disconnect() {
        runCatching { sftp?.close() }
        sftp = null
        runCatching { ssh?.disconnect() }
        ssh = null
    }

    override fun isConnectionLost(e: Throwable): Boolean =
        e is TransportException || e is ConnectionException ||
            (e is IOException && e !is NetFsException && e !is net.schmizz.sshj.sftp.SFTPException &&
                e !is net.schmizz.sshj.userauth.UserAuthException)

    private fun client(): SFTPClient = sftp ?: throw IOException("Not connected")

    override fun doList(path: String): List<RemoteEntry> {
        val c = client()
        return c.ls(path).mapNotNull { info ->
            val name = info.name
            if (name == "." || name == "..") return@mapNotNull null
            val childPath = RemotePath.join(path, name)
            var attrs = info.attributes
            if (attrs.type == FileMode.Type.SYMLINK) {
                attrs = runCatching { c.stat(childPath) }.getOrNull() ?: attrs
            }
            val dir = attrs.type == FileMode.Type.DIRECTORY
            RemoteEntry(
                name = name,
                path = childPath,
                isDirectory = dir,
                size = if (dir) -1 else attrs.size,
                modifiedMs = attrs.mtime * 1000,
            )
        }
    }

    override fun doStat(path: String): RemoteEntry {
        val attrs = client().stat(path)
        val dir = attrs.type == FileMode.Type.DIRECTORY
        return RemoteEntry(RemotePath.name(path), path, dir, if (dir) -1 else attrs.size, attrs.mtime * 1000)
    }

    override fun doOpen(path: String): RemoteRandomAccess {
        val file = client().open(path)
        return try {
            SftpRandomAccess(file, file.length())
        } catch (e: Exception) {
            runCatching { file.close() }
            throw e
        }
    }

    override fun doRename(path: String, newPath: String) = client().rename(path, newPath)

    override fun doDeleteFile(path: String) = client().rm(path)

    override fun doRemoveEmptyDirectory(path: String) = client().rmdir(path)

    /** Reads through sshj's read-ahead stream: [READ_AHEAD] requests in flight hide the RTT. */
    private class SftpRandomAccess(private val file: RemoteFile, override val length: Long) : StreamingRandomAccess() {
        override fun openStream(offset: Long): InputStream = file.ReadAheadRemoteFileInputStream(READ_AHEAD, offset)

        override fun release() {
            runCatching { file.close() }
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val READ_AHEAD = 16

        /** OpenSSH-style `SHA256:<base64, no padding>` of the key blob. */
        fun fingerprint(key: PublicKey): String {
            val blob = Buffer.PlainBuffer().putPublicKey(key).compactData
            val digest = MessageDigest.getInstance("SHA-256").digest(blob)
            return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
        }
    }
}

/**
 * Android ships a stripped-down provider registered as "BC" (no X25519/Ed25519/ChaCha, ...). sshj
 * asks for algorithms from the provider *named* "BC", so swap in the full BouncyCastle under that
 * name. It's appended last, so it never becomes the default for other JCA lookups (Conscrypt and
 * AndroidKeyStore stay first).
 */
internal object BouncyCastle {
    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val current = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
            if (current == null || current.javaClass != BouncyCastleProvider::class.java) {
                Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
                Security.addProvider(BouncyCastleProvider())
            }
            SecurityUtils.setRegisterBouncyCastle(true)
            SecurityUtils.setSecurityProvider(BouncyCastleProvider.PROVIDER_NAME)
            installed = true
        }
    }
}
