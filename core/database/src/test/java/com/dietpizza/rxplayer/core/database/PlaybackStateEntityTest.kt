package com.dietpizza.rxplayer.core.database

import com.dietpizza.rxplayer.core.model.MediaKey
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackStateEntityTest {
    @Test
    fun mapsToModel() {
        val e = PlaybackStateEntity("file:///a/b.mp4", "b", "LOCAL", 10, 100, false, 2, 5, 1, 2)
        val m = e.toModel()
        assertEquals(MediaKey("file:///a/b.mp4"), m.key)
        assertEquals(10L, m.positionMs)
        assertEquals(100L, m.durationMs)
        assertEquals(2, m.playCount)
        assertEquals(5L, m.lastPlayedAt)
    }
}
