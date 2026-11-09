package com.dietpizza.rxplayer.feature.gallery

import com.dietpizza.rxplayer.core.model.GalleryItem
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.MediaSort
import com.dietpizza.rxplayer.core.model.MediaSortField
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumGridTest {
    private val today = LocalDate.of(2026, 11, 9) // a Monday
    private val dates = DateHeaderFormatter(
        today = today,
        zone = ZoneOffset.UTC,
        todayLabel = "Today",
        yesterdayLabel = "Yesterday",
        weekdayFormat = DateTimeFormatter.ofPattern("EEEE", Locale.UK),
        sameYearFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.UK),
        otherYearFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK),
    )

    private fun ms(date: LocalDate, hour: Int = 12) = date.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private var nextId = 1L
    private fun item(name: String, taken: Long, size: Long = 1, video: Boolean = false, mime: String = "image/jpeg") =
        GalleryItem(
            key = MediaKey.forFile("/a/$name"),
            id = nextId++,
            contentUri = "content://media/external/images/media/$nextId",
            path = "/a/$name",
            folderPath = "/a",
            displayName = name,
            mimeType = if (video) "video/mp4" else mime,
            isVideo = video,
            dateTakenMs = taken,
            dateModifiedMs = taken,
            sizeBytes = size,
            width = 1,
            height = 1,
            durationMs = if (video) 62_000 else 0,
            orientation = 0,
        )

    @Test
    fun headerLabels() {
        assertEquals("Today", dates.label(today.toEpochDay()))
        assertEquals("Yesterday", dates.label(today.minusDays(1).toEpochDay()))
        assertEquals("Saturday", dates.label(today.minusDays(2).toEpochDay()))
        assertEquals("Tuesday", dates.label(today.minusDays(6).toEpochDay()))
        assertEquals("Mon, 2 Nov", dates.label(today.minusDays(7).toEpochDay()))
        assertEquals("Tue, 10 Nov", dates.label(today.plusDays(1).toEpochDay())) // future: plain date
        assertEquals("3 Nov 2024", dates.label(LocalDate.of(2024, 11, 3).toEpochDay()))
        assertEquals(today.toEpochDay(), dates.epochDay(ms(today, hour = 23)))
    }

    @Test
    fun dateOrderGetsDayHeadersAndSections() {
        val items = listOf(
            item("a.jpg", ms(today, 18)),
            item("b.mp4", ms(today, 9), video = true),
            item("c.gif", ms(today.minusDays(1)), mime = "image/gif"),
        )
        val grid = buildAlbumGrid(items, MediaSort.DEFAULT, dates)

        assertEquals(5, grid.cells.size)
        assertEquals(listOf(0, 3), grid.headerIndices.toList())
        assertEquals("Today", (grid.cells[0] as HeaderCell).label)
        assertEquals("h:${today.toEpochDay()}", grid.cells[0].key)
        val video = grid.cells[2] as MediaCell
        assertEquals(1, video.index)
        assertEquals("1:02", video.duration)
        assertTrue(video.isVideo)
        val gif = grid.cells[4] as MediaCell
        assertTrue(gif.isGif)
        assertNull(gif.duration)
        assertEquals(items.map { it.key }, grid.keys)
        assertEquals(listOf("Today", "Today", "Today", "Yesterday", "Yesterday"), grid.cells.indices.map(grid::sectionLabelAt))
        assertEquals("Yesterday", grid.sectionLabelAt(99)) // clamped
    }

    @Test
    fun nameAndSizeOrdersHaveNoHeaders() {
        val items = listOf(item("apple.jpg", 1, size = 5_000), item("Avocado.jpg", 2, size = 5_100), item("1.jpg", 3, size = 2_000_000))
        val byName = buildAlbumGrid(items, MediaSort(MediaSortField.NAME, descending = false), dates)
        assertEquals(0, byName.headerIndices.size)
        assertEquals(3, byName.cells.size)
        assertEquals(listOf("A", "A", "#"), byName.cells.indices.map(byName::sectionLabelAt))
        assertEquals(listOf("A", "#"), byName.sectionLabels)

        val bySize = buildAlbumGrid(items, MediaSort(MediaSortField.SIZE), dates, sizeLabel = { if (it > 1_000_000) "big" else "small" })
        assertEquals(listOf("small", "big"), bySize.sectionLabels)
    }

    @Test
    fun emptyGrid() {
        assertSame(AlbumGrid.EMPTY, buildAlbumGrid(emptyList(), MediaSort.DEFAULT, dates))
        assertEquals("", AlbumGrid.EMPTY.sectionLabelAt(0))
    }

    @Test
    fun shareMime() {
        assertEquals("image/jpeg", shareMimeType(listOf("image/jpeg", "image/jpeg")))
        assertEquals("image/*", shareMimeType(listOf("image/jpeg", "image/png")))
        assertEquals("*/*", shareMimeType(listOf("image/jpeg", "video/mp4")))
        assertEquals("*/*", shareMimeType(listOf("", "")))
        assertEquals("*/*", shareMimeType(emptyList()))
    }

    @Test
    fun formatting() {
        assertEquals("0:05", GalleryFormat.duration(5_000))
        assertEquals("1:02:03", GalleryFormat.duration(3_723_000))
        assertNull(GalleryFormat.duration(0))
        assertEquals("4.2 MB", GalleryFormat.bytes(4_200_000, Locale.US))
        assertEquals("12,345", GalleryFormat.count(12_345, Locale.US))
        assertEquals("#", GalleryFormat.initial(""))
        assertEquals("Z", GalleryFormat.initial("zebra"))
    }

    @Test
    fun dragSelectionRanges() {
        val items = listOf(
            item("1", ms(today, 12)), item("2", ms(today, 11)),
            item("3", ms(today.minusDays(1), 12)), item("4", ms(today.minusDays(1), 11)),
        )
        // cells: [H, 1, 2, H, 3, 4]
        val cells = buildAlbumGrid(items, MediaSort.DEFAULT, dates).cells
        val sel = AlbumSelection()

        assertFalse(sel.beginDrag(0, cells)) // header
        assertTrue(sel.beginDrag(1, cells))
        assertEquals(setOf(cells[1].key), sel.selected)
        sel.dragTo(5, cells)
        assertEquals(setOf(cells[1].key, cells[2].key, cells[4].key, cells[5].key), sel.selected)
        sel.dragTo(2, cells) // moving back restores un-crossed tiles
        assertEquals(setOf(cells[1].key, cells[2].key), sel.selected)
        sel.endDrag()

        // Starting on a selected tile deselects the range.
        assertTrue(sel.beginDrag(2, cells))
        sel.dragTo(1, cells)
        assertTrue(sel.selected.isEmpty())
        sel.endDrag()

        sel.toggle(cells[4].key)
        sel.retainAll(setOf(cells[5].key))
        assertTrue(sel.selected.isEmpty())
    }
}
