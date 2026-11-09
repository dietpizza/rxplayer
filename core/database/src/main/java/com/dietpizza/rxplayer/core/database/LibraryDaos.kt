package com.dietpizza.rxplayer.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FavouriteDao {
    @Query("SELECT media_key FROM favourite ORDER BY added_at DESC")
    fun observeKeys(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favourite WHERE media_key = :mediaKey)")
    fun observeIsFavourite(mediaKey: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favourite WHERE media_key = :mediaKey)")
    suspend fun isFavourite(mediaKey: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(rows: List<FavouriteEntity>)

    @Query("DELETE FROM favourite WHERE media_key IN (:mediaKeys)")
    suspend fun delete(mediaKeys: List<String>)

    /** Atomic toggle; returns the new state. */
    @Transaction
    suspend fun toggle(mediaKey: String, now: Long): Boolean {
        return if (isFavourite(mediaKey)) {
            delete(listOf(mediaKey))
            false
        } else {
            insert(listOf(FavouriteEntity(mediaKey, now)))
            true
        }
    }
}

@Dao
interface AlbumDao {
    @Query(
        """
        SELECT a.id, a.name, a.created_at, a.sort_index,
            COALESCE(a.cover_key,
                (SELECT i.media_key FROM album_item i WHERE i.album_id = a.id
                 ORDER BY i.added_at DESC LIMIT 1)) AS cover_key,
            (SELECT COUNT(*) FROM album_item i WHERE i.album_id = a.id) AS item_count
        FROM album a
        ORDER BY a.sort_index, a.id
        """,
    )
    fun observeAlbums(): Flow<List<AlbumWithStats>>

    @Query("SELECT media_key FROM album_item WHERE album_id = :albumId ORDER BY added_at DESC, media_key")
    fun observeItemKeys(albumId: Long): Flow<List<String>>

    @Query("SELECT album_id FROM album_item WHERE media_key = :mediaKey")
    fun observeAlbumIdsContaining(mediaKey: String): Flow<List<Long>>

    @Query("SELECT COALESCE(MAX(sort_index), -1) FROM album")
    suspend fun maxSortIndex(): Int

    @Insert
    suspend fun insertAlbum(album: AlbumEntity): Long

    @Query("UPDATE album SET name = :name WHERE id = :albumId")
    suspend fun rename(albumId: Long, name: String)

    @Query("UPDATE album SET cover_key = :coverKey WHERE id = :albumId")
    suspend fun setCover(albumId: Long, coverKey: String?)

    @Query("UPDATE album SET sort_index = :sortIndex WHERE id = :albumId")
    suspend fun setSortIndex(albumId: Long, sortIndex: Int)

    @Query("DELETE FROM album WHERE id = :albumId")
    suspend fun deleteAlbum(albumId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(items: List<AlbumItemEntity>)

    @Query("DELETE FROM album_item WHERE album_id = :albumId AND media_key IN (:mediaKeys)")
    suspend fun deleteItems(albumId: Long, mediaKeys: List<String>)

    /** Drops an explicit cover that was just removed from the album. */
    @Query("UPDATE album SET cover_key = NULL WHERE id = :albumId AND cover_key IN (:mediaKeys)")
    suspend fun clearCoverIn(albumId: Long, mediaKeys: List<String>)

    @Transaction
    suspend fun create(name: String, now: Long, keys: List<String>): Long {
        val id = insertAlbum(AlbumEntity(name = name, coverKey = null, createdAt = now, sortIndex = maxSortIndex() + 1))
        if (keys.isNotEmpty()) insertItems(keys.map { AlbumItemEntity(id, it, now) })
        return id
    }

    @Transaction
    suspend fun removeItems(albumId: Long, mediaKeys: List<String>) {
        deleteItems(albumId, mediaKeys)
        clearCoverIn(albumId, mediaKeys)
    }

    /** Sets `sort_index` to each id's position in [orderedIds]. */
    @Transaction
    suspend fun reorder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setSortIndex(id, index) }
    }
}

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT p.id, p.name, p.created_at, p.updated_at,
            (SELECT i.media_key FROM playlist_item i WHERE i.playlist_id = p.id
             ORDER BY i.position LIMIT 1) AS cover_key,
            (SELECT COUNT(*) FROM playlist_item i WHERE i.playlist_id = p.id) AS item_count
        FROM playlist p
        ORDER BY p.updated_at DESC, p.id DESC
        """,
    )
    fun observePlaylists(): Flow<List<PlaylistWithStats>>

    @Query(
        """
        SELECT p.id, p.name, p.created_at, p.updated_at,
            (SELECT i.media_key FROM playlist_item i WHERE i.playlist_id = p.id
             ORDER BY i.position LIMIT 1) AS cover_key,
            (SELECT COUNT(*) FROM playlist_item i WHERE i.playlist_id = p.id) AS item_count
        FROM playlist p WHERE p.id = :playlistId
        """,
    )
    fun observePlaylist(playlistId: Long): Flow<PlaylistWithStats?>

    @Query("SELECT media_key FROM playlist_item WHERE playlist_id = :playlistId ORDER BY position")
    fun observeItemKeys(playlistId: Long): Flow<List<String>>

    @Query("SELECT media_key FROM playlist_item WHERE playlist_id = :playlistId ORDER BY position")
    suspend fun itemKeys(playlistId: Long): List<String>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlist SET name = :name, updated_at = :now WHERE id = :playlistId")
    suspend fun rename(playlistId: Long, name: String, now: Long)

    @Query("UPDATE playlist SET updated_at = :now WHERE id = :playlistId")
    suspend fun touch(playlistId: Long, now: Long)

    @Query("DELETE FROM playlist WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_item WHERE playlist_id = :playlistId")
    suspend fun clearItems(playlistId: Long)

    @Insert
    suspend fun insertItems(items: List<PlaylistItemEntity>)

    /** Replaces the whole item list with [keys] at positions 0..n-1. */
    @Transaction
    suspend fun replaceItems(playlistId: Long, keys: List<String>, now: Long) {
        clearItems(playlistId)
        if (keys.isNotEmpty()) insertItems(keys.mapIndexed { i, k -> PlaylistItemEntity(playlistId, k, i) })
        touch(playlistId, now)
    }

    @Transaction
    suspend fun create(name: String, now: Long, keys: List<String>): Long {
        val id = insertPlaylist(PlaylistEntity(name = name, createdAt = now, updatedAt = now))
        if (keys.isNotEmpty()) insertItems(keys.mapIndexed { i, k -> PlaylistItemEntity(id, k, i) })
        return id
    }

    /** Read-modify-write of the item order in one transaction. */
    @Transaction
    suspend fun editItems(playlistId: Long, now: Long, transform: (List<String>) -> List<String>) {
        val current = itemKeys(playlistId)
        val next = transform(current)
        if (next != current) replaceItems(playlistId, next, now)
    }
}

/**
 * Cross-table media_key maintenance for in-app file operations (plan §7.2): one transaction
 * rewrites (rename/move) or removes (delete) a key in every table that stores one.
 */
@Dao
interface MediaKeyDao {
    // OR REPLACE: if a row already exists under the new key (a file that used to live at that
    // path), the moved file's own row wins.
    @Query("UPDATE OR REPLACE playback_state SET media_key = :to WHERE media_key = :from")
    suspend fun movePlaybackState(from: String, to: String)

    @Query("UPDATE OR REPLACE favourite SET media_key = :to WHERE media_key = :from")
    suspend fun moveFavourite(from: String, to: String)

    @Query("UPDATE OR REPLACE album_item SET media_key = :to WHERE media_key = :from")
    suspend fun moveAlbumItems(from: String, to: String)

    @Query("UPDATE album SET cover_key = :to WHERE cover_key = :from")
    suspend fun moveAlbumCovers(from: String, to: String)

    @Query("UPDATE playlist_item SET media_key = :to WHERE media_key = :from")
    suspend fun movePlaylistItems(from: String, to: String)

    @Query("DELETE FROM playback_state WHERE media_key IN (:keys)")
    suspend fun deletePlaybackStates(keys: List<String>)

    @Query("DELETE FROM favourite WHERE media_key IN (:keys)")
    suspend fun deleteFavourites(keys: List<String>)

    @Query("DELETE FROM album_item WHERE media_key IN (:keys)")
    suspend fun deleteAlbumItems(keys: List<String>)

    @Query("UPDATE album SET cover_key = NULL WHERE cover_key IN (:keys)")
    suspend fun clearAlbumCovers(keys: List<String>)

    @Query("DELETE FROM playlist_item WHERE media_key IN (:keys)")
    suspend fun deletePlaylistItems(keys: List<String>)

    /** Rewrites every `from → to` pair in all tables, in one transaction. */
    @Transaction
    suspend fun rewriteKeys(moves: Map<String, String>) {
        for ((from, to) in moves) {
            if (from == to) continue
            movePlaybackState(from, to)
            moveFavourite(from, to)
            moveAlbumItems(from, to)
            moveAlbumCovers(from, to)
            movePlaylistItems(from, to)
        }
    }

    /** Removes every row that refers to [keys], in one transaction. */
    @Transaction
    suspend fun deleteKeys(keys: List<String>) {
        if (keys.isEmpty()) return
        // SQLite caps bound parameters; chunk to stay well under it.
        for (chunk in keys.chunked(500)) {
            deletePlaybackStates(chunk)
            deleteFavourites(chunk)
            deleteAlbumItems(chunk)
            clearAlbumCovers(chunk)
            deletePlaylistItems(chunk)
        }
    }
}
