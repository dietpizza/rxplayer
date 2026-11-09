package com.dietpizza.rxplayer.core.data

import com.dietpizza.rxplayer.core.database.PlaybackStateEntity
import com.dietpizza.rxplayer.core.model.MediaKey

internal const val COMPLETED_FRACTION = 0.95

/** True when [positionMs] is at least 95% of a known [durationMs]. */
fun isCompleted(positionMs: Long, durationMs: Long): Boolean =
    durationMs > 0 && positionMs >= durationMs * COMPLETED_FRACTION

/** LOCAL for `file://` keys, otherwise the upper-cased URI scheme (SMB, CONTENT, …). */
fun sourceOf(key: MediaKey): String {
    if (key.isLocalFile) return PlaybackStateEntity.SOURCE_LOCAL
    val scheme = key.value.substringBefore(':', missingDelimiterValue = "")
    return scheme.uppercase().ifEmpty { "UNKNOWN" }
}

/** Size/mtime fingerprint for local files (plan §7.1). */
data class FileFingerprint(val sizeBytes: Long, val mtime: Long)

/**
 * Pure "next row" rule for `saveProgress`:
 * - position/duration/title always overwritten; `completed` recomputed from the new position
 *   (so re-watching from the start clears it until 95% is reached again);
 * - `playCount` +1 and `lastPlayedAt = now` only when [countAsPlay], otherwise carried over;
 * - fingerprint replaced when provided, otherwise carried over.
 */
fun nextPlaybackState(
    existing: PlaybackStateEntity?,
    key: MediaKey,
    title: String,
    positionMs: Long,
    durationMs: Long,
    countAsPlay: Boolean,
    now: Long,
    fingerprint: FileFingerprint?,
): PlaybackStateEntity {
    val position = positionMs.coerceAtLeast(0)
    val duration = durationMs.coerceAtLeast(0)
    return PlaybackStateEntity(
        mediaKey = key.value,
        title = title,
        source = sourceOf(key),
        positionMs = position,
        durationMs = duration,
        completed = isCompleted(position, duration),
        playCount = (existing?.playCount ?: 0) + if (countAsPlay) 1 else 0,
        lastPlayedAt = if (countAsPlay) now else existing?.lastPlayedAt ?: 0L,
        sizeBytes = fingerprint?.sizeBytes ?: existing?.sizeBytes,
        mtime = fingerprint?.mtime ?: existing?.mtime,
    )
}
