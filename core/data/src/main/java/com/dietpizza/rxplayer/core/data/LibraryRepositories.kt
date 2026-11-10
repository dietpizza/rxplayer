package com.dietpizza.rxplayer.core.data

import com.dietpizza.rxplayer.core.model.CopyProgress
import com.dietpizza.rxplayer.core.model.CustomAlbum
import com.dietpizza.rxplayer.core.model.FileOpError
import com.dietpizza.rxplayer.core.model.HistoryEntry
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.Playlist
import com.dietpizza.rxplayer.core.model.PlaylistEntry
import com.dietpizza.rxplayer.core.model.VideoItem
import kotlinx.coroutines.flow.Flow

/** Favourites (Room `favourite`). Works for any [MediaKey] (photos, videos, network files). */
interface FavouritesRepository {
    /** Every favourite key. */
    val favourites: Flow<Set<MediaKey>>

    fun observeIsFavourite(key: MediaKey): Flow<Boolean>

    suspend fun isFavourite(key: MediaKey): Boolean

    /** Flips one key; returns the new state. */
    suspend fun toggle(key: MediaKey): Boolean

    /** Multi-select: sets all [keys] to [favourite] (already-favourite keys keep their added time). */
    suspend fun setFavourite(keys: List<MediaKey>, favourite: Boolean)
}

/**
 * Custom virtual albums (decision 12): DB-only, never move files. Counts and covers come from the
 * DB rows; rows for files deleted/renamed in-app are maintained by [MediaFileService].
 */
interface AlbumsRepository {
    /** Ordered by the user's order ([reorder]), then creation. */
    fun albums(): Flow<List<CustomAlbum>>

    /** Item keys, most recently added first. */
    fun items(albumId: Long): Flow<List<MediaKey>>

    /** Ids of the albums that contain [key] (for "add to album" checkmarks). */
    fun albumsContaining(key: MediaKey): Flow<Set<Long>>

    /** Returns the new album id. */
    suspend fun create(name: String, keys: List<MediaKey> = emptyList()): Long

    suspend fun rename(albumId: Long, name: String)

    /** Deletes the album and its item rows. Never touches files. */
    suspend fun delete(albumId: Long)

    /** Adds keys (already-present keys are ignored). */
    suspend fun add(albumId: Long, keys: List<MediaKey>)

    suspend fun remove(albumId: Long, keys: List<MediaKey>)

    /** Persists the album order: [orderedIds] first-to-last. */
    suspend fun reorder(orderedIds: List<Long>)

    /** Explicit cover, or null to fall back to the most recently added item. */
    suspend fun setCover(albumId: Long, key: MediaKey?)
}

/** Video playlists. Items are unique within a playlist and kept in a user-defined order. */
interface PlaylistsRepository {
    /** Most recently changed first. */
    fun playlists(): Flow<List<Playlist>>

    fun playlist(playlistId: Long): Flow<Playlist?>

    /** Items in order, joined with live MediaStore info (`video == null` once a file is gone). */
    fun entries(playlistId: Long): Flow<List<PlaylistEntry>>

    /** Returns the new playlist id. */
    suspend fun create(name: String, keys: List<MediaKey> = emptyList()): Long

    suspend fun rename(playlistId: Long, name: String)

    suspend fun delete(playlistId: Long)

    /** Appends keys not already in the playlist; returns how many were added. */
    suspend fun add(playlistId: Long, keys: List<MediaKey>): Int

    suspend fun remove(playlistId: Long, keys: List<MediaKey>)

    /** Moves the item at [from] to [to] (indices in the current order). */
    suspend fun move(playlistId: Long, from: Int, to: Int)

    /**
     * Persists a whole new order (e.g. at the end of a drag). Keys no longer in the playlist are
     * ignored; items added meanwhile and missing from [keys] are kept at the end.
     */
    suspend fun setOrder(playlistId: Long, keys: List<MediaKey>)
}

/**
 * Recently played (plan §7.2): `source = LOCAL` only (decision 23), most recent first, joined
 * with live MediaStore info so files that are gone drop out.
 */
interface HistoryRepository {
    fun history(limit: Int = DEFAULT_HISTORY_LIMIT): Flow<List<HistoryEntry>>

    /** Empties History. Resume positions and watched state are kept. */
    suspend fun clear()

    suspend fun remove(keys: List<MediaKey>)

    companion object {
        const val DEFAULT_HISTORY_LIMIT = 500
    }
}

/** Live index of visible local videos by `MediaKey.value` (shared with [VideoRepository]'s join). */
interface LocalVideoIndex {
    fun videosByKey(): Flow<Map<String, VideoItem>>
}

/** Outcome for one media item. [newKey] is set for successful rename/move/copy. */
data class MediaFileResult(
    val key: MediaKey,
    val newKey: MediaKey?,
    val error: FileOpError?,
) {
    val isSuccess: Boolean get() = error == null
}

/**
 * File operations on media items, keeping Room in step (plan §7.2): a rename/move rewrites the
 * media_key in every table in one transaction; a delete removes the rows. Only `file://` keys are
 * supported; others fail with [FileOpError.UNSUPPORTED]. One result per input key, in order.
 */
interface MediaFileService {
    /** [newName] is the full file name, extension included. */
    suspend fun rename(key: MediaKey, newName: String): MediaFileResult

    /**
     * Not cancellable once started (the key rewrite must match what happened on disk). Clashes
     * get a free name ("clip (1).mp4").
     */
    suspend fun move(
        keys: List<MediaKey>,
        targetDir: String,
        onProgress: (CopyProgress) -> Unit = {},
    ): List<MediaFileResult>

    /** Cancellable: a partial copy is removed and the call throws CancellationException. */
    suspend fun copy(
        keys: List<MediaKey>,
        targetDir: String,
        onProgress: (CopyProgress) -> Unit = {},
    ): List<MediaFileResult>

    /** Permanent, no trash (decision 20). Confirming is the caller's job. */
    suspend fun delete(keys: List<MediaKey>): List<MediaFileResult>

    /** Creates a folder for the move/copy picker; returns its absolute path or the error. */
    suspend fun createFolder(parentDir: String, name: String): Result<String>

    /**
     * Renames the folder [path] (to [newName], in the same parent) and carries playback state,
     * favourites and album memberships over to the new paths. Returns the new folder path.
     */
    suspend fun renameFolder(path: String, newName: String): Result<String>

    /**
     * Permanently deletes [keys] (the folder's media), then the folder [path] itself if nothing
     * else is left in it. Other files in the folder are never touched.
     */
    suspend fun deleteFolder(path: String, keys: List<MediaKey>): List<MediaFileResult>
}

/** Thrown in [Result.failure] by [MediaFileService.createFolder]. */
class FileOpException(val error: FileOpError) : Exception(error.name)
