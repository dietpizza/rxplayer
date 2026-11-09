package com.dietpizza.rxplayer.core.data

import com.dietpizza.rxplayer.core.database.PlaybackStateDao
import com.dietpizza.rxplayer.core.database.toModel
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.PlaybackState
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class RoomPlaybackStateRepository internal constructor(
    private val dao: PlaybackStateDao,
    private val clock: () -> Long,
    private val fingerprintOf: (path: String) -> FileFingerprint?,
) : PlaybackStateRepository {

    @Inject
    constructor(dao: PlaybackStateDao) : this(dao, System::currentTimeMillis, ::fileFingerprint)

    /** Serialises read-modify-write so concurrent saves can't lose a play-count bump. */
    private val writeLock = Mutex()

    override suspend fun get(key: MediaKey): PlaybackState? = dao.get(key.value)?.toModel()

    override fun observe(key: MediaKey): Flow<PlaybackState?> =
        dao.observe(key.value).distinctUntilChanged().map { it?.toModel() }

    override suspend fun saveProgress(
        key: MediaKey,
        title: String,
        positionMs: Long,
        durationMs: Long,
        countAsPlay: Boolean,
    ) = writeLock.withLock {
        val existing = dao.get(key.value)
        // Stat the file only when creating the row or starting a play, not on every periodic save.
        val path = key.filePath
        val fingerprint = if (path != null && (existing == null || countAsPlay)) {
            withContext(Dispatchers.IO) { fingerprintOf(path) }
        } else {
            null
        }
        dao.upsert(
            nextPlaybackState(existing, key, title, positionMs, durationMs, countAsPlay, clock(), fingerprint),
        )
    }
}

private fun fileFingerprint(path: String): FileFingerprint? {
    val f = File(path)
    val size = f.length()
    val mtime = f.lastModified()
    return if (size == 0L && mtime == 0L) null else FileFingerprint(size, mtime)
}
