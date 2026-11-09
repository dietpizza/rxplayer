package com.dietpizza.rxplayer.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * v1: `playback_state`.
 * v2 (M5): `favourite`, `album`, `album_item`, `playlist`, `playlist_item` (plan §7.2), added by
 * an [AutoMigration] (new tables only, no spec needed). Schemas are exported to `schemas/`.
 */
@Database(
    entities = [
        PlaybackStateEntity::class,
        FavouriteEntity::class,
        AlbumEntity::class,
        AlbumItemEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class RxDatabase : RoomDatabase() {
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun favouriteDao(): FavouriteDao
    abstract fun albumDao(): AlbumDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun mediaKeyDao(): MediaKeyDao

    companion object {
        const val NAME = "rx.db"
    }
}
