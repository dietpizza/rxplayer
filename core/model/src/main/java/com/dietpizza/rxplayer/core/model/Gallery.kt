package com.dietpizza.rxplayer.core.model

/**
 * One local photo or video from MediaStore (Gallery, plan §4 #2–4, §5). Already filtered for
 * `.nomedia` / hidden folders.
 */
data class GalleryItem(
    /** `file://<path>` (plan §7.1). */
    val key: MediaKey,
    /** MediaStore row id (unique per volume). */
    val id: Long,
    /**
     * Typed MediaStore URI (`content://media/external/images/media/<id>` or `.../video/media/<id>`).
     * Used for thumbnails, sharing and decoding.
     */
    val contentUri: String,
    /** Absolute file path. */
    val path: String,
    /** Parent directory of [path], no trailing slash. */
    val folderPath: String,
    val displayName: String,
    /** e.g. `image/jpeg`, `video/mp4`. Empty when MediaStore doesn't know. */
    val mimeType: String,
    val isVideo: Boolean,
    /** EXIF / container capture time; falls back to [dateModifiedMs] when unknown. Epoch ms. */
    val dateTakenMs: Long,
    val dateModifiedMs: Long,
    val sizeBytes: Long,
    /** As stored (before applying [orientation]). 0 when unknown. */
    val width: Int,
    val height: Int,
    /** Videos only; 0 for photos or when unknown. */
    val durationMs: Long,
    /** Clockwise rotation in degrees (0/90/180/270) to display the image upright. */
    val orientation: Int,
) {
    /** GIFs (always potentially animated). Animated WebP/HEIC/AVIF can't be told apart from the MIME type alone. */
    val isGif: Boolean get() = mimeType.equals("image/gif", ignoreCase = true)

    /** Width/height after applying [orientation]. */
    val displayWidth: Int get() = if (orientation % 180 == 0) width else height
    val displayHeight: Int get() = if (orientation % 180 == 0) height else width
}

/** Built-in albums shown in the Gallery's pinned row. [id] is stable (intent extras, saved state). */
enum class VirtualAlbum(val id: String) {
    /** Every visible video, newest first. */
    ALL_VIDEOS("all_videos"),

    /** Every `…/DCIM/Camera` folder, on any volume. */
    CAMERA("camera"),
    ;

    companion object {
        fun fromId(id: String?): VirtualAlbum? = entries.firstOrNull { it.id == id }
    }
}

/** A folder with photos/videos, or a [VirtualAlbum]. */
data class GalleryAlbum(
    /** Folder path for folder albums; for virtual albums the first matching folder, or "". */
    val path: String,
    val name: String,
    val itemCount: Int,
    val videoCount: Int,
    /** Newest item by date taken (the first tile of the album's default order). */
    val coverContentUri: String?,
    val coverIsVideo: Boolean,
    /** Most recent [GalleryItem.dateModifiedMs] in the album ("date modified" sort). */
    val lastModifiedMs: Long,
    /** Most recent [GalleryItem.dateTakenMs] in the album. */
    val latestTakenMs: Long,
    /** Non-null for built-in albums. */
    val virtual: VirtualAlbum? = null,
) {
    /** Stable id: the folder path, or `virtual:<id>`. Use as a lazy-list key. */
    val id: String get() = if (virtual != null) "virtual:" + virtual.id else path
}

enum class AlbumSortField { NAME, DATE_MODIFIED, COUNT }

data class AlbumSort(val field: AlbumSortField = AlbumSortField.DATE_MODIFIED, val descending: Boolean = true) {
    companion object {
        val DEFAULT = AlbumSort()

        /** The natural direction when a field is first picked: A→Z, newest first, biggest first. */
        fun defaultFor(field: AlbumSortField) = AlbumSort(field, descending = field != AlbumSortField.NAME)
    }
}

enum class MediaSortField { DATE_TAKEN, NAME, SIZE }

data class MediaSort(val field: MediaSortField = MediaSortField.DATE_TAKEN, val descending: Boolean = true) {
    companion object {
        val DEFAULT = MediaSort()

        /** The natural direction when a field is first picked: newest first, A→Z, biggest first. */
        fun defaultFor(field: MediaSortField) = MediaSort(field, descending = field != MediaSortField.NAME)
    }
}
