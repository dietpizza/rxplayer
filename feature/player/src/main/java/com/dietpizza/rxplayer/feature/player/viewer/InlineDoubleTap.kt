package com.dietpizza.rxplayer.feature.player.viewer

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.dietpizza.rxplayer.core.player.PlaybackController
import com.dietpizza.rxplayer.feature.player.ui.DoubleTapZone
import com.dietpizza.rxplayer.feature.player.ui.PopKind

/** The ⏪/⏩ pop of the inline player; repeated taps on the same side add up, like the full player. */
@Stable
internal class InlinePopState {
    var kind by mutableStateOf(PopKind.Forward)
        private set
    var seconds by mutableIntStateOf(0)
        private set
    var key by mutableIntStateOf(0)
        private set
    private var lastAtMs = 0L

    fun pop(kind: PopKind, seconds: Int = 0) {
        val now = SystemClock.uptimeMillis()
        val chained = kind == this.kind && kind.isSeek && now - lastAtMs < POP_CHAIN_MS
        this.seconds = if (chained) this.seconds + seconds else seconds
        this.kind = kind
        lastAtMs = now
        key++
    }
}

/**
 * Taps on an inline video page. A single tap toggles the chrome straight away (no double-tap
 * wait); a second tap on a side third seeks back/forward by the seek step and undoes that toggle,
 * and further quick taps on the same side keep seeking. A double tap in the middle plays/pauses
 * when the player's "double tap to play/pause" setting is on. Seeking only works while [enabled]
 * (this page is playing and the player is inline); otherwise every tap is a plain tap.
 */
internal fun Modifier.inlineVideoTaps(
    controller: PlaybackController,
    pop: InlinePopState,
    enabled: () -> Boolean,
    onTap: () -> Unit,
): Modifier = pointerInput(controller) {
    val slop = DOUBLE_TAP_SLOP.toPx()
    var lastUpMs = 0L
    var lastPos = Offset.Zero
    var lastZone = DoubleTapZone.Middle
    var chaining = false
    awaitEachGesture {
        val down = awaitFirstDown()
        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
        if (up.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis) return@awaitEachGesture
        val zone = DoubleTapZone.of(up.position.x, size.width.toFloat())
        val quick = down.uptimeMillis - lastUpMs < viewConfiguration.doubleTapTimeoutMillis &&
            (up.position - lastPos).getDistance() < slop && zone == lastZone
        val settings = controller.settings.value
        val seconds = settings.seekStepSeconds
        val middleDouble = zone == DoubleTapZone.Middle && settings.doubleTapPlayPause
        if (quick && enabled() && (zone != DoubleTapZone.Middle || (middleDouble && !chaining))) {
            up.consume()
            // The first tap of this double tap toggled the chrome: put it back (once per chain).
            if (!chaining) onTap()
            when (zone) {
                DoubleTapZone.Left -> {
                    controller.seekBack()
                    pop.pop(PopKind.Back, seconds)
                }
                DoubleTapZone.Right -> {
                    controller.seekForward()
                    pop.pop(PopKind.Forward, seconds)
                }
                DoubleTapZone.Middle -> {
                    val willPlay = !(controller.player.playWhenReady && !controller.isEnded.value)
                    controller.togglePlayPause()
                    pop.pop(if (willPlay) PopKind.Play else PopKind.Pause)
                }
            }
            chaining = zone != DoubleTapZone.Middle
            // A middle double tap is done: a third tap there is a fresh single tap.
            if (!chaining) {
                lastUpMs = 0L
                return@awaitEachGesture
            }
        } else {
            onTap()
            chaining = false
        }
        lastUpMs = up.uptimeMillis
        lastPos = up.position
        lastZone = zone
    }
}

private val DOUBLE_TAP_SLOP = 100.dp
private const val POP_CHAIN_MS = 1_200L
