package com.dietpizza.rxplayer.feature.gallery

import android.content.res.Resources
import androidx.compose.runtime.Immutable
import com.dietpizza.rxplayer.core.model.FileOpError
import com.dietpizza.rxplayer.core.model.GalleryItem
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.VirtualAlbum

/**
 * What an [AlbumActivity] shows. Parsed from its intent extras; [Favourites] and [Custom] are
 * DB-only albums whose items are joined against the live library ([joinAlbumItems]).
 */
sealed interface AlbumSource {
    data class Folder(val path: String) : AlbumSource
    data class Virtual(val album: VirtualAlbum) : AlbumSource
    data object Favourites : AlbumSource
    data class Custom(val albumId: Long) : AlbumSource

    /** Favourites and custom albums: "Remove from album" instead of a folder. */
    val isKeyed: Boolean get() = this is Favourites || this is Custom
}

/**
 * Items of a DB-only album: [keys] (in the album's order) joined with the visible library.
 * Keys whose file is gone, hidden, or not a local MediaStore item are skipped; duplicates once.
 */
internal fun joinAlbumItems(keys: Collection<MediaKey>, byKey: Map<String, GalleryItem>): List<GalleryItem> {
    if (keys.isEmpty() || byKey.isEmpty()) return emptyList()
    val out = ArrayList<GalleryItem>(keys.size)
    val seen = HashSet<String>(keys.size * 2)
    for (key in keys) {
        val item = byKey[key.value] ?: continue
        if (seen.add(key.value)) out += item
    }
    return out
}

/** Count + cover of a keyed album: visible items only; the cover is the newest by date taken. */
internal data class KeyedAlbumSummary(val count: Int, val cover: GalleryItem?)

internal fun summarizeKeyed(keys: Collection<MediaKey>, byKey: Map<String, GalleryItem>): KeyedAlbumSummary {
    var count = 0
    var cover: GalleryItem? = null
    val seen = HashSet<String>(keys.size * 2)
    for (key in keys) {
        val item = byKey[key.value] ?: continue
        if (!seen.add(key.value)) continue
        count++
        val c = cover
        if (c == null || item.dateTakenMs > c.dateTakenMs || (item.dateTakenMs == c.dateTakenMs && item.id > c.id)) cover = item
    }
    return KeyedAlbumSummary(count, cover)
}

/**
 * Move/copy destinations: every folder album plus the Camera folder, minus [exclude] (the folder
 * being viewed), by name.
 */
internal fun folderChoices(folders: List<FolderChoice>, exclude: String?): List<FolderChoice> {
    val excluded = exclude?.trimEnd('/')
    return folders
        .filter { it.path.isNotEmpty() && it.path != excluded }
        .distinctBy { it.path }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it: FolderChoice -> it.name }.thenBy { it.path })
}

enum class FileOp { MOVE, COPY, DELETE }

/** A running move/copy/delete, for the progress dialog. */
@Immutable
data class FileOpProgress(val op: FileOp, val fraction: Float, val cancellable: Boolean)

/** One-off messages from [AlbumViewModel] / [GalleryViewModel]. Resolved to text in the UI. */
internal sealed interface GalleryMessage {
    data class FileOpDone(val op: FileOp, val succeeded: Int, val failed: Int) : GalleryMessage
    data class Favourited(val count: Int, val favourite: Boolean) : GalleryMessage
    data class AddedToAlbum(val count: Int, val album: String) : GalleryMessage
    data class RemovedFromAlbum(val count: Int) : GalleryMessage

    /** The album itself is gone (deleted here): close the screen. */
    data object Close : GalleryMessage
}

internal fun GalleryMessage.resolve(res: Resources): String? = when (this) {
    is GalleryMessage.FileOpDone -> {
        val done = when (op) {
            FileOp.MOVE -> R.plurals.msg_moved
            FileOp.COPY -> R.plurals.msg_copied
            FileOp.DELETE -> R.plurals.msg_deleted
        }
        val total = succeeded + failed
        if (failed == 0) {
            res.getQuantityString(done, succeeded, succeeded)
        } else {
            res.getQuantityString(R.plurals.msg_failed, total, failed, total)
        }
    }
    is GalleryMessage.Favourited -> res.getQuantityString(
        if (favourite) R.plurals.msg_favourited else R.plurals.msg_unfavourited,
        count,
        count,
    )
    is GalleryMessage.AddedToAlbum -> res.getQuantityString(R.plurals.msg_added_to_album, count, count, album)
    is GalleryMessage.RemovedFromAlbum -> res.getQuantityString(R.plurals.msg_removed_from_album, count, count)
    GalleryMessage.Close -> null
}

/** Inline error under a name field. */
internal fun FileOpError.nameErrorRes(): Int = when (this) {
    FileOpError.TARGET_EXISTS -> R.string.error_name_exists
    FileOpError.INVALID_NAME -> R.string.error_name_invalid
    FileOpError.NOT_FOUND -> R.string.error_not_found
    else -> R.string.error_generic
}
