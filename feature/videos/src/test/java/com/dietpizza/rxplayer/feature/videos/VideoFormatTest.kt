package com.dietpizza.rxplayer.feature.videos

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class VideoFormatTest {

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun bytes() {
        assertEquals("512 B", VideoFormat.bytes(512))
        assertEquals("1.5 KB", VideoFormat.bytes(1_500))
        assertEquals("820 MB", VideoFormat.bytes(820_400_000))
        assertEquals("4.2 GB", VideoFormat.bytes(4_200_000_000))
        assertEquals("12 GB", VideoFormat.bytes(12_900_000_000))
    }

    @Test
    fun duration() {
        assertNull(VideoFormat.duration(0))
        assertEquals("0:42", VideoFormat.duration(42_500))
        assertEquals("12:31", VideoFormat.duration(751_000))
        assertEquals("1:02:03", VideoFormat.duration(3_723_000))
    }

    @Test
    fun resolution() {
        assertNull(VideoFormat.resolution(0, 0))
        assertEquals("480p", VideoFormat.resolution(854, 480))
        assertEquals("720p", VideoFormat.resolution(720, 1280))
        assertEquals("1080p", VideoFormat.resolution(1920, 800))
        assertEquals("4K", VideoFormat.resolution(3840, 2160))
        assertEquals("360p", VideoFormat.resolution(640, 360))
    }

    @Test
    fun videoCount() {
        assertEquals("1 video", VideoFormat.videoCount(1))
        assertEquals("12 videos", VideoFormat.videoCount(12))
    }
}
