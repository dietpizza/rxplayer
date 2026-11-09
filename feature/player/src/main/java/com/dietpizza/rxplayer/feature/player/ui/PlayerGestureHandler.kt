package com.dietpizza.rxplayer.feature.player.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import com.dietpizza.rxplayer.core.model.PlayerSettings
import com.dietpizza.rxplayer.core.player.PlaybackController
import com.dietpizza.rxplayer.core.player.gesture.PlayerGestureSink
import com.dietpizza.rxplayer.core.player.gesture.SwipeSeek
import com.dietpizza.rxplayer.core.player.zoom.AspectMode
import com.dietpizza.rxplayer.core.player.zoom.VideoScaling
import com.dietpizza.rxplayer.core.player.zoom.ZoomSnap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Gesture intents → player actions. Per pointer event it only writes primitives into snapshot
 * state (no allocation); coroutines are launched only when a gesture ends (settle animations).
 */
internal class PlayerGestureHandler(
    private val controller: PlaybackController,
    private val ui: FullPlayerState,
    private val scope: CoroutineScope,
) : PlayerGestureSink {
    /** Updated from layout (onSizeChanged); not snapshot state. */
    var widthPx: Float = 0f
    var heightPx: Float = 0f

    /** Updated from composition (SideEffect). */
    var settings: PlayerSettings = PlayerSettings()
    var spatialSpec: AnimationSpec<Float>? = null

    /** Called once a swipe-down passes the threshold and is released. */
    var onDismiss: () -> Unit = {}

    private var boosting = false
    private var pinchStartZoom = 1f
    private var settleJob: Job? = null

    override fun onSingleTap(x: Float, y: Float) {
        if (ui.locked) ui.revealLockButton() else ui.toggleControls()
    }

    override fun onDoubleTap(x: Float, y: Float) {
        if (ui.locked) {
            ui.revealLockButton()
            return
        }
        ui.undoTapToggle()
        val willPlay = !(controller.player.playWhenReady && !controller.isEnded.value)
        controller.togglePlayPause()
        ui.pop(showsPlay = willPlay)
    }

    override fun onLongPressStart(x: Float, y: Float) {
        if (ui.locked || !settings.longPressSpeedEnabled || !controller.player.isPlaying) return
        boosting = true
        controller.startSpeedBoost()
    }

    override fun onLongPressEnd() {
        if (!boosting) return
        boosting = false
        controller.endSpeedBoost()
    }

    // region swipe seek

    override fun onHorizontalSeekStart() {
        if (ui.locked) return
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
    // endregion

    // region swipe down (plan §4.1)

    override fun onVerticalDownStart() {
        if (ui.locked || !settings.swipeDownToClose) return
        settleJob?.cancel()
        ui.isDismissDragging = true
    }

    override fun onVerticalDown(totalDy: Float) {
        if (!ui.isDismissDragging) return
        ui.dismissOffsetPx = totalDy
    }

    override fun onVerticalDownEnd(cancelled: Boolean) {
        if (!ui.isDismissDragging) return
        val passed = !cancelled && heightPx > 0f && ui.dismissOffsetPx >= heightPx * DISMISS_THRESHOLD
        if (passed) {
            onDismiss()
            return
        }
        val from = ui.dismissOffsetPx
        settleJob = scope.launch {
            try {
                animate(from, 0f, animationSpec = spatialSpec ?: androidx.compose.animation.core.spring()) { v, _ ->
                    ui.dismissOffsetPx = v
                }
            } finally {
                ui.isDismissDragging = false
            }
        }
    }
    // endregion

    // region pinch zoom (plan §6.2)

    override fun onPinchStart() {
        if (ui.locked || !settings.pinchToZoom) return
        settleJob?.cancel()
        pinchStartZoom = if (ui.customZoom > 0f) ui.customZoom else uniformZoomOf(ui.aspectMode)
        ui.pinching = true
    }

    override fun onPinch(scale: Float) {
        if (!ui.pinching) return
        val z = ZoomSnap.live(pinchStartZoom * scale, coverZoom())
        if (z == ui.scaleX && z == ui.scaleY) return
        ui.scaleX = z
        ui.scaleY = z
        ui.customZoom = z
    }

    override fun onPinchEnd(cancelled: Boolean) {
        if (!ui.pinching) return
        val cover = coverZoom()
        val target = ZoomSnap.settle(ui.scaleX, cover)
        // Snapping onto Fit / cover returns to that aspect mode; anything else stays free.
        when (target) {
            1f -> {
                ui.aspectMode = AspectMode.Fit
                ui.customZoom = 0f
            }
            cover -> {
                ui.aspectMode = AspectMode.Crop
                ui.customZoom = 0f
            }
            else -> ui.customZoom = target
        }
        val from = ui.scaleX
        settleJob = scope.launch {
            try {
                animate(from, target, animationSpec = spatialSpec ?: androidx.compose.animation.core.spring()) { v, _ ->
                    ui.scaleX = v
                    ui.scaleY = v
                }
            } finally {
                ui.pinching = false
            }
        }
    }
    // endregion

    /** Cycles Fit → Fill → Crop → 100% → Stretch (from any free zoom: back to Fit). */
    fun cycleAspect() {
        val next = if (ui.customZoom > 0f) AspectMode.Fit else ui.aspectMode.next()
        ui.customZoom = 0f
        setAspect(next)
    }

    fun setAspect(mode: AspectMode) {
        ui.customZoom = 0f
        ui.aspectMode = mode
        ui.toast(mode.label)
        val fromX = ui.scaleX
        val fromY = ui.scaleY
        val toX = scaleX(mode)
        val toY = scaleY(mode)
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(0f, 1f, animationSpec = spatialSpec ?: androidx.compose.animation.core.spring()) { t, _ ->
                ui.scaleX = fromX + (toX - fromX) * t
                ui.scaleY = fromY + (toY - fromY) * t
            }
        }
    }

    /** Container or video size changed: re-apply the current mode/zoom without animation. */
    fun relayout() {
        if (ui.pinching) return
        if (ui.customZoom > 0f) {
            ui.scaleX = ui.customZoom
            ui.scaleY = ui.customZoom
        } else {
            ui.scaleX = scaleX(ui.aspectMode)
            ui.scaleY = scaleY(ui.aspectMode)
        }
    }

    private fun videoW() = controller.videoSize.value.width.toFloat()
    private fun videoH() = controller.videoSize.value.height.toFloat()
    private fun scaleX(mode: AspectMode) = VideoScaling.scaleX(mode, widthPx, heightPx, videoW(), videoH())
    private fun scaleY(mode: AspectMode) = VideoScaling.scaleY(mode, widthPx, heightPx, videoW(), videoH())
    private fun coverZoom() = VideoScaling.coverZoom(widthPx, heightPx, videoW(), videoH())
    private fun uniformZoomOf(mode: AspectMode) = if (mode == AspectMode.Stretch) 1f else scaleX(mode)

    companion object {
        /** Fraction of the screen height a swipe-down must travel to close/minimise. */
        const val DISMISS_THRESHOLD = 0.18f
    }
}
