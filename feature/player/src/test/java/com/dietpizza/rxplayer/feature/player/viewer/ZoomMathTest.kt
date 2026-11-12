package com.dietpizza.rxplayer.feature.player.viewer

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomMathTest {
    @Test
    fun noPanAtFitOrWhenContentIsSmaller() {
        assertEquals(0f, ZoomMath.maxOffset(contentSize = 1000f, containerSize = 1000f, scale = 1f), 0f)
        assertEquals(0f, ZoomMath.maxOffset(contentSize = 500f, containerSize = 1000f, scale = 1.5f), 0f)
        assertEquals(0f, ZoomMath.clampOffset(120f, 1000f, 1000f, 1f), 0f)
    }

    @Test
    fun panClampedToContentEdges() {
        // 1000 px content at 2x in a 1000 px container: 500 px of slack each side.
        assertEquals(500f, ZoomMath.maxOffset(1000f, 1000f, 2f), 0f)
        assertEquals(500f, ZoomMath.clampOffset(900f, 1000f, 1000f, 2f), 0f)
        assertEquals(-500f, ZoomMath.clampOffset(-900f, 1000f, 1000f, 2f), 0f)
        assertEquals(200f, ZoomMath.clampOffset(200f, 1000f, 1000f, 2f), 0f)
    }

    @Test
    fun zoomKeepsFocusPointFixed() {
        // Focus 100 px right of centre, no offset, 1x -> 2x: content must shift left by 100.
        assertEquals(-100f, ZoomMath.offsetAfterZoom(0f, 100f, 1f, 2f, 0f), 0.001f)
        // Zooming about the centre never moves anything.
        assertEquals(30f, ZoomMath.offsetAfterZoom(30f, 0f, 1f, 1f, 0f), 0.001f)
        // The content point under the focus stays there: q = (f - off)/s before and after.
        val off = ZoomMath.offsetAfterZoom(40f, -250f, 1.5f, 3f, 0f)
        assertEquals((-250f - 40f) / 1.5f, (-250f - off) / 3f, 0.001f)
        // Pan adds on top.
        assertEquals(-90f, ZoomMath.offsetAfterZoom(0f, 100f, 1f, 2f, 10f), 0.001f)
    }

    @Test
    fun doubleTapTogglesFitAndZoom() {
        assertEquals(ZoomMath.DOUBLE_TAP_SCALE, ZoomMath.doubleTapScale(1f), 0f)
        assertEquals(1f, ZoomMath.doubleTapScale(2.5f), 0f)
        assertEquals(1f, ZoomMath.doubleTapScale(1.3f), 0f)
    }

    @Test
    fun doubleTapFillsTheScreenWhenThatZoomsFurther() {
        // 4:3 photo fit to 1080x810 in a 1080x2400 window: filling the height takes 2400/810.
        val fill = ZoomMath.fillScale(1080f, 810f, 1080f, 2400f)
        assertEquals(2400f / 810f, fill, 0.001f)
        assertEquals(fill, ZoomMath.doubleTapScale(1f, fill), 0.001f)
        // A photo that nearly fills already still zooms by the default step.
        assertEquals(ZoomMath.DOUBLE_TAP_SCALE, ZoomMath.doubleTapScale(1f, 1.1f), 0f)
        // A panorama never zooms past the max.
        assertEquals(ZoomMath.MAX_SCALE, ZoomMath.doubleTapScale(1f, 20f), 0f)
    }

    @Test
    fun pinchPastLimitsIsDampedAndBounded() {
        // Inside the range a pinch applies as is.
        assertEquals(3f, ZoomMath.rubberBandScale(2f, 1.5f), 0.001f)
        // Past the max only part of it applies.
        val over = ZoomMath.rubberBandScale(ZoomMath.MAX_SCALE, 1.5f)
        assertEquals(ZoomMath.MAX_SCALE * 1.15f, over, 0.001f)
        // Never past the gesture limits.
        assertEquals(ZoomMath.MAX_GESTURE_SCALE, ZoomMath.rubberBandScale(ZoomMath.MAX_GESTURE_SCALE, 3f), 0.001f)
        assertEquals(ZoomMath.MIN_GESTURE_SCALE, ZoomMath.rubberBandScale(ZoomMath.MIN_GESTURE_SCALE, 0.2f), 0.001f)
        // Pinching back in from past a limit isn't damped.
        assertEquals(ZoomMath.MAX_SCALE * 1.1f * 0.8f, ZoomMath.rubberBandScale(ZoomMath.MAX_SCALE * 1.1f, 0.8f), 0.001f)
    }

    @Test
    fun scaleClamp() {
        assertEquals(1f, ZoomMath.clampScale(0.5f), 0f)
        assertEquals(ZoomMath.MAX_SCALE, ZoomMath.clampScale(40f), 0f)
    }

    @Test
    fun fitSize() {
        // 4:3 in a 1080x2400 portrait window: width-bound.
        assertEquals(1080f, ZoomMath.fitWidth(1080f, 2400f, 4f / 3f), 0.01f)
        assertEquals(810f, ZoomMath.fitHeight(1080f, 2400f, 4f / 3f), 0.01f)
        // 9:16 in a landscape window: height-bound.
        assertEquals(1080f, ZoomMath.fitHeight(2400f, 1080f, 9f / 16f), 0.01f)
    }

    @Test
    fun sampleSizeNeverDropsBelowTarget() {
        assertEquals(1, ZoomMath.sampleSize(4000, 2400))
        assertEquals(2, ZoomMath.sampleSize(8000, 2400))
        assertEquals(4, ZoomMath.sampleSize(12000, 2400))
        assertEquals(1, ZoomMath.sampleSize(1000, 2400))
        assertEquals(1, ZoomMath.sampleSize(0, 2400))
    }
}
