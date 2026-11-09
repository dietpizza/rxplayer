package com.dietpizza.rxplayer.core.data

import com.dietpizza.rxplayer.core.media.LocalVideo
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoLibraryTest {
    private var nextId = 1L
    private fun video(path: String, size: Long = 100, modified: Long = 0) = LocalVideo(
        id = nextId++,
        contentUri = "content://media/external/video/media/${nextId - 1}",
        path = path,
        folderPath = path.substringBeforeLast('/'),
        displayName = path.substringAfterLast('/'),
        durationMs = 1000,
        sizeBytes = size,
        width = 1920,
        height = 1080,
        dateModifiedMs = modified,
    )

    private fun state(path: String) =
        PlaybackState(MediaKey.forFile(path), "t", 10, 100, false, 1, 1)

    @Test
    fun foldersAggregateAndSortByName() {
        val videos = listOf(
            video("/s/zeta/a.mp4", size = 10, modified = 5),
            video("/s/Alpha/Ep 10.mkv", size = 20, modified = 7),
            video("/s/Alpha/Ep 2.mkv", size = 30, modified = 9),
            video("/s/beta/x.mp4"),
        )
        val states = mapOf(MediaKey.forFile("/s/Alpha/Ep 2.mkv").value to state("/s/Alpha/Ep 2.mkv"))
        val lib = buildVideoLibrary(videos, states)

        assertEquals(listOf("Alpha", "beta", "zeta"), lib.folders.map { it.name })
        val alpha = lib.folders.first()
        assertEquals("/s/Alpha", alpha.path)
        assertEquals(2, alpha.videoCount)
        assertEquals(1, alpha.newCount)
        assertEquals(50L, alpha.totalSizeBytes)
        assertEquals(9L, alpha.lastModifiedMs)
        assertEquals(videos[2].contentUri, alpha.coverContentUri)
    }

    @Test
    fun videosJoinedAndNaturallySorted() {
        val videos = listOf(video("/s/A/Ep 10.mkv"), video("/s/A/Ep 2.mkv"), video("/s/A/Ep 1.mkv"))
        val lib = buildVideoLibrary(videos, mapOf(MediaKey.forFile("/s/A/Ep 10.mkv").value to state("/s/A/Ep 10.mkv")))
        val items = lib.videosByFolder.getValue("/s/A")
        assertEquals(listOf("Ep 1", "Ep 2", "Ep 10"), items.map { it.title })
        assertTrue(items[0].isNew)
        assertEquals(10L, items[2].playback?.positionMs)
        assertEquals(MediaKey("file:///s/A/Ep 1.mkv"), items[0].key)
    }

    @Test
    fun emptyAndTitles() {
        assertTrue(buildVideoLibrary(emptyList(), emptyMap()).folders.isEmpty())
        assertEquals("movie", titleOf("movie.mp4"))
        assertEquals("a.b", titleOf("a.b.mkv"))
        assertEquals(".mkv", titleOf(".mkv"))
        assertNull(aggregateFolder("/x", emptyList()).coverContentUri)
    }
}
