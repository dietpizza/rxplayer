package com.dietpizza.rxplayer.feature.player

import android.content.pm.ActivityInfo
import com.dietpizza.rxplayer.core.player.VideoDimensions
import com.dietpizza.rxplayer.feature.player.ui.OrientationMode
import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationModeTest {
    private val landscape = VideoDimensions(1920, 1080)

    @Test
    fun explicitModesOverrideAspect() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
            PlayerActivity.orientationFor(landscape, locked = false, OrientationMode.Portrait),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            PlayerActivity.orientationFor(VideoDimensions(1080, 1920), locked = false, OrientationMode.Landscape),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,
            PlayerActivity.orientationFor(VideoDimensions.Unknown, locked = false, OrientationMode.Sensor),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LOCKED,
            PlayerActivity.orientationFor(landscape, locked = true, OrientationMode.Portrait),
        )
    }
}
