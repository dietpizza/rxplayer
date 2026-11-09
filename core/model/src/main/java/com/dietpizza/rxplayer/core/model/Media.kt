package com.dietpizza.rxplayer.core.model

/**
 * Stable identity for a piece of media (plan §7.1).
 * - Local files: `file://<absolute path>` (it is also a valid URI ExoPlayer can open directly).
 * - External content URIs (ACTION_VIEW): the URI string itself.
 * - Network (M6): `<scheme>://<serverId>/<path>`.
 */
@JvmInline
value class MediaKey(val value: String) {
    val isLocalFile: Boolean get() = value.startsWith(FILE_PREFIX)

    /** Absolute path for local files, else null. */
    val filePath: String? get() = if (isLocalFile) value.removePrefix(FILE_PREFIX) else null

    override fun toString(): String = value

    companion object {
        private const val FILE_PREFIX = "file://"
        fun forFile(absolutePath: String): MediaKey = MediaKey(FILE_PREFIX + absolutePath)
    }
}

/** Saved progress for one media item (Room `playback_state`). */
data class PlaybackState(
    val key: MediaKey,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val playCount: Int,
    val lastPlayedAt: Long,
) {
    /** 0f..1f for progress bars. */
    val fraction: Float
        get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/** A local video from MediaStore, joined with what we know about it. */
data class VideoItem(
    val key: MediaKey,
    /** content:// URI from MediaStore, used for thumbnails. */
    val contentUri: String,
    val title: String,
    val path: String,
    val folderPath: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val dateModifiedMs: Long,
    /** null = never played → show NEW badge. */
    val playback: PlaybackState?,
) {
    val isNew: Boolean get() = playback == null
}

/** A folder that contains at least one video (Videos tab, MX-style). */
data class VideoFolder(
    val path: String,
    val name: String,
    val videoCount: Int,
    val newCount: Int,
    val totalSizeBytes: Long,
    /** MediaStore content URI of the most recent video, used as the folder thumbnail. */
    val coverContentUri: String?,
    val lastModifiedMs: Long,
)

enum class ResumeMode { ASK, AUTO, NEVER }

/** Player preferences (plan §8). M1 persists all of these; the Settings UI for them comes later. */
data class PlayerSettings(
    /** Global, sticky mute (plan §3.3). A fresh install starts muted. */
    val muted: Boolean = true,
    val seekStepSeconds: Int = 10,
    /** Multiplier on swipe-to-seek distance → time. 1f = default. */
    val swipeSeekSensitivity: Float = 1f,
    val doubleTapPlayPause: Boolean = true,
    val longPressSpeedEnabled: Boolean = true,
    val longPressSpeed: Float = 2f,
    val pinchToZoom: Boolean = true,
    val swipeDownToClose: Boolean = true,
    val seekBarThumbnails: Boolean = true,
    val resumeMode: ResumeMode = ResumeMode.ASK,
    val autoPlayNext: Boolean = true,
    /** Shuffle mode: next / auto-next pick a random item not yet played (sticky). */
    val shuffle: Boolean = false,
    val autoPip: Boolean = true,
    val pauseOnHeadphonesUnplugged: Boolean = true,
    val pauseDuringCalls: Boolean = true,
    val resumeAfterCall: Boolean = true,
)
