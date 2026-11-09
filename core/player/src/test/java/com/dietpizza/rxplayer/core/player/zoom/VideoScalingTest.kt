package com.dietpizza.rxplayer.core.player.zoom

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoScalingTest {
    // 16:9 video on a 20:9 landscape phone: Fit pillarboxes.
    private val cw = 2400f
    private val ch = 1080f
    private val vw = 1920f
    private val vh = 1080f

    @Test
    fun aspectCycleOrderWraps() {
        val seen = generateSequence(AspectMode.Fit) { it.next() }.take(6).toList()
        assertEquals(
            listOf(AspectMode.Fit, AspectMode.Fill, AspectMode.Crop, AspectMode.Original, AspectMode.Stretch, AspectMode.Fit),
            seen,
        )
        assertEquals(listOf("Fit", "Fill", "Crop", "100%", "Stretch"), AspectMode.entries.map { it.label })
    }

    @Test
    fun scalesPerMode() {
        // Fit is 1920x1080 -> fits height exactly; fit width 1920.
        assertEquals(1f, VideoScaling.scaleX(AspectMode.Fit, cw, ch, vw, vh), 1e-4f)
        assertEquals(2400f / 1920f, VideoScaling.scaleX(AspectMode.Fill, cw, ch, vw, vh), 1e-4f)
        assertEquals(2400f / 1920f, VideoScaling.scaleY(AspectMode.Crop, cw, ch, vw, vh), 1e-4f)
        assertEquals(1f, VideoScaling.scaleX(AspectMode.Original, cw, ch, vw, vh), 1e-4f)
        // Stretch: width to the edges, height unchanged.
        assertEquals(1.25f, VideoScaling.scaleX(AspectMode.Stretch, cw, ch, vw, vh), 1e-4f)
        assertEquals(1f, VideoScaling.scaleY(AspectMode.Stretch, cw, ch, vw, vh), 1e-4f)
    }

    @Test
    fun originalIsOneToOnePixels() {
        // 640x360 video fitted into 2400x1080 is shown at 1920 wide; 100% shrinks it to 640.
        assertEquals(640f / 1920f, VideoScaling.scaleX(AspectMode.Original, cw, ch, 640f, 360f), 1e-4f)
    }

    @Test
    fun unknownSizesAreIdentity() {
        assertEquals(1f, VideoScaling.scaleX(AspectMode.Crop, 0f, 0f, vw, vh), 0f)
        assertEquals(1f, VideoScaling.scaleY(AspectMode.Stretch, cw, ch, 0f, 0f), 0f)
    }

    @Test
    fun liveZoomIsMagneticAndClamped() {
        val cover = 1.25f
        assertEquals(1f, ZoomSnap.live(1.03f, cover), 0f)
        assertEquals(cover, ZoomSnap.live(1.27f, cover), 0f)
        assertEquals(1.15f, ZoomSnap.live(1.15f, cover), 0f)
        assertEquals(ZoomSnap.MAX_ZOOM, ZoomSnap.live(10f, cover), 0f)
        assertEquals(ZoomSnap.MIN_ZOOM, ZoomSnap.live(0.1f, cover), 0f)
    }

    @Test
    fun releaseSettlesOnSnapPoints() {
        val cover = 1.25f
        assertEquals(1f, ZoomSnap.settle(0.7f, cover), 0f) // below Fit springs back
        assertEquals(1f, ZoomSnap.settle(1.08f, cover), 0f)
        assertEquals(cover, ZoomSnap.settle(1.35f, cover), 0f)
        assertEquals(2f, ZoomSnap.settle(2f, cover), 0f) // free zoom stays
        // Video already filling the screen: only Fit is a snap point.
        assertEquals(1.5f, ZoomSnap.settle(1.5f, 1f), 0f)
    }

    @Test
    fun percent() {
        assertEquals(100, ZoomSnap.percent(1f))
        assertEquals(125, ZoomSnap.percent(1.249f))
    }
}
