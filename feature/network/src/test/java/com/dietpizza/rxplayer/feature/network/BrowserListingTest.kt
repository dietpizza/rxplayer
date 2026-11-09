package com.dietpizza.rxplayer.feature.network

import com.dietpizza.rxplayer.core.netfs.model.NetProtocol
import com.dietpizza.rxplayer.core.netfs.model.RemoteEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserListingTest {

    private fun file(name: String, hidden: Boolean = name.startsWith('.')) =
        RemoteEntry(name, "/m/$name", false, 10, 0, hidden)

    private fun dir(name: String) = RemoteEntry(name, "/m/$name", true, -1, 0)

    private val entries = listOf(
        file("Episode 10.mkv"),
        file("notes.txt"),
        dir("b-folder"),
        file("Episode 2.mkv"),
        file("cover.JPG"),
        file(".hidden.mkv"),
        dir("A-folder"),
        file("Episode 1.srt"),
    )

    @Test fun `media only by default, folders first, natural order`() {
        assertEquals(
            listOf("A-folder", "b-folder", "cover.JPG", "Episode 2.mkv", "Episode 10.mkv"),
            BrowserListing.visible(entries, showAll = false).map { it.name },
        )
    }

    @Test fun `show all includes other and hidden files`() {
        assertEquals(
            listOf("A-folder", "b-folder", ".hidden.mkv", "cover.JPG", "Episode 1.srt", "Episode 2.mkv", "Episode 10.mkv", "notes.txt"),
            BrowserListing.visible(entries, showAll = true).map { it.name },
        )
    }

    @Test fun hidesFiles() {
        assertTrue(BrowserListing.hidesFiles(entries))
        assertFalse(BrowserListing.hidesFiles(listOf(dir("x"), file("a.mkv"), file(".DS_Store"))))
    }

    @Test fun `tapping a video plays the folder's videos in order`() {
        val rows = BrowserListing.visible(entries, showAll = false)
        val playlist = BrowserListing.playlistFor(rows, rows.first { it.name == "Episode 10.mkv" }, NetProtocol.SMB, 3)!!
        assertEquals(listOf("smb://3/m/Episode 2.mkv", "smb://3/m/Episode 10.mkv"), playlist.keys.map { it.value })
        assertEquals(1, playlist.startIndex)
        assertFalse(playlist.viewer)
    }

    @Test fun `tapping an image opens the viewer over photos and videos`() {
        val rows = BrowserListing.visible(entries, showAll = false)
        val playlist = BrowserListing.playlistFor(rows, rows.first { it.name == "cover.JPG" }, NetProtocol.WEBDAV, 9)!!
        assertEquals(listOf("webdav://9/m/cover.JPG", "webdav://9/m/Episode 2.mkv", "webdav://9/m/Episode 10.mkv"), playlist.keys.map { it.value })
        assertEquals(0, playlist.startIndex)
        assertTrue(playlist.viewer)
    }

    @Test fun `other files don't play`() {
        val rows = BrowserListing.visible(entries, showAll = true)
        assertNull(BrowserListing.playlistFor(rows, rows.first { it.name == "notes.txt" }, NetProtocol.FTP, 1))
    }

    @Test fun `rename selects the base name`() {
        assertEquals(7, BrowserListing.baseNameEnd("Episode.mkv", false))
        assertEquals(7, BrowserListing.baseNameEnd(".bashrc", false))
        assertEquals(10, BrowserListing.baseNameEnd("Season 1.x", true))
    }

    @Test fun naturalOrder() {
        val sorted = listOf("ep10", "Ep2", "ep1", "ep02", "a", "ep", "B").sortedWith(NaturalOrder)
        assertEquals(listOf("a", "B", "ep", "ep1", "Ep2", "ep02", "ep10"), sorted)
    }
}
