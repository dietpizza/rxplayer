package com.dietpizza.rxplayer.core.player.gesture

import kotlin.math.abs

/**
 * Receives the intents decided by [PlayerGestureMachine]. Callbacks take primitives only, so a
 * gesture never allocates per pointer event.
 */
interface PlayerGestureSink {
    /** Fired on the up of a tap, immediately (no waiting for the double-tap window). */
    fun onSingleTap(x: Float, y: Float) {}

    /**
     * Fired on the *down* of a second tap inside the double-tap window (instant response). The
     * rest of that pointer sequence is swallowed.
     */
    fun onDoubleTap(x: Float, y: Float) {}

    /**
     * Whether a double tap at [x]/[y] may chain: a further quick tap there fires another
     * [onDoubleTap] (e.g. tap-tap-tap keeps seeking). False makes the next tap a fresh one.
     */
    fun canChainDoubleTap(x: Float, y: Float): Boolean = false

    fun onLongPressStart(x: Float, y: Float) {}
    fun onLongPressEnd() {}

    fun onHorizontalSeekStart() {}

    /** [totalDx] is measured from the point where the horizontal axis locked (no slop jump). */
    fun onHorizontalSeek(totalDx: Float) {}
    fun onHorizontalSeekEnd(cancelled: Boolean) {}

    fun onVerticalDownStart() {}

    /** [totalDy] ≥ 0, measured from the point where the vertical axis locked. */
    fun onVerticalDown(totalDy: Float) {}
    fun onVerticalDownEnd(cancelled: Boolean) {}

    fun onPinchStart() {}

    /** Current span / span when the pinch started. */
    fun onPinch(scale: Float) {}
    fun onPinchEnd(cancelled: Boolean) {}
}

/**
 * Pure-Kotlin gesture arbitration for the full player (plan §6.2). One instance per gesture
 * surface; it owns no timers. The host feeds it pointer events and, when [nextDeadlineMs] is
 * set, calls [onTick] once that time passes (or simply keeps feeding events: every entry point
 * processes due deadlines first).
 *
 * Rules:
 * - Touch slop locks the axis: |dx| > |dy| → horizontal seek; downward → swipe-down; upward →
 *   ignored. A second finger before the lock → pinch. Once locked, nothing else can take over.
 * - A tap emits [PlayerGestureSink.onSingleTap] on up, immediately. A second down within
 *   [doubleTapTimeoutMs] of that up (and within [doubleTapSlopPx]) emits a double tap; when the
 *   sink allows it ([PlayerGestureSink.canChainDoubleTap]) each further quick tap emits another.
 * - Holding still for [longPressTimeoutMs] emits a long press (until up).
 * - There are no brightness or volume gestures.
 */
class PlayerGestureMachine(
    private val sink: PlayerGestureSink,
    var touchSlopPx: Float,
    var doubleTapSlopPx: Float,
    var doubleTapTimeoutMs: Long = DEFAULT_DOUBLE_TAP_TIMEOUT_MS,
    var longPressTimeoutMs: Long = DEFAULT_LONG_PRESS_TIMEOUT_MS,
) {
    var doubleTapEnabled: Boolean = true
    var longPressEnabled: Boolean = true
    var verticalDownEnabled: Boolean = true
    var pinchEnabled: Boolean = true

    enum class Mode { Idle, Pending, LongPress, Seek, SwipeDown, Pinch, Swallow }

    var mode: Mode = Mode.Idle
        private set

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var originX = 0f
    private var originY = 0f
    private var startSpan = 0f

    private var lastTapUpTime = NO_TIME
    private var lastTapX = 0f
    private var lastTapY = 0f

    /** The current [Mode.Swallow] sequence is a double tap (not a swallowed drag/pinch). */
    private var swallowingDoubleTap = false

    /** Uptime at which [onTick] has work to do, or [NO_TIME]. */
    val nextDeadlineMs: Long
        get() = if (mode == Mode.Pending && longPressEnabled) downTime + longPressTimeoutMs else NO_TIME

    /** First pointer down. */
    fun onDown(timeMs: Long, x: Float, y: Float) {
        if (mode != Mode.Idle) cancel()
        val tapTime = lastTapUpTime
        lastTapUpTime = NO_TIME
        if (doubleTapEnabled && tapTime != NO_TIME && timeMs - tapTime <= doubleTapTimeoutMs &&
            withinSlop(x - lastTapX, y - lastTapY, doubleTapSlopPx)
        ) {
            mode = Mode.Swallow
            swallowingDoubleTap = true
            downX = x
            downY = y
            sink.onDoubleTap(x, y)
            return
        }
        swallowingDoubleTap = false
        mode = Mode.Pending
        downX = x
        downY = y
        downTime = timeMs
    }

    /**
     * Pointer movement or a pointer-count change. [x]/[y] are the first pointer's position;
     * [span] is the distance between the first two pointers (0 when only one is down).
     */
    fun onMove(timeMs: Long, x: Float, y: Float, pointerCount: Int, span: Float) {
        onTick(timeMs)
        when (mode) {
            Mode.Pending -> {
                if (pointerCount >= 2) {
                    if (pinchEnabled) {
                        mode = Mode.Pinch
                        startSpan = span
                        sink.onPinchStart()
                    } else {
                        mode = Mode.Swallow
                    }
                    return
                }
                val dx = x - downX
                val dy = y - downY
                if (withinSlop(dx, dy, touchSlopPx)) return
                originX = x
                originY = y
                mode = when {
                    abs(dx) > abs(dy) -> Mode.Seek.also { sink.onHorizontalSeekStart() }
                    dy > 0 && verticalDownEnabled -> Mode.SwipeDown.also { sink.onVerticalDownStart() }
                    else -> Mode.Swallow
                }
            }
            Mode.Seek -> sink.onHorizontalSeek(x - originX)
            Mode.SwipeDown -> sink.onVerticalDown((y - originY).coerceAtLeast(0f))
            Mode.Pinch -> if (pointerCount >= 2 && span > 0f) {
                if (startSpan <= 0f) startSpan = span else sink.onPinch(span / startSpan)
            }
            Mode.Idle, Mode.LongPress, Mode.Swallow -> Unit
        }
    }

    /** Last pointer up. */
    fun onUp(timeMs: Long) {
        onTick(timeMs)
        when (mode) {
            Mode.Pending -> {
                sink.onSingleTap(downX, downY)
                lastTapUpTime = timeMs
                lastTapX = downX
                lastTapY = downY
            }
            Mode.LongPress -> sink.onLongPressEnd()
            Mode.Seek -> sink.onHorizontalSeekEnd(cancelled = false)
            Mode.SwipeDown -> sink.onVerticalDownEnd(cancelled = false)
            Mode.Pinch -> sink.onPinchEnd(cancelled = false)
            Mode.Swallow -> if (swallowingDoubleTap && sink.canChainDoubleTap(downX, downY)) {
                lastTapUpTime = timeMs
                lastTapX = downX
                lastTapY = downY
            }
            Mode.Idle -> Unit
        }
        mode = Mode.Idle
        swallowingDoubleTap = false
    }

    /** The sequence was taken away (e.g. a child consumed it, or the surface left composition). */
    fun cancel() {
        when (mode) {
            Mode.LongPress -> sink.onLongPressEnd()
            Mode.Seek -> sink.onHorizontalSeekEnd(cancelled = true)
            Mode.SwipeDown -> sink.onVerticalDownEnd(cancelled = true)
            Mode.Pinch -> sink.onPinchEnd(cancelled = true)
            Mode.Idle, Mode.Pending, Mode.Swallow -> Unit
        }
        mode = Mode.Idle
        lastTapUpTime = NO_TIME
    }

    /** Processes due deadlines (long press). */
    fun onTick(timeMs: Long) {
        if (mode == Mode.Pending && longPressEnabled && timeMs - downTime >= longPressTimeoutMs) {
            mode = Mode.LongPress
            sink.onLongPressStart(downX, downY)
        }
    }

    private fun withinSlop(dx: Float, dy: Float, slop: Float) = dx * dx + dy * dy <= slop * slop

    companion object {
        const val NO_TIME = Long.MIN_VALUE

        /** Matches the platform's ViewConfiguration.getDoubleTapTimeout(). */
        const val DEFAULT_DOUBLE_TAP_TIMEOUT_MS = 300L

        /** Platform default; hosts should pass ViewConfiguration.getLongPressTimeout(). */
        const val DEFAULT_LONG_PRESS_TIMEOUT_MS = 400L
    }
}
