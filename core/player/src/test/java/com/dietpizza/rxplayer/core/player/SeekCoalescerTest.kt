package com.dietpizza.rxplayer.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekCoalescerTest {
    private val seeks = mutableListOf<Pair<Long, Boolean>>()
    private val c = SeekCoalescer({ pos, exact -> seeks += pos to exact }, timeoutMs = 500)

    @Test
    fun firstRequestSeeksImmediatelyAsFastSeek() {
        c.request(1_000, nowMs = 0)
        assertEquals(listOf(1_000L to false), seeks)
        assertTrue(c.inFlight)
    }

    @Test
    fun requestsWhileInFlightCoalesceToLatest() {
        c.request(1_000, 0)
        c.request(2_000, 10)
        c.request(3_000, 20)
        c.request(4_000, 30)
        assertEquals(1, seeks.size)
        c.onSeekProcessed(40)
        assertEquals(listOf(1_000L to false, 4_000L to false), seeks)
        assertTrue(c.inFlight)
        c.onSeekProcessed(50)
        assertFalse(c.inFlight)
        assertEquals(2, seeks.size)
    }

    @Test
    fun neverMoreThanOneInFlight() {
        var inFlight = 0
        var maxInFlight = 0
        val counting = SeekCoalescer({ _, _ -> inFlight++; maxInFlight = maxOf(maxInFlight, inFlight) })
        for (i in 0 until 100) {
            counting.request(i * 100L, i.toLong())
            if (i % 7 == 0 && counting.inFlight) { inFlight--; counting.onSeekProcessed(i.toLong()) }
        }
        assertEquals(1, maxInFlight)
    }

    @Test
    fun duplicateTargetIsSkipped() {
        c.request(1_000, 0)
        c.onSeekProcessed(5)
        c.request(1_000, 10)
        assertEquals(1, seeks.size)
        c.request(2_000, 20)
        c.request(1_000, 30) // back to the in-flight target: nothing pending
        c.onSeekProcessed(40)
        assertEquals(listOf(1_000L to false, 2_000L to false, 1_000L to false), seeks)
    }

    @Test
    fun commitDropsPendingAndSeeksExactly() {
        c.request(1_000, 0)
        c.request(2_000, 10)
        c.commit(2_500)
        assertEquals(listOf(1_000L to false, 2_500L to true), seeks)
        assertFalse(c.inFlight)
        assertFalse(c.hasPending)
        c.onSeekProcessed(20) // late completion of the old fast seek: no extra seek
        assertEquals(2, seeks.size)
    }

    @Test
    fun timeoutReleasesAStuckSeek() {
        c.request(1_000, 0)
        c.request(2_000, 100)
        c.onTick(499)
        assertEquals(1, seeks.size)
        c.onTick(500)
        assertEquals(listOf(1_000L to false, 2_000L to false), seeks)
    }

    @Test
    fun resetForgetsEverything() {
        c.request(1_000, 0)
        c.request(2_000, 10)
        c.reset()
        c.onSeekProcessed(20)
        assertEquals(1, seeks.size)
        c.request(1_000, 30) // same target as before reset still seeks
        assertEquals(2, seeks.size)
    }
}
