package com.dietpizza.rxplayer.feature.player.viewer

import com.dietpizza.rxplayer.core.model.MediaKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageKindsTest {
    @Test
    fun mediaStoreFlagWins() {
        assertEquals(PageKind.Video, PageKinds.classify("file:///a/b.jpg", "image/jpeg", isVideo = true))
        assertEquals(PageKind.Photo, PageKinds.classify("file:///a/b.mp4", "video/mp4", isVideo = false))
    }

    @Test
    fun mimeThenExtension() {
        assertEquals(PageKind.Video, PageKinds.classify("content://x/1", "video/mp4"))
        assertEquals(PageKind.Photo, PageKinds.classify("content://x/1", "image/heic"))
        assertEquals(PageKind.Video, PageKinds.classify("smb://3/Movies/My film #2.MKV", null))
        assertEquals(PageKind.Photo, PageKinds.classify("smb://3/Pics/IMG_1.jpg", null))
        // Unknown: photo (the decoder copes or says so).
        assertEquals(PageKind.Photo, PageKinds.classify("smb://3/Pics/noext", null))
    }

    @Test
    fun origins() {
        assertEquals(ItemOrigin.Local, PageKinds.origin("file:///sdcard/DCIM/a.jpg", external = false))
        assertEquals(ItemOrigin.Network, PageKinds.origin("sftp://4/home/a.jpg", external = false))
        assertEquals(ItemOrigin.External, PageKinds.origin("content://com.app/x", external = false))
        assertEquals(ItemOrigin.External, PageKinds.origin("file:///sdcard/a.jpg", external = true))
    }

    @Test
    fun capabilitiesByOrigin() {
        val local = PageKinds.item(MediaKey("file:///sdcard/a.jpg"), null, external = false)
        assertTrue(local.canFavourite)
        assertTrue(local.canDelete)
        assertNull(local.shareUri) // no MediaStore row yet

        val net = PageKinds.item(MediaKey("smb://3/Pics/a b#1.png"), null, external = false)
        assertFalse(net.canFavourite)
        assertFalse(net.canDelete)
        assertNull(net.shareUri)
        assertEquals("a b#1.png", net.title)
        assertEquals("image/png", net.mimeType)

        val ext = PageKinds.item(MediaKey("content://media/external/images/media/12"), null, external = true, mimeHint = "image/webp")
        assertFalse(ext.canFavourite)
        assertFalse(ext.canDelete)
        assertEquals("content://media/external/images/media/12", ext.shareUri)
        assertEquals("image/webp", ext.mimeType)
    }

    @Test
    fun namesAndExtensions() {
        assertEquals("IMG 1.HEIC", PageKinds.fileName("smb://3/Pics/IMG 1.HEIC"))
        assertEquals("heic", PageKinds.extension("smb://3/Pics/IMG 1.HEIC"))
        assertEquals("", PageKinds.extension("smb://3/Pics/README"))
        assertEquals("my photo.jpg", PageKinds.item(MediaKey("content://com.app/files/my%20photo.jpg"), null, true).title)
    }

    @Test
    fun animatableTypes() {
        assertTrue(PageKinds.mayBeAnimated("image/gif"))
        assertTrue(PageKinds.mayBeAnimated("image/WEBP"))
        assertTrue(PageKinds.mayBeAnimated("image/avif"))
        assertTrue(PageKinds.mayBeAnimated(null))
        assertFalse(PageKinds.mayBeAnimated("image/jpeg"))
    }
}
