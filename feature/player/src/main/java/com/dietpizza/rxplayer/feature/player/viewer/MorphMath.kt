package com.dietpizza.rxplayer.feature.player.viewer

import androidx.compose.ui.geometry.Rect
import com.dietpizza.rxplayer.feature.player.ui.DISMISS_SHRINK

/**
 * Pure geometry of the viewer ↔ full player morph (plan §4.1), unit tested.
 *
 * Progress `t`: 0 = inline in the pager page, 1 = full player. Springs may overshoot slightly
 * past either end; everything here tolerates that (rects extrapolate, alphas/radii clamp).
 */
internal object MorphMath {
    /** Viewer chrome (top bar, actions, slim controls) is gone by this progress. */
    const val INLINE_CHROME_END = 0.35f

    /** Full player chrome starts fading in from this progress. */
    const val FULL_CHROME_START = 0.55f

    /** Drag-up distance, as a fraction of the window height, that maps to the whole morph. */
    const val DRAG_DISTANCE_FRACTION = 0.5f

    /** Release velocity (progress units per second) that commits regardless of position. */
    const val FLING_VELOCITY = 1.5f

    /** [area] (l, t, r, b) shrunk to [aspect] (w/h) and centred. Unknown aspect → the area. */
    fun fit(left: Float, top: Float, right: Float, bottom: Float, aspect: Float): Rect {
        val aw = right - left
        val ah = bottom - top
        if (aspect <= 0f || aw <= 0f || ah <= 0f) return Rect(left, top, right, bottom)
        val w: Float
        val h: Float
        if (aw / ah > aspect) {
            h = ah
            w = h * aspect
        } else {
            w = aw
            h = w / aspect
        }
        val l = left + (aw - w) / 2f
        val t = top + (ah - h) / 2f
        return Rect(l, t, l + w, t + h)
    }

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /** Rect between [from] (t = 0) and [to] (t = 1). Extrapolates for t outside 0..1. */
    fun lerp(from: Rect, to: Rect, t: Float): Rect = Rect(
        lerp(from.left, to.left, t),
        lerp(from.top, to.top, t),
        lerp(from.right, to.right, t),
        lerp(from.bottom, to.bottom, t),
    )

    /**
     * Where the full player draws the video mid swipe-down: translated by [offsetPx] and shrunk
     * around its centre like `FullPlayer` does, so a collapse can start exactly there.
     */
    fun dismissed(full: Rect, offsetPx: Float, containerH: Float): Rect {
        val f = if (containerH > 0f) (offsetPx / containerH).coerceIn(0f, 1f) else 0f
        val s = 1f - DISMISS_SHRINK * f
        val cx = full.center.x
        val cy = full.center.y + offsetPx
        val hw = full.width * s / 2f
        val hh = full.height * s / 2f
        return Rect(cx - hw, cy - hh, cx + hw, cy + hh)
    }

    fun inlineChromeAlpha(t: Float): Float = 1f - (t / INLINE_CHROME_END).coerceIn(0f, 1f)

    fun fullChromeAlpha(t: Float): Float = ((t - FULL_CHROME_START) / (1f - FULL_CHROME_START)).coerceIn(0f, 1f)

    /** Rounded inline card corners straighten as it grows. */
    fun cornerRadius(t: Float, inlineRadius: Float): Float = inlineRadius * (1f - t.coerceIn(0f, 1f))

    /** Progress after dragging up by [-totalDy] px (up is negative dy) from [start]. */
    fun dragProgress(start: Float, totalDy: Float, distancePx: Float): Float {
        if (distancePx <= 0f) return start
        return (start - totalDy / distancePx).coerceIn(0f, 1f)
    }

    /** Converts a vertical release velocity (px/s, down positive) to progress units per second. */
    fun velocityToProgress(velocityPx: Float, distancePx: Float): Float =
        if (distancePx <= 0f) 0f else -velocityPx / distancePx

    /** Where a released drag settles: a fling decides, otherwise the nearer end. */
    fun settleTarget(progress: Float, velocity: Float): Float = when {
        velocity > FLING_VELOCITY -> 1f
        velocity < -FLING_VELOCITY -> 0f
        progress > 0.5f -> 1f
        else -> 0f
    }
}
