package com.dietpizza.rxplayer.core.model

/** A custom (virtual) album (plan §5, decision 12). DB-only: never moves files. */
data class CustomAlbum(
    val id: Long,
    val name: String,
    val itemCount: Int,
    /** Explicit cover, else the most recently added item, else null (empty album). */
    val coverKey: MediaKey?,
    val createdAt: Long,
    val sortIndex: Int,
)

/** A user video playlist. */
data class Playlist(
    val id: Long,
    val name: String,
    val itemCount: Int,
    /** The first item, or null when empty. */
    val coverKey: MediaKey?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** One playlist slot, joined with live MediaStore info. */
data class PlaylistEntry(
    /** 0-based index in the playlist's order. */
    val index: Int,
    val key: MediaKey,
    /** Null when the file no longer exists locally (or isn't a local file). */
    val video: VideoItem?,
)

/** One History row (plan §7.2): a local video that still exists, with its playback state. */
data class HistoryEntry(
    val video: VideoItem,
    val lastPlayedAt: Long,
)

enum class VideoViewMode { LIST, GRID }

enum class VideoSortField { NAME, DATE_MODIFIED, SIZE, DURATION }

data class VideoSort(val field: VideoSortField = VideoSortField.NAME, val descending: Boolean = false) {
    companion object {
        val DEFAULT = VideoSort()

        /** The natural direction when a field is first picked: A→Z, newest, biggest, longest first. */
        fun defaultFor(field: VideoSortField) = VideoSort(field, descending = field != VideoSortField.NAME)
    }
}

/** Library preferences (plan §8). */
data class LibrarySettings(
    /** Folder video list: list or grid. */
    val videoViewMode: VideoViewMode = VideoViewMode.LIST,
    /** Default order of videos inside a folder. */
    val videoSort: VideoSort = VideoSort.DEFAULT,
    /** Show `.nomedia` and hidden folders (decision 32: off by default). */
    val showHidden: Boolean = false,
    /**
     * Initial column count of a gallery album grid (pinch still changes it per screen).
     * [GRID_COLUMNS_AUTO] = follow the screen width.
     */
    val galleryGridColumns: Int = GRID_COLUMNS_AUTO,
) {
    companion object {
        const val GRID_COLUMNS_AUTO = 0
        const val MIN_GRID_COLUMNS = 2
        const val MAX_GRID_COLUMNS = 6
    }
}

/**
 * Hilt name of the `Flow<Boolean>` "show hidden / .nomedia media" binding. Provided by
 * `:core:datastore`, consumed by the MediaStore sources in `:core:media`, which don't depend
 * on each other; both reference this one constant.
 */
const val SHOW_HIDDEN_MEDIA_BINDING = "rx.media.showHidden"
