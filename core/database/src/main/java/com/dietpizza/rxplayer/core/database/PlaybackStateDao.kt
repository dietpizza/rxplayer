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

    /** History rows (plan §7.2): local only, played at least once, most recent first. */
    @Query(
        "SELECT * FROM playback_state WHERE source = 'LOCAL' AND last_played_at > 0 " +
            "ORDER BY last_played_at DESC LIMIT :limit",
    )
    fun observeHistory(limit: Int): Flow<List<PlaybackStateEntity>>

    /**
     * Clears History without losing resume positions / watched state: rows stay, but drop out of
     * [observeHistory] until played again.
     */
    @Query("UPDATE playback_state SET last_played_at = 0 WHERE source = 'LOCAL'")
    suspend fun clearHistory()

    @Query("UPDATE playback_state SET last_played_at = 0 WHERE media_key IN (:mediaKeys)")
    suspend fun removeFromHistory(mediaKeys: List<String>)
}
