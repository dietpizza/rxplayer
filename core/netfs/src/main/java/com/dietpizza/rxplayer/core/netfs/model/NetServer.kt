package com.dietpizza.rxplayer.core.netfs.model

/**
 * Network protocols (plan §7.2 `network_server.protocol`). [scheme] is the media-key scheme
 * (see [com.dietpizza.rxplayer.core.netfs.NetKey]); it never changes for a saved server unless the
 * protocol itself changes, so resume positions survive edits to host/port/https.
 */
enum class NetProtocol(val scheme: String, val label: String) {
    SMB("smb", "SMB"),
    SFTP("sftp", "SFTP"),
    FTP("ftp", "FTP"),
    FTPS("ftps", "FTPS"),
    WEBDAV("webdav", "WebDAV"),
    ;

    fun defaultPort(https: Boolean = false): Int = when (this) {
        SMB -> 445
        SFTP -> 22
        FTP, FTPS -> 21
        WEBDAV -> if (https) 443 else 80
    }

    companion object {
        fun fromName(name: String): NetProtocol? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Per-protocol extras, stored as `network_server.options_json`.
 * @property hostKey SFTP host-key fingerprint (`SHA256:<base64>`), recorded on first connect
 *   (trust on first use). A later mismatch fails the connection.
 */
data class ServerOptions(
    val anonymous: Boolean = false,
    val domain: String? = null,
    val passive: Boolean = true,
    val https: Boolean = false,
    val hostKey: String? = null,
)

/** A saved server, without its secret. */
data class NetServer(
    val id: Long,
    val protocol: NetProtocol,
    val name: String,
    val host: String,
    val port: Int,
    /** Normalised absolute path the browser starts at (and can't go above). SMB: `/share[/dir]`. */
    val basePath: String,
    val username: String?,
    val hasPassword: Boolean,
    val options: ServerOptions,
    /** Epoch ms, 0 = never. */
    val lastUsedAt: Long,
) {
    val displayName: String get() = name.ifBlank { host }
}

/** What to do with the stored password when saving a [ServerDraft]. */
sealed interface PasswordChange {
    data object Keep : PasswordChange
    data object Clear : PasswordChange
    class Set(val password: String) : PasswordChange {
        override fun toString(): String = "Set(***)"
    }
}

/** An add/edit form result. [id] 0 = new server. */
data class ServerDraft(
    val id: Long,
    val protocol: NetProtocol,
    val name: String,
    val host: String,
    val port: Int,
    val basePath: String,
    val username: String?,
    val password: PasswordChange,
    val options: ServerOptions,
)

/**
 * Everything a client needs to connect, password included. Lives only in memory;
 * [toString] never prints the password.
 */
class ConnectionSpec(
    val protocol: NetProtocol,
    val host: String,
    val port: Int,
    val basePath: String,
    val username: String?,
    val password: String?,
    val options: ServerOptions,
) {
    override fun toString(): String =
        "ConnectionSpec($protocol, $host:$port$basePath, user=${username ?: "-"}, password=${if (password != null) "***" else "-"})"
}
