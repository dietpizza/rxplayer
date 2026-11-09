package com.dietpizza.rxplayer.feature.search

import com.dietpizza.rxplayer.core.model.GalleryItem
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Photos / videos chip. */
enum class TypeFilter { ALL, PHOTOS, VIDEOS }

/** Date chip, on [GalleryItem.dateTakenMs]. [CUSTOM] uses [SearchFilters.customStartDay]..[SearchFilters.customEndDay]. */
enum class DateFilter { ANY, LAST_7_DAYS, LAST_30_DAYS, THIS_YEAR, CUSTOM }

/** Size chip. Bounds in bytes, [min, max). Decimal units, like the sizes shown elsewhere. */
enum class SizeFilter(val minBytes: Long, val maxBytes: Long) {
    ANY(0, Long.MAX_VALUE),
    UNDER_10_MB(0, 10 * MB),
    MB_10_TO_100(10 * MB, 100 * MB),
    MB_100_TO_1000(100 * MB, 1_000 * MB),
    OVER_1_GB(1_000 * MB, Long.MAX_VALUE),
}

/** Duration chip (videos only: any choice but [ANY] leaves photos out). Bounds in ms, [min, max). */
enum class DurationFilter(val minMs: Long, val maxMs: Long) {
    ANY(0, Long.MAX_VALUE),
    UNDER_1_MIN(0, MINUTE),
    MIN_1_TO_10(MINUTE, 10 * MINUTE),
    MIN_10_TO_60(10 * MINUTE, 60 * MINUTE),
    OVER_1_HOUR(60 * MINUTE, Long.MAX_VALUE),
}

private const val MB = 1_000_000L
private const val MINUTE = 60_000L
private const val DAY = 86_400_000L

data class SearchFilters(
    val type: TypeFilter = TypeFilter.ALL,
    val date: DateFilter = DateFilter.ANY,
    /** Custom range, inclusive local calendar days as epoch days. */
    val customStartDay: Long? = null,
    val customEndDay: Long? = null,
    val size: SizeFilter = SizeFilter.ANY,
    val duration: DurationFilter = DurationFilter.ANY,
) {
    val isActive: Boolean
        get() = type != TypeFilter.ALL || date != DateFilter.ANY || size != SizeFilter.ANY || duration != DurationFilter.ANY

    /** Only videos can match (video type, or a duration bound). */
    val videosOnly: Boolean get() = type == TypeFilter.VIDEOS || duration != DurationFilter.ANY
}

/** Lower-case, accents stripped ("Café" → "cafe"), so matching is case- and accent-insensitive. */
internal fun normalizeForSearch(text: String): String {
    val lower = text.lowercase(Locale.ROOT)
    if (lower.all { it.code < 0x80 }) return lower
    return COMBINING_MARKS.replace(Normalizer.normalize(lower, Normalizer.Form.NFD), "")
}

private val COMBINING_MARKS = Regex("\\p{Mn}+")

/** Whitespace-separated, normalised query tokens. */
internal fun tokenize(query: String): List<String> =
    normalizeForSearch(query).split(' ', '\t', '\n').filter { it.isNotEmpty() }

/**
 * The library prepared for searching: names normalised once per library change, not per keystroke.
 * Items keep the order they came in (newest first).
 */
class SearchIndex(items: List<GalleryItem>) {
    internal val items: List<GalleryItem> = items
    internal val names: Array<String> = Array(items.size) { normalizeForSearch(items[it].displayName) }
    internal val folders: Array<String> = Array(items.size) {
        normalizeForSearch(items[it].folderPath.substringAfterLast('/'))
    }

    val size: Int get() = items.size

    companion object {
        val EMPTY = SearchIndex(emptyList())
    }
}

/**
 * Filename + folder-name search (plan §4 #11). Every query token must appear in the file name or
 * its folder's name. Results are ranked: names starting with the first token, then names containing
 * every token, then folder matches; newest first within each rank. A blank query with active
 * filters lists everything that passes them; a blank query without filters returns nothing.
 */
fun search(
    index: SearchIndex,
    query: String,
    filters: SearchFilters,
    nowMs: Long,
    zone: ZoneId,
): List<GalleryItem> {
    val tokens = tokenize(query)
    if (tokens.isEmpty() && !filters.isActive) return emptyList()
    val dateRange = dateRange(filters, nowMs, zone)
    val prefix = ArrayList<GalleryItem>()
    val inName = ArrayList<GalleryItem>()
    val inFolder = ArrayList<GalleryItem>()
    for (i in 0 until index.size) {
        val item = index.items[i]
        if (!passes(item, filters, dateRange)) continue
        if (tokens.isEmpty()) {
            prefix += item
            continue
        }
        val name = index.names[i]
        val folder = index.folders[i]
        var allInName = true
        var matches = true
        for (token in tokens) {
            if (name.contains(token)) continue
            allInName = false
            if (!folder.contains(token)) {
                matches = false
                break
            }
        }
        if (!matches) continue
        when {
            allInName && name.startsWith(tokens[0]) -> prefix += item
            allInName -> inName += item
            else -> inFolder += item
        }
    }
    if (inName.isEmpty() && inFolder.isEmpty()) return prefix
    val out = ArrayList<GalleryItem>(prefix.size + inName.size + inFolder.size)
    out.addAll(prefix)
    out.addAll(inName)
    out.addAll(inFolder)
    return out
}

/** [start, end) in epoch ms for the date filter, or null for any date. */
internal fun dateRange(filters: SearchFilters, nowMs: Long, zone: ZoneId): LongRange? = when (filters.date) {
    DateFilter.ANY -> null
    DateFilter.LAST_7_DAYS -> (nowMs - 7 * DAY) until Long.MAX_VALUE
    DateFilter.LAST_30_DAYS -> (nowMs - 30 * DAY) until Long.MAX_VALUE
    DateFilter.THIS_YEAR -> {
        val year = Instant.ofEpochMilli(nowMs).atZone(zone).year
        LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli() until Long.MAX_VALUE
    }
    DateFilter.CUSTOM -> {
        val startDay = filters.customStartDay
        if (startDay == null) {
            null
        } else {
            val endDay = maxOf(filters.customEndDay ?: startDay, startDay)
            val start = LocalDate.ofEpochDay(startDay).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = LocalDate.ofEpochDay(endDay + 1).atStartOfDay(zone).toInstant().toEpochMilli()
            start until end
        }
    }
}

private fun passes(item: GalleryItem, filters: SearchFilters, dateRange: LongRange?): Boolean {
    when (filters.type) {
        TypeFilter.ALL -> Unit
        TypeFilter.PHOTOS -> if (item.isVideo) return false
        TypeFilter.VIDEOS -> if (!item.isVideo) return false
    }
    if (dateRange != null && item.dateTakenMs !in dateRange) return false
    if (filters.size != SizeFilter.ANY &&
        (item.sizeBytes < filters.size.minBytes || item.sizeBytes >= filters.size.maxBytes)
    ) {
        return false
    }
    if (filters.duration != DurationFilter.ANY) {
        if (!item.isVideo) return false
        if (item.durationMs < filters.duration.minMs || item.durationMs >= filters.duration.maxMs) return false
    }
    return true
}
