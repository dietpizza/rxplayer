package com.dietpizza.rxplayer.core.netfs

import com.dietpizza.rxplayer.core.netfs.db.NetworkServerDao
import com.dietpizza.rxplayer.core.netfs.db.NetworkServerEntity
import com.dietpizza.rxplayer.core.netfs.model.ConnectionSpec
import com.dietpizza.rxplayer.core.netfs.model.NetProtocol
import com.dietpizza.rxplayer.core.netfs.model.NetServer
import com.dietpizza.rxplayer.core.netfs.model.PasswordChange
import com.dietpizza.rxplayer.core.netfs.model.ServerDraft
import com.dietpizza.rxplayer.core.netfs.model.ServerOptions
import com.dietpizza.rxplayer.core.netfs.secret.Sealed
import com.dietpizza.rxplayer.core.netfs.secret.SecretBox
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Saved servers. Passwords go through [SecretBox] on the way in and out; nothing is logged. */
@Singleton
class ServerRepository @Inject constructor(
    private val dao: NetworkServerDao,
    private val secrets: SecretBox,
) {
    private val _changes = MutableSharedFlow<Long>(extraBufferCapacity = 16)

    /** Server ids whose connection settings changed or that were deleted (pooled clients must drop). */
    val changes: SharedFlow<Long> = _changes

    val servers: Flow<List<NetServer>> = dao.observeAll().map { list -> list.mapNotNull { it.toModel() } }

    suspend fun get(id: Long): NetServer? = dao.get(id)?.toModel()

    /** Inserts or updates; returns the id. */
    suspend fun save(draft: ServerDraft): Long = withContext(Dispatchers.IO) {
        val existing = if (draft.id != 0L) dao.get(draft.id) else null
        val sealed: Sealed? = when (val pw = draft.password) {
            PasswordChange.Keep -> existing?.let { e -> e.secretCt?.let { ct -> e.secretIv?.let { Sealed(ct, it) } } }
            PasswordChange.Clear -> null
            is PasswordChange.Set -> if (pw.password.isEmpty()) null else secrets.encrypt(pw.password)
        }
        val entity = NetworkServerEntity(
            id = existing?.id ?: 0,
            protocol = draft.protocol.name,
            name = draft.name.trim(),
            host = draft.host.trim(),
            port = draft.port,
            basePath = RemotePath.normalize(draft.basePath),
            username = draft.username?.trim()?.ifEmpty { null },
            secretCt = sealed?.ciphertext,
            secretIv = sealed?.iv,
            optionsJson = draft.options.toJson(),
            lastUsedAt = existing?.lastUsedAt ?: 0L,
        )
        if (existing == null) {
            dao.insert(entity)
        } else {
            dao.update(entity)
            _changes.emit(existing.id)
            existing.id
        }
    }

    suspend fun delete(id: Long) {
        dao.delete(id)
        _changes.emit(id)
    }

    suspend fun markUsed(id: Long) = dao.touch(id, System.currentTimeMillis())

    /** Trust-on-first-use: records the SFTP host-key fingerprint the first connect saw. */
    suspend fun rememberHostKey(id: Long, fingerprint: String) {
        val entity = dao.get(id) ?: return
        val options = parseOptions(entity.optionsJson).copy(hostKey = fingerprint)
        dao.updateOptions(id, options.toJson())
    }

    /** Connection details with the decrypted password, or null if the server is gone. */
    suspend fun connectionSpec(id: Long): ConnectionSpec? = withContext(Dispatchers.IO) { connectionSpecBlocking(id) }

    /** Blocking variant for Media3 loader threads. */
    fun connectionSpecBlocking(id: Long): ConnectionSpec? = dao.getBlocking(id)?.toSpec()

    /**
     * Spec for a "Test connection" on an unsaved form. A [PasswordChange.Keep] draft of an existing
     * server reuses its stored password.
     */
    suspend fun connectionSpec(draft: ServerDraft): ConnectionSpec = withContext(Dispatchers.IO) {
        val password = when (val pw = draft.password) {
            PasswordChange.Keep -> if (draft.id != 0L) dao.get(draft.id)?.decryptPassword() else null
            PasswordChange.Clear -> null
            is PasswordChange.Set -> pw.password.ifEmpty { null }
        }
        ConnectionSpec(
            protocol = draft.protocol,
            host = draft.host.trim(),
            port = draft.port,
            basePath = RemotePath.normalize(draft.basePath),
            username = draft.username?.trim()?.ifEmpty { null },
            password = password,
            options = draft.options,
        )
    }

    private fun NetworkServerEntity.decryptPassword(): String? {
        val ct = secretCt ?: return null
        val iv = secretIv ?: return null
        return secrets.decryptOrNull(Sealed(ct, iv))
    }

    private fun NetworkServerEntity.toSpec(): ConnectionSpec? {
        val protocol = NetProtocol.fromName(protocol) ?: return null
        return ConnectionSpec(
            protocol = protocol,
            host = host,
            port = port,
            basePath = basePath,
            username = username,
            password = decryptPassword(),
            options = parseOptions(optionsJson),
        )
    }

    private fun NetworkServerEntity.toModel(): NetServer? {
        val protocol = NetProtocol.fromName(protocol) ?: return null
        return NetServer(
            id = id,
            protocol = protocol,
            name = name,
            host = host,
            port = port,
            basePath = basePath,
            username = username,
            hasPassword = secretCt != null,
            options = parseOptions(optionsJson),
            lastUsedAt = lastUsedAt,
        )
    }

    internal companion object {
        fun ServerOptions.toJson(): String = JSONObject().apply {
            put("anonymous", anonymous)
            domain?.let { put("domain", it) }
            put("passive", passive)
            put("https", https)
            hostKey?.let { put("hostKey", it) }
        }.toString()

        fun parseOptions(json: String): ServerOptions = try {
            val o = JSONObject(json)
            ServerOptions(
                anonymous = o.optBoolean("anonymous", false),
                domain = o.optString("domain").ifEmpty { null },
                passive = o.optBoolean("passive", true),
                https = o.optBoolean("https", false),
                hostKey = o.optString("hostKey").ifEmpty { null },
            )
        } catch (_: Exception) {
            ServerOptions()
        }
    }
}
