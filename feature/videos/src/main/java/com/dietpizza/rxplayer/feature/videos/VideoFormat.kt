package com.dietpizza.rxplayer.feature.videos

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Pre-formatting for list rows. Called from the ViewModels (off the main thread, once per data
 * change), never per frame.
 */
internal object VideoFormat {

    /** "1 video", "12 videos". */
    fun videoCount(count: Int): String = if (count == 1) "1 video" else "$count videos"

    /** Decimal units like the system Files app: "820 KB", "4.2 GB", "12 GB". */
    fun bytes(bytes: Long): String {
        if (bytes < 1_000) return "$bytes B"
        var value = bytes / 1_000.0
        var unit = 0
        while (value >= 1_000 && unit < UNITS.lastIndex) {
            value /= 1_000
            unit++
        }
        return if (value < 10) {
            String.format(Locale.getDefault(), "%.1f %s", value, UNITS[unit])
        } else {
            String.format(Locale.getDefault(), "%d %s", value.toLong(), UNITS[unit])
        }
    }

    /** "0:42", "12:31", "1:02:03". Null when unknown. */
    fun duration(ms: Long): String? {
        if (ms <= 0) return null
        val total = ms / 1_000
        val h = total / 3_600
        val m = (total % 3_600) / 60
        val s = total % 60
        return if (h > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.ROOT, "%d:%02d", m, s)
        }
    }

    /**
     * Resolution chip from the frame size. Uses both sides so rotated (portrait) and letterboxed
     * (e.g. 1920×800) videos still get the label people expect.
     */
    fun resolution(width: Int, height: Int): String? {
        if (width <= 0 || height <= 0) return null
        val long = max(width, height)
        val short = min(width, height)
        return when {
            long >= 3_840 || short >= 2_160 -> "4K"
            long >= 2_560 || short >= 1_440 -> "1440p"
            long >= 1_920 || short >= 1_080 -> "1080p"
            long >= 1_280 || short >= 720 -> "720p"
            long >= 854 || short >= 480 -> "480p"
            else -> "${short}p"
        }
    }

    private val UNITS = arrayOf("KB", "MB", "GB", "TB")
}
