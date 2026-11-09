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
