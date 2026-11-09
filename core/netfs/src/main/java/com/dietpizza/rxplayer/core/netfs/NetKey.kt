package com.dietpizza.rxplayer.core.netfs

import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.netfs.model.NetProtocol

/**
 * Media key for a network file (plan §7.1): `<scheme>://<serverId><path>`, e.g.
 * `smb://3/Movies/x.mkv`.
 *
 * Convention:
 * - The scheme is [NetProtocol.scheme]: `smb`, `sftp`, `ftp`, `ftps`, `webdav`. WebDAV keys are
 *   always `webdav` (https is a server option, so toggling it keeps resume positions);
 *   `webdavs` is accepted when parsing as an alias.
 * - The authority is the `network_server.id`. The server row, not the scheme, decides how to
 *   connect; the scheme exists for routing and for the `playback_state.source` column.
 * - The path is the absolute remote path, **raw, not URL-encoded** (like the `file://` keys), so
 *   it may contain spaces, `#`, `?` and `%`. Always parse with [parse] on the whole string
 *   (`uri.toString()`); never use `Uri.getPath()`, which would split at `#`/`?`.
 */
data class NetKey(val scheme: String, val serverId: Long, val path: String) {

    val value: String get() = "$scheme://$serverId$path"

    fun toMediaKey(): MediaKey = MediaKey(value)

    override fun toString(): String = value

    companion object {
        /** Schemes routed to the network data source. */
        val SCHEMES: Set<String> = NetProtocol.entries.map { it.scheme }.toSet() + "webdavs"

        fun of(protocol: NetProtocol, serverId: Long, path: String): NetKey =
            NetKey(protocol.scheme, serverId, RemotePath.normalize(path))

        fun parse(key: String): NetKey? {
            val sep = key.indexOf("://")
            if (sep <= 0) return null
            val scheme = key.substring(0, sep).lowercase()
            if (scheme !in SCHEMES) return null
            val rest = key.substring(sep + 3)
            val slash = rest.indexOf('/')
            val authority = if (slash < 0) rest else rest.substring(0, slash)
            val id = authority.toLongOrNull()?.takeIf { it > 0 } ?: return null
            val rawPath = if (slash < 0) "/" else rest.substring(slash)
            return NetKey(if (scheme == "webdavs") "webdav" else scheme, id, RemotePath.normalize(rawPath))
        }

        fun isNetworkKey(key: String): Boolean = parse(key) != null
    }
}
