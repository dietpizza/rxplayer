package com.dietpizza.rxplayer.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Room `favourite` (plan §7.2). */
@Entity(tableName = "favourite")
data class FavouriteEntity(
    @PrimaryKey @ColumnInfo(name = "media_key") val mediaKey: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

/** Room `album`: custom virtual albums. DB-only, they never move files (decision 12). */
@Entity(tableName = "album")
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    /** Explicit cover, or null to use the most recently added item. */
    @ColumnInfo(name = "cover_key") val coverKey: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "sort_index") val sortIndex: Int,
)

@Entity(
    tableName = "album_item",
    primaryKeys = ["album_id", "media_key"],
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["album_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("media_key")],
)
data class AlbumItemEntity(
    @ColumnInfo(name = "album_id") val albumId: Long,
    @ColumnInfo(name = "media_key") val mediaKey: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

/** Room `playlist`: user video playlists. */
@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "playlist_item",
    primaryKeys = ["playlist_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("media_key")],
)
data class PlaylistItemEntity(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "media_key") val mediaKey: String,
    /** Order within the playlist. Contiguous (0..n-1) after a reorder; may have gaps after removals. */
    @ColumnInfo(name = "position") val position: Int,
)

/** `album` row plus aggregates, for album lists. */
data class AlbumWithStats(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "name") val name: String,
    /** Explicit cover, else the most recently added item, else null (empty album). */
    @ColumnInfo(name = "cover_key") val coverKey: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "sort_index") val sortIndex: Int,
    @ColumnInfo(name = "item_count") val itemCount: Int,
)

/** `playlist` row plus aggregates, for playlist lists. */
data class PlaylistWithStats(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** The first item (lowest position), or null when empty. */
    @ColumnInfo(name = "cover_key") val coverKey: String?,
    @ColumnInfo(name = "item_count") val itemCount: Int,
)
