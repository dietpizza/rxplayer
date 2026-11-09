package com.dietpizza.rxplayer.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackStateDao {
    /** Every row. Re-emits on any write to the table (callers should `distinctUntilChanged`). */
    @Query("SELECT * FROM playback_state")
    fun observeAll(): Flow<List<PlaybackStateEntity>>

    @Query("SELECT * FROM playback_state WHERE media_key = :mediaKey")
    suspend fun get(mediaKey: String): PlaybackStateEntity?

    @Query("SELECT * FROM playback_state WHERE media_key = :mediaKey")
    fun observe(mediaKey: String): Flow<PlaybackStateEntity?>

    @Upsert
    suspend fun upsert(entity: PlaybackStateEntity)
}
