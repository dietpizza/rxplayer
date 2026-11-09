package com.dietpizza.rxplayer.feature.player.ui

import android.os.SystemClock
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import com.dietpizza.rxplayer.core.player.gesture.PlayerGestureMachine
import com.dietpizza.rxplayer.core.player.gesture.PlayerGestureMachine.Mode
import kotlin.math.sqrt

/**
 * Feeds raw pointer events into [machine] (all arbitration lives there, in `:core:player`).
 * Sequences whose down was consumed by a child (buttons, the seek bar) are ignored. Per event it
 * only walks the change list by index: no allocation of its own.
 */
internal fun Modifier.playerGestures(machine: PlayerGestureMachine): Modifier =
    pointerInput(machine) {
        machine.touchSlopPx = viewConfiguration.touchSlop
        machine.doubleTapTimeoutMs = viewConfiguration.doubleTapTimeoutMillis
        machine.longPressTimeoutMs = viewConfiguration.longPressTimeoutMillis
        machine.doubleTapSlopPx = DOUBLE_TAP_SLOP_DP * density
        awaitPointerEventScope {
            var primaryId = NO_POINTER
            try {
                while (true) {
                    val deadline = machine.nextDeadlineMs
                    val event: PointerEvent? = if (deadline == PlayerGestureMachine.NO_TIME) {
                        awaitPointerEvent()
                    } else {
                        val wait = deadline - SystemClock.uptimeMillis()
                        if (wait > 0) withTimeoutOrNull(wait) { awaitPointerEvent() } else null
                    }
                    if (event == null) {
                        machine.onTick(SystemClock.uptimeMillis())
                        continue
                    }
                    val changes = event.changes
                    var pressed = 0
                    var first: PointerInputChange? = null
                    var second: PointerInputChange? = null
                    var primary: PointerInputChange? = null
                    for (i in changes.indices) {
                        val c = changes[i]
                        if (c.id.value == primaryId) primary = c
                        if (!c.pressed) continue
                        pressed++
                        if (first == null) first = c else if (second == null) second = c
                    }

                    if (machine.mode == Mode.Idle) {
                        primaryId = NO_POINTER
                        val down = first ?: continue
                        if (event.type == PointerEventType.Press && pressed == 1 &&
                            !down.previousPressed && !down.isConsumed
                        ) {
                            primaryId = down.id.value
                            machine.onDown(down.uptimeMillis, down.position.x, down.position.y)
                        }
                        continue
                    }

                    val time = (primary ?: changes[0]).uptimeMillis
                    if (primary == null || !primary.pressed) {
                        // Primary finger lifted (or vanished): the gesture ends here even if
                        // other fingers remain; they're ignored until everything is up.
                        machine.onUp(time)
                        primaryId = NO_POINTER
                        continue
                    }
                    val span = if (first != null && second != null) {
                        val dx = first.position.x - second.position.x
                        val dy = first.position.y - second.position.y
                        sqrt(dx * dx + dy * dy)
                    } else {
                        0f
                    }
                    machine.onMove(time, primary.position.x, primary.position.y, pressed, span)
                    when (machine.mode) {
                        Mode.Seek, Mode.SwipeDown, Mode.Pinch, Mode.LongPress, Mode.Swallow ->
                            for (i in changes.indices) changes[i].consume()
                        Mode.Idle, Mode.Pending -> Unit
                    }
                }
            } finally {
                machine.cancel()
            }
        }
    }

private const val NO_POINTER = Long.MIN_VALUE

/** Max distance between the two taps of a double tap (platform default is 100dp). */
private const val DOUBLE_TAP_SLOP_DP = 100f
