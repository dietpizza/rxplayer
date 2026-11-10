package com.dietpizza.rxplayer.feature.videos

import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.VideoItem
import com.dietpizza.rxplayer.core.model.VideoSort
import com.dietpizza.rxplayer.core.model.VideoSortField
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderSortTest {
    private fun video(title: String, modified: Long = 0, size: Long = 0, duration: Long = 0) = VideoItem(
        key = MediaKey.forFile("/f/$title.mp4"), contentUri = "", title = title, path = "/f/$title.mp4",
        folderPath = "/f", durationMs = duration, sizeBytes = size, width = 0, height = 0,
        dateModifiedMs = modified, playback = null,
    )

    private val videos = listOf(
        video("Ep 10", modified = 3, size = 10, duration = 5),
        video("Ep 2", modified = 1, size = 30, duration = 5),
        video("ep 1", modified = 2, size = 20, duration = 9),
    )

    private fun order(sort: VideoSort) = videos.sortedWith(comparatorFor(sort)).map { it.title }

    @Test
    fun sortsByEachField() {
        assertEquals(listOf("ep 1", "Ep 2", "Ep 10"), order(VideoSort(VideoSortField.NAME)))
        assertEquals(listOf("Ep 10", "Ep 2", "ep 1"), order(VideoSort(VideoSortField.NAME, descending = true)))
        assertEquals(listOf("Ep 10", "ep 1", "Ep 2"), order(VideoSort.defaultFor(VideoSortField.DATE_MODIFIED)))
        assertEquals(listOf("Ep 2", "ep 1", "Ep 10"), order(VideoSort.defaultFor(VideoSortField.SIZE)))
        // Equal durations fall back to A→Z whatever the direction.
        assertEquals(listOf("ep 1", "Ep 2", "Ep 10"), order(VideoSort.defaultFor(VideoSortField.DURATION)))
    }

}
