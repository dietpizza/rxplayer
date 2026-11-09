package com.dietpizza.rxplayer.core.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.dietpizza.rxplayer.core.data.PlaybackStateRepository
import com.dietpizza.rxplayer.core.data.VideoRepository
import com.dietpizza.rxplayer.core.datastore.PlayerSettingsRepository
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.PlayerSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The item the player is on. */
data class NowPlaying(
    val key: MediaKey,
    val title: String,
    val index: Int,
    val count: Int,
) {
    val hasPrevious: Boolean get() = index > 0
    val hasNext: Boolean get() = index < count - 1
}

/** Shown before playing an item with saved progress when resume mode is ASK (decision 30). */
data class ResumePrompt(val key: MediaKey, val positionMs: Long)

/** Display size of the video, pixel aspect ratio applied. */
data class VideoDimensions(val width: Int, val height: Int) {
    val aspectRatio: Float get() = if (height == 0) 0f else width.toFloat() / height
    val isLandscape: Boolean get() = width > height
    val isPortrait: Boolean get() = height > width

    companion object {
        val Unknown = VideoDimensions(0, 0)
    }
}

/**
 * The app's single ExoPlayer (plan §3.2 "one player, many surfaces") plus everything around it:
 * playlist + next/prev/auto-next, resume, progress saving, sticky mute, live seek, and the
 * self-managed interruption policy. Audio focus is never requested (plan §3.3).
 *
 * Main-thread only.
 */
@OptIn(UnstableApi::class)
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoRepository: VideoRepository,
    private val playbackStateRepository: PlaybackStateRepository,
    private val settingsRepository: PlayerSettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    /** The ExoPlayer, for surfaces (`PlayerSurface` / `ContentFrame`). Don't drive it directly. */
    val player: ExoPlayer = ExoPlayer.Builder(
        context,
        DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true),
    )
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ false,
        )
        // We handle "becoming noisy" ourselves so it respects PlayerSettings.
        .setHandleAudioBecomingNoisy(false)
        .build()

    /**
     * What a `MediaSession` should wrap: routes play/next/previous/seek-step through this
     * controller (resume prompt, our own playlist) and advertises next/previous for media keys.
     */
    val sessionPlayer: Player = SessionPlayer(player)

    // region state
    private val _settings = MutableStateFlow(PlayerSettings())
    val settings: StateFlow<PlayerSettings> = _settings.asStateFlow()

    private val _isMuted = MutableStateFlow(PlayerSettings().muted)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)

    /** Actually producing frames/audio. */
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playWhenReady = MutableStateFlow(false)

    /** The user's intent (play vs pause); drives the play/pause button without buffering flicker. */
    val playWhenReady: StateFlow<Boolean> = _playWhenReady.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _isEnded = MutableStateFlow(false)
    val isEnded: StateFlow<Boolean> = _isEnded.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)

    /** Ticks every [POSITION_TICK_MS] while playing; also updated on seeks. Read it in leaf UI. */
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _bufferedMs = MutableStateFlow(0L)
    val bufferedMs: StateFlow<Long> = _bufferedMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)

    /** 0 while unknown. */
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _videoSize = MutableStateFlow(VideoDimensions.Unknown)
    val videoSize: StateFlow<VideoDimensions> = _videoSize.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _error = MutableStateFlow<PlayerError?>(null)
    val error: StateFlow<PlayerError?> = _error.asStateFlow()

    private val _resumePrompt = MutableStateFlow<ResumePrompt?>(null)
    val resumePrompt: StateFlow<ResumePrompt?> = _resumePrompt.asStateFlow()

    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _isSpeedBoosted = MutableStateFlow(false)
    val isSpeedBoosted: StateFlow<Boolean> = _isSpeedBoosted.asStateFlow()
    // endregion

    private var playlist: List<MediaKey> = emptyList()
    private var index = -1
    private var loadJob: Job? = null
    private var tickerJob: Job? = null
    private var session: ItemSession? = null
    private var lastSaveAt = 0L
    private val settingsLoaded = CompletableDeferred<Unit>()

    private var hostVisible = false
    private var resumeOnReturn = false
    private var pausedByCall = false

    private var scrubbing = false
    private var scrubWasPlaying = false
    private var speedBeforeBoost = 1f

    private val coalescer = SeekCoalescer({ positionMs, exact ->
        player.setSeekParameters(if (exact) SeekParameters.EXACT else SeekParameters.CLOSEST_SYNC)
        player.seekTo(positionMs)
        if (!exact) {
            handler.removeCallbacks(seekTimeout)
            handler.postDelayed(seekTimeout, SeekCoalescer.DEFAULT_TIMEOUT_MS)
        }
    })
    private val seekTimeout: Runnable = Runnable { coalescer.onTick(SystemClock.uptimeMillis()) }

    private val autoPause = AutoPauseMonitor(
        context = context,
        onBecomingNoisy = {
            if (_settings.value.pauseOnHeadphonesUnplugged && player.playWhenReady) pauseInternal()
        },
        onCallStateChanged = ::onCallStateChanged,
    )

    init {
        player.addListener(PlayerListener())
        player.volume = if (_isMuted.value) 0f else 1f
        scope.launch {
            settingsRepository.settings.collect { s ->
                _settings.value = s
                setVolume(s.muted)
                settingsLoaded.complete(Unit)
            }
        }
    }

    // region playlist

    /** Replaces the playlist and starts [startIndex] (resume rules applied). */
    fun setPlaylist(keys: List<MediaKey>, startIndex: Int) {
        if (keys.isEmpty()) return
        saveProgress()
        playlist = keys.toList()
        autoPause.start()
        load(startIndex.coerceIn(0, playlist.lastIndex))
    }

    /** True when [keys]/[startIndex] are what's already loaded (e.g. the activity was recreated). */
    fun isShowing(keys: List<MediaKey>, startIndex: Int): Boolean =
        playlist == keys && player.playbackState != Player.STATE_IDLE &&
            playlist.getOrNull(startIndex) != null

    fun next() {
        if (index < playlist.lastIndex) load(index + 1)
    }

    /** Restarts the current item if more than [RESTART_THRESHOLD_MS] in, else goes back one. */
    fun previous() {
        if (index <= 0 || player.currentPosition > RESTART_THRESHOLD_MS) seekTo(0) else load(index - 1)
    }

    private fun load(newIndex: Int) {
        saveProgress()
        index = newIndex
        val key = playlist[newIndex]
        loadJob?.cancel()
        session = null
        coalescer.reset()
        scrubbing = false
        _resumePrompt.value = null
        _error.value = null
        _isEnded.value = false
        _videoSize.value = VideoDimensions.Unknown
        _positionMs.value = 0
        _durationMs.value = 0
        player.pause()
        _nowPlaying.value = NowPlaying(key, fallbackTitle(key), newIndex, playlist.size)

        loadJob = scope.launch {
            val title = async { resolveTitle(key) }
            val saved = async {
                if (key.tracksProgress) runCatching { playbackStateRepository.get(key) }.getOrNull() else null
            }
            settingsLoaded.await()
            val decision = ResumePolicy.decide(_settings.value.resumeMode, saved.await())
            val resolvedTitle = title.await()
            session = ItemSession(key, resolvedTitle)
            _nowPlaying.value = NowPlaying(key, resolvedTitle, newIndex, playlist.size)

            val startMs = when (decision) {
                ResumeDecision.StartOver -> 0L
                is ResumeDecision.Resume -> decision.positionMs
                is ResumeDecision.Ask -> decision.positionMs
            }
            player.setSeekParameters(SeekParameters.EXACT)
            player.setMediaItem(mediaItem(key, resolvedTitle), startMs)
            player.prepare()
            _positionMs.value = startMs
            if (decision is ResumeDecision.Ask) {
                player.playWhenReady = false
                _resumePrompt.value = ResumePrompt(key, decision.positionMs)
            } else if (hostVisible) {
                player.playWhenReady = true
            } else {
                // Loaded before the UI started (or while it's away): play once it's visible.
                player.playWhenReady = false
                resumeOnReturn = true
            }
        }
    }

    /** Answers the resume prompt. */
    fun resolveResume(resume: Boolean) {
        if (_resumePrompt.value == null) return
        _resumePrompt.value = null
        if (!resume) {
            player.seekTo(0)
            _positionMs.value = 0
        }
        play()
    }

    /** Saves progress and unloads everything (the player screen closed). */
    fun stop() {
        saveProgress()
        loadJob?.cancel()
        tickerJob?.cancel()
        handler.removeCallbacks(seekTimeout)
        coalescer.reset()
        scrubbing = false
        player.stop()
        player.clearMediaItems()
        if (_isSpeedBoosted.value) endSpeedBoost()
        setSpeed(1f)
        playlist = emptyList()
        index = -1
        session = null
        resumeOnReturn = false
        pausedByCall = false
        autoPause.stop()
        _nowPlaying.value = null
        _resumePrompt.value = null
        _error.value = null
        _isEnded.value = false
        _videoSize.value = VideoDimensions.Unknown
        _positionMs.value = 0
        _durationMs.value = 0
    }
    // endregion

    // region transport

    fun play() {
        resumeOnReturn = false
        pausedByCall = false
        if (_resumePrompt.value != null) {
            resolveResume(resume = true)
            return
        }
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) {
            _error.value = null
            player.prepare()
        }
        player.play()
    }

    fun pause() {
        resumeOnReturn = false
        pausedByCall = false
        pauseInternal()
    }

    fun togglePlayPause() {
        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) pause() else play()
    }

    private fun pauseInternal() {
        player.pause()
    }

    fun seekTo(positionMs: Long) {
        val target = clampPosition(positionMs)
        player.setSeekParameters(SeekParameters.EXACT)
        player.seekTo(target)
        _positionMs.value = target
    }

    fun seekBy(deltaMs: Long) = seekTo(player.currentPosition + deltaMs)

    /** ⏩ — one seek step (PlayerSettings.seekStepSeconds). */
    fun seekForward() = seekBy(_settings.value.seekStepSeconds * 1_000L)

    /** ⏪ — one seek step. */
    fun seekBack() = seekBy(-_settings.value.seekStepSeconds * 1_000L)

    /** Current position straight from the player (for gesture start points). */
    val currentPositionMs: Long get() = player.currentPosition

    private fun clampPosition(ms: Long): Long {
        val dur = player.duration
        return if (dur != C.TIME_UNSET && dur > 0) ms.coerceIn(0, dur) else ms.coerceAtLeast(0)
    }
    // endregion

    // region live seek (decision 31)

    /** Starts a scrub (swipe-seek or seek-bar drag): playback holds while dragging. */
    fun beginScrub() {
        if (scrubbing) return
        scrubbing = true
        scrubWasPlaying = player.playWhenReady && player.playbackState != Player.STATE_ENDED
        if (scrubWasPlaying) player.pause()
        coalescer.reset()
    }

    /** Dragged to [positionMs]: a closest-sync seek, at most one in flight, latest target wins. */
    fun scrubTo(positionMs: Long) {
        if (!scrubbing) beginScrub()
        val target = clampPosition(positionMs)
        _positionMs.value = target
        coalescer.request(target, SystemClock.uptimeMillis())
    }

    /** Released at [positionMs]: one exact seek, then playback continues if it was playing. */
    fun endScrub(positionMs: Long) {
        if (!scrubbing) return
        scrubbing = false
        handler.removeCallbacks(seekTimeout)
        val target = clampPosition(positionMs)
        _positionMs.value = target
        _isEnded.value = false
        coalescer.commit(target)
        if (scrubWasPlaying) player.play()
        _playWhenReady.value = player.playWhenReady
    }
    // endregion

    // region mute / speed

    fun setMuted(muted: Boolean) {
        setVolume(muted)
        scope.launch { settingsRepository.setMuted(muted) }
    }

    fun toggleMute() = setMuted(!_isMuted.value)

    private fun setVolume(muted: Boolean) {
        _isMuted.value = muted
        player.volume = if (muted) 0f else 1f
    }

    fun setSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed)
    }

    /** Long-press boost (plan §6.2): [PlayerSettings.longPressSpeed] until [endSpeedBoost]. */
    fun startSpeedBoost() {
        if (_isSpeedBoosted.value) return
        speedBeforeBoost = player.playbackParameters.speed
        _isSpeedBoosted.value = true
        setSpeed(_settings.value.longPressSpeed)
    }

    fun endSpeedBoost() {
        if (!_isSpeedBoosted.value) return
        _isSpeedBoosted.value = false
        setSpeed(speedBeforeBoost)
    }
    // endregion

    // region host lifecycle (decision 28) and interruptions

    /** The player UI became visible. Resumes if we paused it on the way out. */
    fun onHostStarted() {
        hostVisible = true
        if (resumeOnReturn && !autoPause.inCall) {
            resumeOnReturn = false
            if (_resumePrompt.value == null) player.play()
        }
    }

    /** The player UI is no longer visible: no background play, so pause (and save). */
    fun onHostStopped() {
        hostVisible = false
        if (scrubbing) endScrub(_positionMs.value)
        if (_isSpeedBoosted.value) endSpeedBoost()
        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) {
            pauseInternal()
            resumeOnReturn = true
        }
        saveProgress()
    }

    private fun onCallStateChanged(inCall: Boolean) {
        val s = _settings.value
        if (inCall) {
            if (s.pauseDuringCalls && player.playWhenReady) {
                pauseInternal()
                pausedByCall = true
            }
        } else if (pausedByCall) {
            pausedByCall = false
            if (s.resumeAfterCall) {
                if (hostVisible) player.play() else resumeOnReturn = true
            }
        }
    }
    // endregion

    fun dismissError() {
        _error.value = null
    }

    // region progress saving

    private fun saveProgress() {
        val s = session ?: return
        if (!s.started || !s.key.tracksProgress) return
        if (player.currentMediaItem?.mediaId != s.key.value) return
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0) return
        val position = if (player.playbackState == Player.STATE_ENDED) duration else player.currentPosition
        val countAsPlay = !s.counted
        s.counted = true
        lastSaveAt = SystemClock.elapsedRealtime()
        scope.launch {
            runCatching {
                playbackStateRepository.saveProgress(s.key, s.title, position, duration, countAsPlay)
            }
        }
    }

    private fun updateTicker() {
        if (!player.isPlaying) {
            tickerJob?.cancel()
            tickerJob = null
            return
        }
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                publishPosition()
                if (SystemClock.elapsedRealtime() - lastSaveAt >= SAVE_INTERVAL_MS) saveProgress()
                delay(POSITION_TICK_MS)
            }
        }
    }

    private fun publishPosition() {
        if (!scrubbing) _positionMs.value = player.currentPosition
        _bufferedMs.value = player.bufferedPosition
        val dur = player.duration
        _durationMs.value = if (dur == C.TIME_UNSET) 0 else dur
    }
    // endregion

    private fun onEnded() {
        _isEnded.value = true
        saveProgress()
        if (_settings.value.autoPlayNext && index < playlist.lastIndex) load(index + 1)
    }

    private inner class PlayerListener : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                session?.let {
                    if (!it.started) {
                        it.started = true
                        lastSaveAt = SystemClock.elapsedRealtime()
                    }
                }
            } else {
                saveProgress()
            }
            publishPosition()
            updateTicker()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // A scrub holds playback internally; the user's play/pause intent hasn't changed.
            if (!scrubbing) _playWhenReady.value = playWhenReady
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _isBuffering.value = playbackState == Player.STATE_BUFFERING
            if (playbackState != Player.STATE_ENDED) _isEnded.value = false
            if (playbackState == Player.STATE_READY) {
                handler.removeCallbacks(seekTimeout)
                coalescer.onSeekProcessed(SystemClock.uptimeMillis())
            }
            publishPosition()
            if (playbackState == Player.STATE_ENDED) onEnded()
        }

        override fun onRenderedFirstFrame() {
            if (coalescer.inFlight) {
                handler.removeCallbacks(seekTimeout)
                coalescer.onSeekProcessed(SystemClock.uptimeMillis())
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) = publishPosition()

        override fun onTimelineChanged(timeline: Timeline, reason: Int) = publishPosition()

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width == 0 || videoSize.height == 0) return
            val width = (videoSize.width * videoSize.pixelWidthHeightRatio).toInt()
            _videoSize.value = VideoDimensions(width, videoSize.height)
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            _speed.value = playbackParameters.speed
        }

        override fun onPlayerError(error: PlaybackException) {
            _error.value = PlaybackErrors.describe(error)
            saveProgress()
        }

        override fun onTracksChanged(tracks: Tracks) {
            if (_error.value?.fatal == true) return
            _error.value = unsupportedTrackWarning(tracks, C.TRACK_TYPE_VIDEO)
                ?: unsupportedTrackWarning(tracks, C.TRACK_TYPE_AUDIO)
        }
    }

    private fun unsupportedTrackWarning(tracks: Tracks, type: Int): PlayerError? {
        var firstUnsupported: androidx.media3.common.Format? = null
        for (group in tracks.groups) {
            if (group.type != type) continue
            if (group.isSupported) return null
            if (firstUnsupported == null && group.length > 0) firstUnsupported = group.getTrackFormat(0)
        }
        return firstUnsupported?.let { PlaybackErrors.unsupportedTrack(type, it) }
    }

    // region titles / media items

    private fun mediaItem(key: MediaKey, title: String): MediaItem {
        val uri = key.filePath?.let { Uri.fromFile(File(it)) } ?: Uri.parse(key.value)
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(key.value)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setDisplayTitle(title).build())
            .build()
    }

    private suspend fun resolveTitle(key: MediaKey): String {
        runCatching { videoRepository.video(key) }.getOrNull()?.let { return it.title }
        if (key.value.startsWith("content://")) {
            val name = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.query(
                        Uri.parse(key.value),
                        arrayOf(OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null,
                    )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                }.getOrNull()
            }
            if (!name.isNullOrBlank()) return name.substringBeforeLast('.', name)
        }
        return fallbackTitle(key)
    }

    private fun fallbackTitle(key: MediaKey): String {
        key.filePath?.let { return File(it).nameWithoutExtension.ifEmpty { File(it).name } }
        val segment = Uri.parse(key.value).lastPathSegment ?: return key.value
        return Uri.decode(segment).substringAfterLast('/').substringBeforeLast('.')
    }
    // endregion

    /**
     * Plain content URIs (ACTION_VIEW from other apps, not mappable to a file) get no resume or
     * history (plan §4 #15): they're often one-off grants that won't resolve again.
     */
    private val MediaKey.tracksProgress: Boolean get() = !value.startsWith("content://")

    private class ItemSession(val key: MediaKey, val title: String) {
        /** Playback actually started; before this we never overwrite saved progress. */
        var started = false

        /** countAsPlay already sent for this item this session. */
        var counted = false
    }

    /** Media-session facing wrapper: our playlist, our resume prompt, our seek step. */
    private inner class SessionPlayer(player: Player) : ForwardingPlayer(player) {
        override fun play() = this@PlaybackController.play()
        override fun pause() = this@PlaybackController.pause()
        override fun setPlayWhenReady(playWhenReady: Boolean) =
            if (playWhenReady) this@PlaybackController.play() else this@PlaybackController.pause()

        override fun seekToNext() = next()
        override fun seekToNextMediaItem() = next()
        override fun seekToPrevious() = previous()
        override fun seekToPreviousMediaItem() = previous()
        override fun seekForward() = this@PlaybackController.seekForward()
        override fun seekBack() = this@PlaybackController.seekBack()
        override fun hasNextMediaItem(): Boolean = index < playlist.lastIndex
        override fun hasPreviousMediaItem(): Boolean = index > 0

        override fun isCommandAvailable(command: Int): Boolean =
            command in EXTRA_COMMANDS || super.isCommandAvailable(command)

        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon().addAll(*EXTRA_COMMANDS).build()
    }

    companion object {
        const val POSITION_TICK_MS = 250L
        const val SAVE_INTERVAL_MS = 5_000L
        const val RESTART_THRESHOLD_MS = 3_000L

        private val EXTRA_COMMANDS = intArrayOf(
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD,
        )
    }
}
