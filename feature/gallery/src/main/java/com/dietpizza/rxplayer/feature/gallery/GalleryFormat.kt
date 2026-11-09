package com.dietpizza.rxplayer.feature.gallery

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pre-formatting for gallery models. Called from ViewModels (off the main thread, once per data
 * change), never per frame.
 */
internal object GalleryFormat {

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

    /** Decimal units like the system Files app: "820 KB", "4.2 GB", "12 GB". */
    fun bytes(bytes: Long, locale: Locale = Locale.getDefault()): String {
        if (bytes < 1_000) return "$bytes B"
        var value = bytes / 1_000.0
        var unit = 0
        while (value >= 1_000 && unit < UNITS.lastIndex) {
            value /= 1_000
            unit++
        }
        return if (value < 10) {
            String.format(locale, "%.1f %s", value, UNITS[unit])
        } else {
            String.format(locale, "%d %s", value.toLong(), UNITS[unit])
        }
    }

    /** Grouped integer: "12,345". */
    fun count(n: Int, locale: Locale = Locale.getDefault()): String = NumberFormat.getIntegerInstance(locale).format(n)

    /**
     * Fast-scroller label for name order: the upper-cased first letter, or "#" for anything that
     * isn't a letter.
     */
    fun initial(name: String): String {
        val c = name.firstOrNull() ?: return "#"
        return if (c.isLetter()) c.uppercaseChar().toString() else "#"
    }

    private val UNITS = arrayOf("KB", "MB", "GB", "TB")
}

/**
 * Day header labels: "Today", "Yesterday", the weekday for the rest of the last week, then a date
 * ("Mon, 3 Nov" this year, "3 Nov 2024" otherwise). [today] is fixed at construction, so one
 * instance should serve one build of the grid.
 */
internal class DateHeaderFormatter(
    private val today: LocalDate,
    private val zone: ZoneId,
    private val todayLabel: String,
    private val yesterdayLabel: String,
    private val weekdayFormat: DateTimeFormatter,
    private val sameYearFormat: DateTimeFormatter,
    private val otherYearFormat: DateTimeFormatter,
) {
    private val todayEpochDay = today.toEpochDay()

    /** Local calendar day of [epochMs], as an epoch day (the grouping key). */
    fun epochDay(epochMs: Long): Long = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().toEpochDay()

    fun label(epochDay: Long): String {
        val date = LocalDate.ofEpochDay(epochDay)
        return when (todayEpochDay - epochDay) {
            0L -> todayLabel
            1L -> yesterdayLabel
            in 2L..6L -> weekdayFormat.format(date)
            else -> if (date.year == today.year) sameYearFormat.format(date) else otherYearFormat.format(date)
        }
    }
}
