package com.dietpizza.rxplayer.core.player.gesture

import kotlin.math.roundToLong

/** Horizontal swipe → seek target mapping (plan §6.2). */
object SwipeSeek {
    /** A full-width swipe covers this much time at sensitivity 1×. */
    const val FULL_WIDTH_MS = 90_000L

    /** Short clips scale the full-width span down to their length, but never below this. */
    const val MIN_FULL_WIDTH_MS = 20_000L

    /** Milliseconds covered by a full-width swipe for a video of [durationMs] (≤ 0 = unknown). */
    fun fullWidthMs(durationMs: Long, sensitivity: Float): Long {
        val base = if (durationMs > 0) durationMs.coerceIn(MIN_FULL_WIDTH_MS, FULL_WIDTH_MS) else FULL_WIDTH_MS
        return (base * sensitivity.coerceIn(0.1f, 10f)).roundToLong()
    }

    /**
     * Seek target for a swipe of [dxPx] on a surface [widthPx] wide, started at [startMs].
     * Clamped to `0..durationMs` (lower bound only when the duration is unknown).
     */
    fun targetMs(startMs: Long, dxPx: Float, widthPx: Float, sensitivity: Float, durationMs: Long): Long {
        if (widthPx <= 0f) return startMs.coerceAtLeast(0)
        val delta = (dxPx / widthPx * fullWidthMs(durationMs, sensitivity)).roundToLong()
        val target = startMs + delta
        return if (durationMs > 0) target.coerceIn(0, durationMs) else target.coerceAtLeast(0)
    }
}
