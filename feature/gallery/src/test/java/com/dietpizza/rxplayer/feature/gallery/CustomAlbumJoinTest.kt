package com.dietpizza.rxplayer.feature.gallery

import com.dietpizza.rxplayer.core.model.GalleryItem
import com.dietpizza.rxplayer.core.model.MediaKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomAlbumJoinTest {
    private fun item(path: String, taken: Long, id: Long = taken) = GalleryItem(
        key = MediaKey.forFile(path),
        id = id,
        contentUri = "content://media/external/images/media/$id",
        path = path,
        folderPath = path.substringBeforeLast('/'),
        displayName = path.substringAfterLast('/'),
        mimeType = "image/jpeg",
        isVideo = false,
        dateTakenMs = taken,
        dateModifiedMs = taken,
        sizeBytes = 1,
        width = 1,
        height = 1,
        durationMs = 0,
        orientation = 0,
    )

    private val a = item("/s/A/a.jpg", 10)
    private val b = item("/s/B/b.jpg", 30)
    private val c = item("/s/A/c.jpg", 20)
    private val library = listOf(a, b, c).associateBy { it.key.value }

    @Test
    fun joinKeepsAlbumOrderAndSkipsMissingFiles() {
        val keys = listOf(
            c.key,
            MediaKey.forFile("/s/gone.jpg"),
            a.key,
            MediaKey("smb://server/share/x.mp4"),
            MediaKey("content://media/external/images/media/9"),
        )
        assertEquals(listOf(c, a), joinAlbumItems(keys, library))
    }

    @Test
    fun joinDropsDuplicatesAndHandlesEmptyInputs() {
        assertEquals(listOf(b), joinAlbumItems(listOf(b.key, b.key), library))
        assertTrue(joinAlbumItems(emptyList(), library).isEmpty())
        assertTrue(joinAlbumItems(listOf(a.key), emptyMap()).isEmpty())
    }

    @Test
    fun summaryCountsVisibleItemsAndPicksNewestCover() {
        val summary = summarizeKeyed(setOf(a.key, c.key, MediaKey.forFile("/s/gone.jpg")), library)
        assertEquals(2, summary.count)
        assertEquals(c, summary.cover)

        val empty = summarizeKeyed(setOf(MediaKey.forFile("/s/gone.jpg")), library)
        assertEquals(0, empty.count)
        assertNull(empty.cover)
    }

    @Test
    fun folderChoicesExcludeCurrentFolderAndSortByName() {
        val choices = folderChoices(
            listOf(
                FolderChoice("/s/b", "beta"),
                FolderChoice("/s/A", "Alpha"),
                FolderChoice("/s/c", "gamma"),
                FolderChoice("/s/A", "Alpha"),
                FolderChoice("", "virtual"),
            ),
            exclude = "/s/c/",
        )
        assertEquals(listOf("/s/A", "/s/b"), choices.map { it.path })
    }
}
