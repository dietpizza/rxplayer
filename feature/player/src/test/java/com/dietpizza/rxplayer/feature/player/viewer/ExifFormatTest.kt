package com.dietpizza.rxplayer.feature.player.viewer

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExifFormatTest {
    private val full = mapOf(
        "Make" to "Google",
        "Model" to "Pixel 8 Pro",
        "LensModel" to "Pixel 8 Pro back camera 6.9mm f/1.68",
        "PhotographicSensitivity" to "125",
        "FNumber" to "1.68",
        "ExposureTime" to "0.0083333",
        "FocalLength" to "6.9",
        "DateTimeOriginal" to "2026:05:01 14:32:10",
        // Location: must never be read or shown.
        "GPSLatitude" to "51/1,30/1,0/1",
        "GPSLongitude" to "0/1,7/1,0/1",
        "GPSLatitudeRef" to "N",
        "GPSAltitude" to "35/1",
    )

    @Test
    fun neverReadsOrShowsLocation() {
        val asked = ArrayList<String>()
        val rows = ExifFormat.rows({ tag -> asked += tag; full[tag] }, Locale.US)
        assertTrue(asked.none { it.startsWith("GPS", ignoreCase = true) })
        assertTrue(ExifFormat.READ_TAGS.none { it.startsWith("GPS", ignoreCase = true) })
        val text = rows.joinToString { it.first + "=" + it.second }
        assertFalse(text.contains("Location", ignoreCase = true))
        assertFalse(text.contains("GPS", ignoreCase = true))
        assertFalse(text.contains("51"))
    }

    @Test
    fun cameraRows() {
        val rows = ExifFormat.rows({ full[it] }, Locale.US).toMap()
        assertEquals("Google Pixel 8 Pro", rows["Camera"])
        assertEquals("Pixel 8 Pro back camera 6.9mm f/1.68", rows["Lens"])
        assertEquals("ƒ/1.7 · 1/120 s · ISO 125 · 6.9 mm", rows["Exposure"])
        assertTrue(rows["Taken"]!!.contains("2026"))
    }

    @Test
    fun emptyExifGivesNoRows() {
        assertTrue(ExifFormat.rows({ null }).isEmpty())
        assertTrue(ExifFormat.rows({ "  " }).isEmpty())
    }

    @Test
    fun cameraNameDedupesMake() {
        assertEquals("Canon EOS R5", ExifFormat.camera("Canon", "Canon EOS R5"))
        assertEquals("SONY ILCE-7M3", ExifFormat.camera("SONY", "ILCE-7M3"))
        assertEquals("Pixel 8", ExifFormat.camera(null, "Pixel 8"))
        assertNull(ExifFormat.camera(" ", null))
    }

    @Test
    fun exposureValues() {
        assertEquals("ƒ/2.8", ExifFormat.aperture(2.8))
        assertEquals("ƒ/8", ExifFormat.aperture(8.0))
        assertNull(ExifFormat.aperture(0.0))
        assertEquals("1/4000 s", ExifFormat.shutter(0.00025))
        assertEquals("1/60 s", ExifFormat.shutter(1.0 / 60))
        assertEquals("0.3 s", ExifFormat.shutter(0.3))
        assertEquals("2 s", ExifFormat.shutter(2.0))
        assertEquals("ISO 100", ExifFormat.iso("100, 100"))
        assertNull(ExifFormat.iso("abc"))
        assertEquals("24 mm", ExifFormat.focalLength(24.0))
    }

    @Test
    fun sizesAndResolution() {
        assertEquals("4032 × 3024 · 12.2 MP", ExifFormat.resolution(4032, 3024))
        assertEquals("640 × 480", ExifFormat.resolution(640, 480))
        assertNull(ExifFormat.resolution(0, 10))
        assertEquals("2.4 MB", ExifFormat.size((2.4 * 1024 * 1024).toLong()))
        assertEquals("512 KB", ExifFormat.size(512 * 1024))
        assertNull(ExifFormat.size(0))
    }

    @Test
    fun badDateIgnored() {
        assertNull(ExifFormat.date("0000:00:00 00:00:00"))
        assertNull(ExifFormat.date("yesterday"))
    }
}
