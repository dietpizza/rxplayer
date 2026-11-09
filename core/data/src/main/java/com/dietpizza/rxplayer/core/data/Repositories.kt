package com.dietpizza.rxplayer.core.data

import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.PlaybackState
import com.dietpizza.rxplayer.core.model.VideoFolder
import com.dietpizza.rxplayer.core.model.VideoItem
import kotlinx.coroutines.flow.Flow

/**
 * CONTRACT (owned by the data agent; signatures are relied on by the player and Videos agents).
 * Local videos from MediaStore joined with Room playback state. Flows re-emit when MediaStore
 * changes (ContentObserver) or when playback state changes.
 */
interface VideoRepository {
    /** Folders containing videos, sorted by name (case-insensitive). */
    fun folders(): Flow<List<VideoFolder>>

    /** Videos directly inside [folderPath], sorted by title (natural order: "Ep 2" < "Ep 10"). */
    fun videosIn(folderPath: String): Flow<List<VideoItem>>

    /** One video by key, or null if it isn't a local MediaStore video. */
    suspend fun video(key: MediaKey): VideoItem?
}

/** CONTRACT. Resume position / watched state / history (Room `playback_state`, plan §7.2). */
interface PlaybackStateRepository {
    suspend fun get(key: MediaKey): PlaybackState?

    fun observe(key: MediaKey): Flow<PlaybackState?>

    /**
     * Upserts progress. Marks `completed` when position >= 95% of duration. Bumps `playCount`
     * and `lastPlayedAt` only when [countAsPlay] is true (once per playback session).
     * `source` is derived from the key (LOCAL for file://, otherwise the scheme).
     */
    suspend fun saveProgress(
        key: MediaKey,
        title: String,
        positionMs: Long,
        durationMs: Long,
        countAsPlay: Boolean,
    )
}
