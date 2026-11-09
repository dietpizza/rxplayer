package com.dietpizza.rxplayer.core.player

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShufflePickTest {
    @Test
    fun neverCurrentNorPlayed() {
        repeat(200) { seed ->
            val pick = pickShuffle(size = 10, current = 3, played = setOf(0, 1, 3, 7), random = Random(seed))!!
            assertTrue(pick.index !in setOf(0, 1, 3, 7))
            assertFalse(pick.resetRound)
        }
    }

    @Test
    fun startsNewRoundWhenAllPlayed() {
        val pick = pickShuffle(size = 3, current = 1, played = setOf(0, 1, 2), random = Random(1))!!
        assertTrue(pick.index != 1)
        assertTrue(pick.resetRound)
    }

    @Test
    fun onlyOneUnplayedLeft() {
        assertEquals(4, pickShuffle(5, current = 0, played = setOf(0, 1, 2, 3), random = Random(9))!!.index)
    }

    @Test
    fun singleItemHasNothingToShuffle() {
        assertNull(pickShuffle(1, current = 0, played = emptySet(), random = Random(0)))
    }
}
