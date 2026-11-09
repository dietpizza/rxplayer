package com.dietpizza.rxplayer.feature.player.viewer

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class MorphMathTest {
    private fun assertRect(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, 0.01f)
        assertEquals(expected.top, actual.top, 0.01f)
        assertEquals(expected.right, actual.right, 0.01f)
        assertEquals(expected.bottom, actual.bottom, 0.01f)
    }

    @Test
    fun fitCentresInArea() {
        // 16:9 in 1080x2400: full width, centred vertically.
        val r = MorphMath.fit(0f, 0f, 1080f, 2400f, 16f / 9f)
        assertRect(Rect(0f, 896.25f, 1080f, 1503.75f), r)
        // Unknown aspect: the area itself.
        assertRect(Rect(10f, 20f, 30f, 40f), MorphMath.fit(10f, 20f, 30f, 40f, 0f))
    }

    @Test
    fun lerpEndsAndOvershoot() {
        val a = Rect(0f, 0f, 100f, 100f)
        val b = Rect(100f, 100f, 300f, 300f)
        assertRect(a, MorphMath.lerp(a, b, 0f))
        assertRect(b, MorphMath.lerp(a, b, 1f))
        assertRect(Rect(50f, 50f, 200f, 200f), MorphMath.lerp(a, b, 0.5f))
        // Springs overshoot: extrapolates, no clamping.
        assertRect(Rect(110f, 110f, 320f, 320f), MorphMath.lerp(a, b, 1.1f))
    }

    @Test
    fun dismissedMatchesFullPlayerSwipeTransform() {
        val full = Rect(0f, 700f, 1000f, 1300f)
        assertRect(full, MorphMath.dismissed(full, 0f, 2000f))
        // 400 px down in a 2000 px window: f = 0.2 -> scale 0.96 around the moved centre.
        val r = MorphMath.dismissed(full, 400f, 2000f)
        assertEquals(1400f, r.center.y, 0.01f)
        assertEquals(500f, r.center.x, 0.01f)
        assertEquals(960f, r.width, 0.01f)
        assertEquals(576f, r.height, 0.01f)
    }

    @Test
    fun chromeAlphasCrossOverWithoutOverlap() {
        assertEquals(1f, MorphMath.inlineChromeAlpha(0f), 0f)
        assertEquals(0f, MorphMath.inlineChromeAlpha(MorphMath.INLINE_CHROME_END), 0f)
        assertEquals(0f, MorphMath.inlineChromeAlpha(1.05f), 0f)
        assertEquals(0f, MorphMath.fullChromeAlpha(MorphMath.FULL_CHROME_START), 0f)
        assertEquals(1f, MorphMath.fullChromeAlpha(1f), 0f)
        assertEquals(0f, MorphMath.fullChromeAlpha(-0.05f), 0f)
        // Never both visible at once.
        var t = 0f
        while (t <= 1f) {
            val both = MorphMath.inlineChromeAlpha(t) > 0f && MorphMath.fullChromeAlpha(t) > 0f
            assertEquals("t=$t", false, both)
            t += 0.01f
        }
    }

    @Test
    fun cornerRadiusStraightensAndClamps() {
        assertEquals(28f, MorphMath.cornerRadius(0f, 28f), 0f)
        assertEquals(14f, MorphMath.cornerRadius(0.5f, 28f), 0f)
        assertEquals(0f, MorphMath.cornerRadius(1.08f, 28f), 0f)
        assertEquals(28f, MorphMath.cornerRadius(-0.1f, 28f), 0f)
    }

    @Test
    fun dragUpMapsToProgress() {
        assertEquals(0f, MorphMath.dragProgress(0f, 0f, 1000f), 0f)
        assertEquals(0.5f, MorphMath.dragProgress(0f, -500f, 1000f), 0.0001f)
        assertEquals(1f, MorphMath.dragProgress(0f, -5000f, 1000f), 0f)
        // Dragging down from rest stays at rest.
        assertEquals(0f, MorphMath.dragProgress(0f, 300f, 1000f), 0f)
        // Upward fling: px/s negative -> positive progress velocity.
        assertEquals(2f, MorphMath.velocityToProgress(-2000f, 1000f), 0.0001f)
    }

    @Test
    fun releaseSettles() {
        assertEquals(1f, MorphMath.settleTarget(0.1f, 2f), 0f) // fling up commits
        assertEquals(0f, MorphMath.settleTarget(0.9f, -2f), 0f) // fling down cancels
        assertEquals(1f, MorphMath.settleTarget(0.6f, 0f), 0f)
        assertEquals(0f, MorphMath.settleTarget(0.4f, 0.5f), 0f)
    }
}
