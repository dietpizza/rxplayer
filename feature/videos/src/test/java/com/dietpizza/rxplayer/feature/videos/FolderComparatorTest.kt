package com.dietpizza.rxplayer.feature.videos

import com.dietpizza.rxplayer.core.model.AlbumSort
import com.dietpizza.rxplayer.core.model.AlbumSortField
import com.dietpizza.rxplayer.core.model.VideoFolder
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderComparatorTest {
    private fun folder(name: String, count: Int, size: Long, modified: Long) =
        VideoFolder("/s/$name", name, count, 0, size, null, modified)

    private val folders = listOf(
        folder("b", count = 5, size = 100, modified = 3),
        folder("a 10", count = 1, size = 900, modified = 1),
        folder("a 2", count = 9, size = 10, modified = 2),
    )

    private fun order(sort: AlbumSort) = folders.sortedWith(folderComparator(sort)).map { it.name }

    @Test
    fun sortsByEachField() {
        assertEquals(listOf("a 2", "a 10", "b"), order(AlbumSort(AlbumSortField.NAME, descending = false)))
        assertEquals(listOf("a 2", "b", "a 10"), order(AlbumSort.defaultFor(AlbumSortField.COUNT)))
        assertEquals(listOf("a 10", "b", "a 2"), order(AlbumSort.defaultFor(AlbumSortField.SIZE)))
        assertEquals(listOf("a 2", "b", "a 10"), order(AlbumSort(AlbumSortField.SIZE, descending = false)))
        assertEquals(listOf("b", "a 2", "a 10"), order(AlbumSort.defaultFor(AlbumSortField.DATE_MODIFIED)))
    }
}
