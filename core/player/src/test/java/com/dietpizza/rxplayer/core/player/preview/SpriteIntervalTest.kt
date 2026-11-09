package com.dietpizza.rxplayer.core.player.preview

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpriteIntervalTest {
    @Test
    fun intervalAdaptsToDuration() {
        assertEquals(1_000L, SpriteInterval.intervalMs(30_000))           // 30 s clip
        assertEquals(1_000L, SpriteInterval.intervalMs(120_000))          // 2 min
        assertEquals(5_000L, SpriteInterval.intervalMs(8 * 60_000L))      // 8 min
        assertEquals(15_000L, SpriteInterval.intervalMs(25 * 60_000L))    // episode
        assertEquals(60_000L, SpriteInterval.intervalMs(2 * 3_600_000L))  // film
        assertEquals(120_000L, SpriteInterval.intervalMs(3 * 3_600_000L))
        assertEquals(1_000L, SpriteInterval.intervalMs(0))
    }

    @Test
    fun frameCountNeverExceedsMax() {
        for (minutes in longArrayOf(1, 10, 45, 90, 180, 600, 6_000)) {
            val d = minutes * 60_000
            val n = SpriteInterval.frameCount(d, SpriteInterval.intervalMs(d))
            assertTrue("$minutes min -> $n", n in 1..SpriteInterval.MAX_FRAMES)
        }
        assertEquals(0, SpriteInterval.frameCount(0, 1_000))
        assertEquals(10, SpriteInterval.frameCount(10_000, 1_000)) // 0..9 s, not the end frame
        assertEquals(11, SpriteInterval.frameCount(10_001, 1_000))
    }

    @Test
    fun indexRoundsToNearestAndClamps() {
        assertEquals(0, SpriteInterval.indexFor(0, 10_000, 5))
        assertEquals(0, SpriteInterval.indexFor(4_999, 10_000, 5))
        assertEquals(1, SpriteInterval.indexFor(5_000, 10_000, 5))
        assertEquals(4, SpriteInterval.indexFor(999_999, 10_000, 5))
        assertEquals(0, SpriteInterval.indexFor(-50, 10_000, 5))
        assertEquals(-1, SpriteInterval.indexFor(0, 10_000, 0))
    }

    @Test
    fun generationIsCoarseToFineAndComplete() {
        val order = SpriteInterval.generationOrder(40)
        assertArrayEquals(intArrayOf(0, 16, 32, 8, 24), order.copyOfRange(0, 5))
        assertEquals((0 until 40).toList(), order.sorted())
        assertEquals(0, SpriteInterval.generationOrder(0).size)
    }
}
