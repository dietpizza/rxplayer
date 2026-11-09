package com.dietpizza.rxplayer.feature.search

import com.dietpizza.rxplayer.core.model.GalleryItem
import com.dietpizza.rxplayer.core.model.MediaKey
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {
    private val zone = ZoneOffset.UTC
    private val now = LocalDate.of(2026, 11, 9).atTime(12, 0).toInstant(zone).toEpochMilli()
    private fun daysAgo(d: Long) = now - d * 86_400_000L

    private var nextId = 1L
    private fun item(
        path: String,
        taken: Long = now,
        video: Boolean = false,
        size: Long = 1_000,
        duration: Long = 0,
    ): GalleryItem {
        val id = nextId++
        return GalleryItem(
            key = MediaKey.forFile(path),
            id = id,
            contentUri = "content://x/$id",
            path = path,
            folderPath = path.substringBeforeLast('/'),
            displayName = path.substringAfterLast('/'),
            mimeType = if (video) "video/mp4" else "image/jpeg",
            isVideo = video,
            dateTakenMs = taken,
            dateModifiedMs = taken,
            sizeBytes = size,
            width = 1,
            height = 1,
            durationMs = duration,
            orientation = 0,
        )
    }

    private fun run(items: List<GalleryItem>, query: String, filters: SearchFilters = SearchFilters()) =
        search(SearchIndex(items), query, filters, now, zone).map { it.displayName }

    @Test
    fun matchesFileAndFolderNamesCaseAndAccentInsensitive() {
        val items = listOf(
            item("/s/Pictures/Holiday/IMG_1.jpg"),
            item("/s/DCIM/Camera/holiday_beach.jpg"),
            item("/s/Download/Café menu.png"),
            item("/s/Download/other.png"),
        )
        assertEquals(listOf("holiday_beach.jpg", "IMG_1.jpg"), run(items, "HOLIDAY"))
        assertEquals(listOf("Café menu.png"), run(items, "cafe"))
        assertEquals(listOf("Café menu.png"), run(items, "CAFÉ"))
        assertTrue(run(items, "nothing").isEmpty())
    }

    @Test
    fun everyTokenMustMatchNameOrFolder() {
        val items = listOf(
            item("/s/Trip/beach.jpg"),
            item("/s/Trip/mountain.jpg"),
            item("/s/Home/beach.jpg"),
        )
        assertEquals(listOf("beach.jpg"), run(items, "trip beach"))
        assertEquals("/s/Trip/beach.jpg", search(SearchIndex(items), "trip beach", SearchFilters(), now, zone).single().path)
    }

    @Test
    fun ranksNamePrefixThenNameThenFolderKeepingLibraryOrder() {
        val items = listOf(
            item("/s/cat/zzz.jpg"), // folder match only
            item("/s/a/my cat.jpg"), // contains
            item("/s/a/cat 2.jpg"), // prefix
            item("/s/a/cat 1.jpg"), // prefix
        )
        assertEquals(listOf("cat 2.jpg", "cat 1.jpg", "my cat.jpg", "zzz.jpg"), run(items, "cat"))
    }

    @Test
    fun blankQueryWithoutFiltersIsEmptyButFiltersAloneList() {
        val items = listOf(item("/s/a/p.jpg"), item("/s/a/v.mp4", video = true))
        assertTrue(run(items, "   ").isEmpty())
        assertEquals(listOf("v.mp4"), run(items, "", SearchFilters(type = TypeFilter.VIDEOS)))
        assertEquals(listOf("p.jpg"), run(items, "", SearchFilters(type = TypeFilter.PHOTOS)))
    }

    @Test
    fun dateFilters() {
        val items = listOf(
            item("/s/a/today.jpg", taken = now),
            item("/s/a/week.jpg", taken = daysAgo(6)),
            item("/s/a/month.jpg", taken = daysAgo(20)),
            item("/s/a/year.jpg", taken = LocalDate.of(2026, 1, 2).atStartOfDay(zone).toInstant().toEpochMilli()),
            item("/s/a/old.jpg", taken = LocalDate.of(2025, 12, 31).atStartOfDay(zone).toInstant().toEpochMilli()),
        )
        assertEquals(listOf("today.jpg", "week.jpg"), run(items, "", SearchFilters(date = DateFilter.LAST_7_DAYS)))
        assertEquals(listOf("today.jpg", "week.jpg", "month.jpg"), run(items, "", SearchFilters(date = DateFilter.LAST_30_DAYS)))
        assertEquals(4, run(items, "", SearchFilters(date = DateFilter.THIS_YEAR)).size)

        val dec31 = LocalDate.of(2025, 12, 31).toEpochDay()
        val jan2 = LocalDate.of(2026, 1, 2).toEpochDay()
        assertEquals(
            listOf("year.jpg", "old.jpg"),
            run(items, "", SearchFilters(date = DateFilter.CUSTOM, customStartDay = dec31, customEndDay = jan2)),
        )
        // Single day: inclusive end.
        assertEquals(
            listOf("old.jpg"),
            run(items, "", SearchFilters(date = DateFilter.CUSTOM, customStartDay = dec31, customEndDay = dec31)),
        )
    }

    @Test
    fun sizeAndDurationFilters() {
        val items = listOf(
            item("/s/a/small.jpg", size = 2_000_000),
            item("/s/a/big.mp4", video = true, size = 2_000_000_000, duration = 90 * 60_000L),
            item("/s/a/short.mp4", video = true, size = 50_000_000, duration = 30_000),
            item("/s/a/mid.mp4", video = true, size = 500_000_000, duration = 5 * 60_000L),
        )
        assertEquals(listOf("small.jpg"), run(items, "", SearchFilters(size = SizeFilter.UNDER_10_MB)))
        assertEquals(listOf("short.mp4"), run(items, "", SearchFilters(size = SizeFilter.MB_10_TO_100)))
        assertEquals(listOf("big.mp4"), run(items, "", SearchFilters(size = SizeFilter.OVER_1_GB)))
        // Duration filters drop photos.
        assertEquals(listOf("short.mp4"), run(items, "", SearchFilters(duration = DurationFilter.UNDER_1_MIN)))
        assertEquals(listOf("mid.mp4"), run(items, "", SearchFilters(duration = DurationFilter.MIN_1_TO_10)))
        assertEquals(listOf("big.mp4"), run(items, "", SearchFilters(duration = DurationFilter.OVER_1_HOUR)))
        assertEquals(listOf("mid.mp4"), run(items, "mid", SearchFilters(type = TypeFilter.VIDEOS)))
    }

    @Test
    fun filterFlags() {
        assertTrue(!SearchFilters().isActive)
        assertTrue(SearchFilters(duration = DurationFilter.UNDER_1_MIN).videosOnly)
        assertEquals(listOf("a", "b"), tokenize("  A\tb "))
    }
}
