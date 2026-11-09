package com.dietpizza.rxplayer.core.player.zoom

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Aspect modes of the bottom-bar cycle button (plan §6.3): Fit → Fill → Crop → 100% → Stretch.
 *
 * All modes are expressed as a scale applied on top of the *fit* rectangle (the video fitted
 * inside the container, letterboxed), so the surface itself never resizes: switching modes or
 * pinching is a graphicsLayer change only.
 * - [Fit]: the whole frame visible.
 * - [Fill]: fills the container's width (bars top/bottom may remain for very wide video).
 * - [Crop]: covers the whole container, cropping the overflow.
 * - [Original]: one video pixel per screen pixel (100%).
 * - [Stretch]: fills the container non-uniformly (distorts).
 */
enum class AspectMode(val label: String) {
    Fit("Fit"),
    Fill("Fill"),
    Crop("Crop"),
    Original("100%"),
    Stretch("Stretch"),
    ;

    fun next(): AspectMode = entries[(ordinal + 1) % entries.size]
}

/** Pure scale math for [AspectMode] and pinch zoom. No allocation. */
object VideoScaling {

    /** Width of the video fitted inside the container (0 if anything is unknown). */
    fun fitWidth(containerW: Float, containerH: Float, videoW: Float, videoH: Float): Float {
        if (containerW <= 0f || containerH <= 0f || videoW <= 0f || videoH <= 0f) return 0f
        return min(containerW / videoW, containerH / videoH) * videoW
    }

    fun fitHeight(containerW: Float, containerH: Float, videoW: Float, videoH: Float): Float {
        if (containerW <= 0f || containerH <= 0f || videoW <= 0f || videoH <= 0f) return 0f
        return min(containerW / videoW, containerH / videoH) * videoH
    }

    /** Horizontal scale of [mode] relative to Fit. */
    fun scaleX(mode: AspectMode, containerW: Float, containerH: Float, videoW: Float, videoH: Float): Float {
        val fw = fitWidth(containerW, containerH, videoW, videoH)
        val fh = fitHeight(containerW, containerH, videoW, videoH)
        if (fw <= 0f || fh <= 0f) return 1f
        return when (mode) {
            AspectMode.Fit -> 1f
            AspectMode.Fill -> containerW / fw
            AspectMode.Crop -> max(containerW / fw, containerH / fh)
            AspectMode.Original -> videoW / fw
            AspectMode.Stretch -> containerW / fw
        }
    }

    /** Vertical scale of [mode] relative to Fit. */
    fun scaleY(mode: AspectMode, containerW: Float, containerH: Float, videoW: Float, videoH: Float): Float {
        val fw = fitWidth(containerW, containerH, videoW, videoH)
        val fh = fitHeight(containerW, containerH, videoW, videoH)
        if (fw <= 0f || fh <= 0f) return 1f
        return when (mode) {
            AspectMode.Stretch -> containerH / fh
            else -> scaleX(mode, containerW, containerH, videoW, videoH)
        }
    }

    /** The uniform zoom that covers the container ("Fill/crop" snap point of a pinch). */
    fun coverZoom(containerW: Float, containerH: Float, videoW: Float, videoH: Float): Float =
        scaleX(AspectMode.Crop, containerW, containerH, videoW, videoH)
}

/**
 * Pinch-zoom snapping (plan §6.2: "snaps to Fit and Fill/crop"). Zoom values are relative to
 * Fit (1 = Fit). While pinching, values near a snap point stick to it (magnetic); on release
 * the zoom settles on a snap point if close, or stays free otherwise. Zooming out below Fit is
 * allowed while the fingers are down (rubber band) but always settles back on Fit.
 */
object ZoomSnap {
    const val MIN_ZOOM = 0.5f
    const val MAX_ZOOM = 4f

    /** Relative distance within which a live pinch sticks to a snap point. */
    const val MAGNET = 0.04f

    /** Relative distance within which a release settles on a snap point. */
    const val SETTLE = 0.10f

    /** Live value while pinching: clamped, and magnetic around Fit and [coverZoom]. */
    fun live(zoom: Float, coverZoom: Float): Float {
        val z = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (near(z, 1f, MAGNET)) return 1f
        if (coverZoom > 1f && near(z, coverZoom, MAGNET)) return coverZoom
        return z
    }

    /** Where the zoom settles when the fingers lift. */
    fun settle(zoom: Float, coverZoom: Float): Float {
        val z = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (z <= 1f || near(z, 1f, SETTLE)) return 1f
        if (coverZoom > 1f && near(z, coverZoom, SETTLE)) return coverZoom
        return z
    }

    /** Rounded percentage for the indicator (relative to Fit). */
    fun percent(zoom: Float): Int = (zoom * 100f + 0.5f).toInt()

    private fun near(value: Float, target: Float, tolerance: Float) = abs(value - target) <= target * tolerance
}
