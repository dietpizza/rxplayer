package com.dietpizza.rxplayer.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaVisibilityTest {
    @Test
    fun hiddenSegmentsAreHidden() {
        val v = MediaVisibility(hasNoMedia = { false })
        assertTrue(v.isVisible("/storage/emulated/0/Movies/a.mp4"))
        assertFalse(v.isVisible("/storage/emulated/0/.hidden/a.mp4"))
        assertFalse(v.isVisible("/storage/emulated/0/.hidden/sub/a.mp4"))
        assertFalse(v.isVisible("/storage/emulated/0/Movies/.a.mp4"))
    }

    @Test
    fun noMediaAppliesToSubfoldersAndIsCachedPerDirectory() {
        val checked = mutableListOf<String>()
        val v = MediaVisibility(hasNoMedia = { checked += it; it == "/sd/WhatsApp/Media" })
        assertFalse(v.isVisible("/sd/WhatsApp/Media/Video/a.mp4"))
        assertFalse(v.isVisible("/sd/WhatsApp/Media/Video/b.mp4"))
        assertTrue(v.isVisible("/sd/WhatsApp/c.mp4"))
        // Each directory stat'ed at most once.
        assertEquals(checked.size, checked.toSet().size)
    }
}
