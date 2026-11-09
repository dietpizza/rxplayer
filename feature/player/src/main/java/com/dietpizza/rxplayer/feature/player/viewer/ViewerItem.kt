package com.dietpizza.rxplayer.feature.player.viewer

import androidx.compose.runtime.Immutable
import com.dietpizza.rxplayer.core.model.GalleryItem
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.player.MediaKeys

/** What a viewer page shows. */
internal enum class PageKind { Photo, Video }

/** Where an item comes from; decides which actions the viewer offers. */
internal enum class ItemOrigin {
    /** `file://` key from the gallery: favourite, share, delete, info. */
    Local,

    /** `smb://` / `sftp://` / … key: share-less, favourite-less, delete lives in the browser. */
    Network,

    /** `ACTION_VIEW` from another app (plan §4 #15): share and info only. */
    External,
}

/**
 * One viewer page. [gallery] is the MediaStore row when the key is a known local file (it has
 * the share URI, dimensions and dates); null for network/external items or before it's loaded.
 */
@Immutable
internal data class ViewerItem(
    val key: MediaKey,
    val kind: PageKind,
    val origin: ItemOrigin,
    val title: String,
    /** Best known MIME type (`image/jpeg`, `video/mp4`), or null. */
    val mimeType: String?,
    val gallery: GalleryItem? = null,
) {
    val isVideo: Boolean get() = kind == PageKind.Video
    val canFavourite: Boolean get() = origin == ItemOrigin.Local
    val canDelete: Boolean get() = origin == ItemOrigin.Local

    /** content:// URI to share: the MediaStore URI for local items, the granted URI for external ones. */
    val shareUri: String?
        get() = when (origin) {
            ItemOrigin.Local -> gallery?.contentUri
            ItemOrigin.External -> key.value.takeIf { it.startsWith("content://") } ?: gallery?.contentUri
            ItemOrigin.Network -> null
        }

    /** Display aspect (w/h) when known from metadata, else 0. */
    val aspect: Float
        get() {
            val g = gallery ?: return 0f
            val w = g.displayWidth
            val h = g.displayHeight
            return if (w > 0 && h > 0) w.toFloat() / h else 0f
        }
}

/** Pure classification of keys into pages (unit tested). */
internal object PageKinds {
    private val VIDEO_EXT = setOf(
        "mkv", "mp4", "m4v", "mov", "avi", "webm", "wmv", "flv", "ts", "m2ts", "mts", "mpg",
        "mpeg", "3gp", "3g2", "ogv", "vob", "divx", "rmvb", "asf", "f4v",
    )
    private val IMAGE_MIME = mapOf(
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "gif" to "image/gif",
        "webp" to "image/webp", "heic" to "image/heic", "heif" to "image/heif", "avif" to "image/avif",
        "bmp" to "image/bmp", "dng" to "image/x-adobe-dng",
    )
    private val ANIMATABLE_MIME = setOf("image/gif", "image/webp", "image/heic", "image/heif", "image/avif")

    /**
     * [isVideo] (from MediaStore) wins, then the MIME type, then the file extension. Anything
     * unknown is a photo: the image decoder copes (or shows an error) rather than the player.
     */
    fun classify(key: String, mimeType: String?, isVideo: Boolean? = null): PageKind {
        if (isVideo != null) return if (isVideo) PageKind.Video else PageKind.Photo
        val mime = mimeType?.lowercase()
        if (mime != null) {
            if (mime.startsWith("video/")) return PageKind.Video
            if (mime.startsWith("image/")) return PageKind.Photo
        }
        return if (extension(key) in VIDEO_EXT) PageKind.Video else PageKind.Photo
    }

    /** Local file, network share, or (with [external]) another app's item. */
    fun origin(key: String, external: Boolean): ItemOrigin = when {
        external -> ItemOrigin.External
        key.startsWith("file://") -> ItemOrigin.Local
        key.startsWith("content://") -> ItemOrigin.External
        else -> ItemOrigin.Network
    }

    /** Image MIME type from the extension, or null. */
    fun imageMimeFor(key: String): String? = IMAGE_MIME[extension(key)]

    /** GIF / WebP / HEIF / AVIF may be animated (plan §5), so they're decoded as drawables. */
    fun mayBeAnimated(mimeType: String?): Boolean = mimeType == null || mimeType.lowercase() in ANIMATABLE_MIME

    /** File name (last path segment as-is; network keys hold raw paths). */
    fun fileName(key: String): String {
        val noScheme = key.substringAfter("://", key)
        val path = if (key.startsWith("content://")) noScheme.substringBefore('?') else noScheme
        return path.trimEnd('/').substringAfterLast('/')
    }

    /** Lower-case extension of the key's file name, or "". */
    fun extension(key: String): String {
        val name = fileName(key)
        val dot = name.lastIndexOf('.')
        return if (dot >= 0 && dot < name.length - 1) name.substring(dot + 1).lowercase() else ""
    }

    /** Builds the page for [key] from whatever's known (MediaStore row, MIME type hint). */
    fun item(key: MediaKey, gallery: GalleryItem?, external: Boolean, mimeHint: String? = null): ViewerItem {
        val origin = origin(key.value, external)
        val mime = gallery?.mimeType?.takeIf { it.isNotEmpty() } ?: mimeHint ?: imageMimeFor(key.value)
        val kind = classify(key.value, mime, gallery?.isVideo)
        val name = gallery?.displayName ?: fileName(key.value).let {
            // Network keys hold raw paths; content URIs are percent-encoded.
            if (origin == ItemOrigin.Network) it else MediaKeys.decode(it).substringAfterLast('/')
        }
        return ViewerItem(key, kind, origin, name, mime, gallery)
    }
}
