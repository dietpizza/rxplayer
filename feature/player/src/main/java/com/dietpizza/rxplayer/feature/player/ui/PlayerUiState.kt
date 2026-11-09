package com.dietpizza.rxplayer.feature.player.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dietpizza.rxplayer.core.model.PlayerSettings
import com.dietpizza.rxplayer.core.player.PlaybackController
import com.dietpizza.rxplayer.core.player.gesture.PlayerGestureSink
import com.dietpizza.rxplayer.core.player.gesture.SwipeSeek

/** Screen-local UI state of the full player (things the controller doesn't own). */
@Stable
internal class PlayerUiState {
    var controlsVisible by mutableStateOf(true)
        private set

    /** Bumped on every interaction; restarts the auto-hide timer. */
    var interaction by mutableIntStateOf(0)
        private set

    var seekBarDragging by mutableStateOf(false)

    var swipeSeeking by mutableStateOf(false)

    /** Read only inside the overlay leaf; written per drag frame. */
    var swipeStartMs by mutableLongStateOf(0L)
    var swipeTargetMs by mutableLongStateOf(0L)

    /** Centre pop after a double tap: which icon, and a counter that restarts the animation. */
    var popShowsPlay by mutableStateOf(false)
        private set
    var popKey by mutableIntStateOf(0)
        private set

    var showRemaining by mutableStateOf(false)

    val isDragging: Boolean get() = seekBarDragging || swipeSeeking

    private var visibilityBeforeTap = true

    fun bump() {
        interaction++
    }

    fun showControls(visible: Boolean) {
        controlsVisible = visible
        bump()
    }

    fun toggleControls() {
        visibilityBeforeTap = controlsVisible
        showControls(!controlsVisible)
    }

    /** The first tap of a double tap already toggled the controls: put them back. */
    fun undoTapToggle() {
        controlsVisible = visibilityBeforeTap
        bump()
    }

    fun pop(showsPlay: Boolean) {
        popShowsPlay = showsPlay
        popKey++
    }
}

/** Gesture intents → player actions. Holds no per-frame allocations. */
internal class PlayerGestureHandler(
    private val controller: PlaybackController,
    private val ui: PlayerUiState,
) : PlayerGestureSink {
    /** Updated from layout (onSizeChanged); not snapshot state. */
    var widthPx: Float = 0f

    /** Updated from composition (SideEffect). */
    var settings: PlayerSettings = PlayerSettings()

    private var boosting = false

    override fun onSingleTap(x: Float, y: Float) = ui.toggleControls()

    override fun onDoubleTap(x: Float, y: Float) {
        ui.undoTapToggle()
        val willPlay = !(controller.player.playWhenReady && !controller.isEnded.value)
        controller.togglePlayPause()
        ui.pop(showsPlay = willPlay)
    }

    override fun onLongPressStart(x: Float, y: Float) {
        if (!settings.longPressSpeedEnabled || !controller.player.isPlaying) return
        boosting = true
        controller.startSpeedBoost()
    }

    override fun onLongPressEnd() {
        if (!boosting) return
        boosting = false
        controller.endSpeedBoost()
    }

    override fun onHorizontalSeekStart() {
        if (controller.nowPlaying.value == null || controller.durationMs.value <= 0) return
        val start = controller.currentPositionMs
        ui.swipeStartMs = start
        ui.swipeTargetMs = start
        ui.swipeSeeking = true
        controller.beginScrub()
    }

    override fun onHorizontalSeek(totalDx: Float) {
        if (!ui.swipeSeeking) return
        val target = SwipeSeek.targetMs(
            startMs = ui.swipeStartMs,
            dxPx = totalDx,
            widthPx = widthPx,
            sensitivity = settings.swipeSeekSensitivity,
            durationMs = controller.durationMs.value,
        )
        if (target == ui.swipeTargetMs) return
        ui.swipeTargetMs = target
        controller.scrubTo(target)
    }

    override fun onHorizontalSeekEnd(cancelled: Boolean) {
        if (!ui.swipeSeeking) return
        ui.swipeSeeking = false
        controller.endScrub(ui.swipeTargetMs)
        ui.bump()
    }

    // Swipe-down (minimise/dismiss) and pinch-zoom are M3: plumbing only.
}
