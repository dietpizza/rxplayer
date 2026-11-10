package com.dietpizza.rxplayer.core.model

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlideshowTest {

    private data class Item(val name: String, val mime: String, val video: Boolean = false) {
        val type get() = SlideshowMediaType.of(mime, video)
    }

    private val photo = Item("p", "image/jpeg")
    private val gif = Item("g", "IMAGE/GIF")
    private val video = Item("v", "video/mp4", video = true)
    private val unknownVideo = Item("u", "", video = true)
    private val items = listOf(photo, video, gif, unknownVideo)

    @Test
    fun classifiesByMimeAndVideoFlag() {
        assertEquals(SlideshowMediaType.PHOTO, photo.type)
        assertEquals(SlideshowMediaType.GIF, gif.type)
        assertEquals(SlideshowMediaType.VIDEO, video.type)
        assertEquals(SlideshowMediaType.VIDEO, unknownVideo.type)
        assertEquals(SlideshowMediaType.PHOTO, SlideshowMediaType.of(null, isVideo = false))
    }

    @Test
    fun filterKeepsOrderAndIncludedTypes() {
        val noVideos = SlideshowOptions(includeVideos = false)
        assertEquals(listOf(photo, gif), items.filterForSlideshow(noVideos) { it.type })
        val gifsOnly = SlideshowOptions(includePhotos = false, includeVideos = false)
        assertEquals(listOf(gif), items.filterForSlideshow(gifsOnly) { it.type })
        assertEquals(items, items.filterForSlideshow(SlideshowOptions.DEFAULT) { it.type })
    }

    @Test
    fun countsMatchFilter() {
        val counts = SlideshowCounts.of(items) { it.type }
        assertEquals(SlideshowCounts(photos = 1, videos = 2, gifs = 1), counts)
        assertEquals(2, counts.matching(SlideshowOptions(includeVideos = false)))
        assertEquals(0, SlideshowCounts(photos = 3).matching(SlideshowOptions(includePhotos = false)))
    }

    @Test
    fun lastIncludedTypeCannotBeTurnedOff() {
        val one = SlideshowOptions(includePhotos = true, includeVideos = false, includeGifs = false)
        assertEquals(one, one.withType(SlideshowMediaType.PHOTO, included = false))
        assertFalse(SlideshowOptions().withType(SlideshowMediaType.GIF, false).includeGifs)
    }

    @Test
    fun sanitizedFixesBadValues() {
        val bad = SlideshowOptions(includePhotos = false, includeVideos = false, includeGifs = false, secondsPerPhoto = 7)
        val fixed = bad.sanitized()
        assertTrue(fixed.includesAnything)
        assertEquals(SlideshowOptions.DEFAULT_SECONDS, fixed.secondsPerPhoto)
        assertEquals(SlideshowOptions(secondsPerPhoto = 10), SlideshowOptions(secondsPerPhoto = 10).sanitized())
    }

    @Test
    fun orderWithoutShuffleKeepsListAndClampsStart() {
        val list = listOf(1, 2, 3)
        assertEquals(list to 2, slideshowOrder(list, 2, shuffle = false))
        assertEquals(list to 0, slideshowOrder(list, -1, shuffle = false))
        assertEquals(list to 2, slideshowOrder(list, 9, shuffle = false))
        assertEquals(emptyList<Int>() to 0, slideshowOrder(emptyList<Int>(), 0, shuffle = true))
    }

    @Test
    fun shuffleKeepsStartItemFirstAndEveryItem() {
        val list = (0 until 50).toList()
        repeat(20) { seed ->
            val (order, start) = slideshowOrder(list, 17, shuffle = true, random = Random(seed))
            assertEquals(0, start)
            assertEquals(17, order.first())
            assertEquals(list.toSet(), order.toSet())
            assertEquals(list.size, order.size)
        }
        val (any, _) = slideshowOrder(list, -1, shuffle = true, random = Random(1))
        assertEquals(list.toSet(), any.toSet())
    }
}
