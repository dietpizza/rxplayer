package com.dietpizza.rxplayer.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalOrderComparatorTest {
    private fun sorted(vararg s: String) = s.sortedWith(NaturalOrderComparator)

    @Test
    fun numbersCompareNumerically() {
        assertEquals(listOf("Ep 1", "Ep 2", "Ep 10", "Ep 11"), sorted("Ep 10", "Ep 2", "Ep 11", "Ep 1"))
        assertTrue(NaturalOrderComparator.compare("Ep 2", "Ep 10") < 0)
    }

    @Test
    fun caseInsensitive() {
        assertEquals(listOf("alpha", "Beta", "gamma"), sorted("gamma", "Beta", "alpha"))
    }

    @Test
    fun multipleNumberRuns() {
        assertEquals(
            listOf("S1E2", "S1E10", "S2E1", "S10E1"),
            sorted("S10E1", "S1E10", "S2E1", "S1E2"),
        )
    }

    @Test
    fun leadingZerosAndHugeNumbers() {
        assertEquals(listOf("1", "01", "002", "3"), sorted("3", "002", "01", "1"))
        val big = "x 123456789012345678901234567890"
        assertTrue(NaturalOrderComparator.compare("x 99", big) < 0)
    }

    @Test
    fun prefixShorterFirstAndTotalOrder() {
        assertEquals(listOf("Ep", "Ep 1", "Ep 1 extra"), sorted("Ep 1 extra", "Ep", "Ep 1"))
        assertTrue(NaturalOrderComparator.compare("a", "A") != 0)
        assertEquals(0, NaturalOrderComparator.compare("same", "same"))
    }
}
