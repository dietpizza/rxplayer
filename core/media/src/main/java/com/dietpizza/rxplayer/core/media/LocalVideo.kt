package com.dietpizza.rxplayer.core.media

/** One row of `MediaStore.Video.Media`, already filtered for `.nomedia` / hidden folders. */
data class LocalVideo(
    val id: Long,
    /** `content://media/external/video/media/<id>`, used for thumbnails. */
    val contentUri: String,
    /** Absolute file path (`DATA`). */
    val path: String,
    /** Parent directory of [path], no trailing slash. */
    val folderPath: String,
    val displayName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val dateModifiedMs: Long,
)

/** Source of local videos. Implemented by [MediaStoreVideoSource]; an interface so repositories are testable. */
interface LocalVideoSource {
    /**
     * Every visible local video. Emits once immediately on collection, then re-queries after
     * MediaStore changes (debounced ~300 ms). Shared: concurrent collectors reuse one query/observer.
     * Only emits when the list actually changed.
     */
    val videos: kotlinx.coroutines.flow.Flow<List<LocalVideo>>

    /** Single lookup by absolute path. Does NOT apply the `.nomedia` / hidden-folder filter. */
    suspend fun videoAt(path: String): LocalVideo?
}
