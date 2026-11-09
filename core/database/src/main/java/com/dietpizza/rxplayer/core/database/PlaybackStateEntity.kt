package com.dietpizza.rxplayer.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.PlaybackState

/** Room `playback_state` (plan §7.2): resume + watched + history, one row per media. */
@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey @ColumnInfo(name = "media_key") val mediaKey: String,
    @ColumnInfo(name = "title") val title: String,
    /** LOCAL | SMB | WEBDAV | FTP | SFTP | CONTENT ... */
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "completed") val completed: Boolean,
    @ColumnInfo(name = "play_count") val playCount: Int,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long,
    /** Re-match fingerprint (nullable: unknown for non-file sources). */
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long?,
    @ColumnInfo(name = "mtime") val mtime: Long?,
) {
    companion object {
        const val SOURCE_LOCAL = "LOCAL"
    }
}

fun PlaybackStateEntity.toModel(): PlaybackState = PlaybackState(
    key = MediaKey(mediaKey),
    title = title,
    positionMs = positionMs,
    durationMs = durationMs,
    completed = completed,
    playCount = playCount,
    lastPlayedAt = lastPlayedAt,
)
