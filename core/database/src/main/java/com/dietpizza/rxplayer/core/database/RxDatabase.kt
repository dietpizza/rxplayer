package com.dietpizza.rxplayer.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/** v1: only `playback_state`. favourite/album/playlist/network_server arrive in M5/M6 with migrations. */
@Database(
    entities = [PlaybackStateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class RxDatabase : RoomDatabase() {
    abstract fun playbackStateDao(): PlaybackStateDao

    companion object {
        const val NAME = "rx.db"
    }
}
