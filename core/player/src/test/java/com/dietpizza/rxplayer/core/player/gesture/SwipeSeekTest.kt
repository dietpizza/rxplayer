package com.dietpizza.rxplayer.core.player.gesture

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeSeekTest {
    private val hour = 3_600_000L

    @Test
    fun fullWidthIsNinetySecondsForLongVideos() {
        assertEquals(90_000L, SwipeSeek.targetMs(0, 1000f, 1000f, 1f, hour))
        assertEquals(45_000L + 600_000L, SwipeSeek.targetMs(600_000, 500f, 1000f, 1f, hour))
    }

    @Test
    fun sensitivityScales() {
        assertEquals(180_000L, SwipeSeek.targetMs(0, 1000f, 1000f, 2f, hour))
        assertEquals(45_000L, SwipeSeek.targetMs(0, 1000f, 1000f, 0.5f, hour))
    }

    @Test
    fun clampsToZeroAndDuration() {
        assertEquals(0L, SwipeSeek.targetMs(10_000, -1000f, 1000f, 1f, hour))
        assertEquals(hour, SwipeSeek.targetMs(hour - 5_000, 1000f, 1000f, 1f, hour))
    }

    @Test
    fun shortClipsUseTheirLengthButNotBelowMinimum() {
        assertEquals(40_000L, SwipeSeek.fullWidthMs(40_000, 1f))
        assertEquals(20_000L, SwipeSeek.fullWidthMs(5_000, 1f))
        assertEquals(5_000L, SwipeSeek.targetMs(0, 1000f, 1000f, 1f, 5_000)) // clamped
    }

    @Test
    fun unknownDurationOnlyClampsAtZero() {
        assertEquals(90_000L, SwipeSeek.fullWidthMs(0, 1f))
        assertEquals(190_000L, SwipeSeek.targetMs(100_000, 1000f, 1000f, 1f, 0))
        assertEquals(0L, SwipeSeek.targetMs(1_000, -1000f, 1000f, 1f, 0))
    }

    @Test
    fun zeroWidthIsNoOp() {
        assertEquals(1_234L, SwipeSeek.targetMs(1_234, 500f, 0f, 1f, hour))
    }
}
