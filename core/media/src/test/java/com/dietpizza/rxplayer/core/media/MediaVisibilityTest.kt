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

    @Test
    fun showHiddenMakesEverythingVisibleWithoutStatting() {
        var stats = 0
        val v = MediaVisibility(showHidden = true, hasNoMedia = { stats++; true })
        assertTrue(v.isVisible("/storage/emulated/0/.hidden/a.mp4"))
        assertTrue(v.isVisible("/sd/WhatsApp/Media/Video/a.mp4"))
        assertTrue(v.isVisible("/storage/emulated/0/Movies/.a.mp4"))
        assertTrue(v.isDirVisible("/sd/.thumbs"))
        assertFalse(v.isVisible("no-slash"))
        assertEquals(0, stats)
    }

    @Test
    fun defaultStillHidesNoMediaAndDotFolders() {
        val v = MediaVisibility(hasNoMedia = { it == "/sd/Secret" })
        assertFalse(v.isVisible("/sd/Secret/a.jpg"))
        assertFalse(v.isVisible("/sd/.trash/a.jpg"))
        assertTrue(v.isVisible("/sd/Public/a.jpg"))
    }
}
