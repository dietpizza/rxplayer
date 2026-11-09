package com.dietpizza.rxplayer.feature.player

import android.content.pm.ActivityInfo
import com.dietpizza.rxplayer.core.player.VideoDimensions
import com.dietpizza.rxplayer.feature.player.ui.formatDelta
import com.dietpizza.rxplayer.feature.player.ui.formatTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeFormatTest {
    @Test
    fun formatsMinutesAndHours() {
        assertEquals("0:00", formatTime(0))
        assertEquals("0:42", formatTime(42_999))
        assertEquals("12:31", formatTime(751_000))
        assertEquals("1:02:03", formatTime(3_723_000))
        assertEquals("0:00", formatTime(-5_000))
    }

    @Test
    fun formatsDeltas() {
        assertEquals("+0:42", formatDelta(42_000))
        assertEquals("-1:05", formatDelta(-65_000))
        assertEquals("+0:00", formatDelta(0))
    }

    @Test
    fun orientationFollowsAspect() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            PlayerActivity.orientationFor(VideoDimensions(1920, 1080), locked = false),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            PlayerActivity.orientationFor(VideoDimensions(1080, 1920), locked = false),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LOCKED,
            PlayerActivity.orientationFor(VideoDimensions(1920, 1080), locked = true),
        )
        assertNull(PlayerActivity.orientationFor(VideoDimensions.Unknown, locked = false))
    }
}
