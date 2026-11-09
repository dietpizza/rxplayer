package com.dietpizza.rxplayer.feature.player.viewer

import kotlin.math.max
import kotlin.math.min

/**
 * Pure zoom/pan math for the photo pages (unit tested, no allocation).
 *
 * The image is drawn Fit-centred in the container; on top of that a graphicsLayer applies
 * `scale` around the container centre and then `offset` (px). So a content point `q` (relative
 * to the centre, at fit size) lands at `centre + q * scale + offset`.
 */
internal object ZoomMath {
    const val MIN_SCALE = 1f
    const val MAX_SCALE = 6f

    /** Double-tap target when at fit (plan §4 #4). */
    const val DOUBLE_TAP_SCALE = 2.5f

    /** Pinching below fit rubber-bands down to this, then springs back on release. */
    const val MIN_GESTURE_SCALE = 0.75f

    /** Largest |offset| on one axis that keeps the scaled content covering the container edge. */
    fun maxOffset(contentSize: Float, containerSize: Float, scale: Float): Float =
        max(0f, (contentSize * scale - containerSize) / 2f)

    /** [offset] clamped so no gap opens between the content and the container edge. */
    fun clampOffset(offset: Float, contentSize: Float, containerSize: Float, scale: Float): Float {
        val m = maxOffset(contentSize, containerSize, scale)
        return offset.coerceIn(-m, m)
    }

    fun clampScale(scale: Float, min: Float = MIN_SCALE, max: Float = MAX_SCALE): Float = scale.coerceIn(min, max)

    /**
     * New offset on one axis after scaling from [oldScale] to [newScale] around [focus] (the
     * gesture centroid, relative to the container centre), plus a [pan]: the content point under
     * the focus stays under it.
     */
    fun offsetAfterZoom(offset: Float, focus: Float, oldScale: Float, newScale: Float, pan: Float): Float {
        if (oldScale <= 0f) return offset + pan
        return focus - (focus - offset) * (newScale / oldScale) + pan
    }

    /** Target of a double tap: fit when zoomed in, else [DOUBLE_TAP_SCALE]. */
    fun doubleTapScale(current: Float): Float = if (current > MIN_SCALE + 0.01f) MIN_SCALE else DOUBLE_TAP_SCALE

    /** Fit size of content with aspect [contentAspect] (w/h) inside the container: (w, h). */
    fun fitWidth(containerW: Float, containerH: Float, contentAspect: Float): Float {
        if (containerW <= 0f || containerH <= 0f || contentAspect <= 0f) return containerW
        return min(containerW, containerH * contentAspect)
    }

    fun fitHeight(containerW: Float, containerH: Float, contentAspect: Float): Float {
        if (containerW <= 0f || containerH <= 0f || contentAspect <= 0f) return containerH
        return min(containerH, containerW / contentAspect)
    }

    /**
     * Sample size for decoding a [sourceLong]-px long edge so it still covers [targetLong] px
     * (largest power of two that doesn't drop below it). ImageDecoder accepts any integer, but
     * powers of two are what the JPEG/HEIF decoders do natively and fastest.
     */
    fun sampleSize(sourceLong: Int, targetLong: Int): Int {
        if (sourceLong <= 0 || targetLong <= 0) return 1
        var s = 1
        while (sourceLong / (s * 2) >= targetLong) s *= 2
        return s
    }
}
