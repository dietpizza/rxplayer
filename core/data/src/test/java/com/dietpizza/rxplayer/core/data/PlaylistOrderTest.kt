package com.dietpizza.rxplayer.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PlaylistOrderTest {
    private val abc = listOf("a", "b", "c")

    @Test
    fun appendSkipsDuplicates() {
        assertEquals(listOf("a", "b", "c", "d"), PlaylistOrder.append(abc, listOf("b", "d", "d", "a")))
        assertEquals(abc, PlaylistOrder.append(abc, emptyList()))
    }

    @Test
    fun remove() {
        assertEquals(listOf("a", "c"), PlaylistOrder.remove(abc, listOf("b", "zzz")))
        assertSame(abc, PlaylistOrder.remove(abc, emptyList()))
    }

    @Test
    fun moveDownUpAndOutOfRange() {
        assertEquals(listOf("b", "c", "a"), PlaylistOrder.move(abc, 0, 2))
        assertEquals(listOf("c", "a", "b"), PlaylistOrder.move(abc, 2, 0))
        assertEquals(listOf("a", "c", "b"), PlaylistOrder.move(abc, 1, 2))
        assertSame(abc, PlaylistOrder.move(abc, 1, 1))
        assertSame(abc, PlaylistOrder.move(abc, -1, 2))
        assertSame(abc, PlaylistOrder.move(abc, 0, 3))
    }

    @Test
    fun applyOrderIsAPermutationOfCurrent() {
        assertEquals(listOf("c", "a", "b"), PlaylistOrder.applyOrder(abc, listOf("c", "a", "b")))
        // Removed meanwhile: ignored. Added meanwhile: kept at the end.
        assertEquals(listOf("c", "a", "d"), PlaylistOrder.applyOrder(listOf("a", "c", "d"), listOf("c", "b", "a")))
        // Duplicates in the desired order don't duplicate items.
        assertEquals(listOf("b", "a", "c"), PlaylistOrder.applyOrder(abc, listOf("b", "b", "a")))
    }
}
