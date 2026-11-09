package com.dietpizza.rxplayer.feature.gallery

import androidx.compose.runtime.Immutable
import com.dietpizza.rxplayer.core.model.GalleryItem
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.MediaSort
import com.dietpizza.rxplayer.core.model.MediaSortField

/** One cell of the album grid: a full-width day header or a media tile. */
@Immutable
sealed interface AlbumCell {
    /** Lazy-grid key. */
    val key: String
}

@Immutable
data class HeaderCell(override val key: String, val label: String) : AlbumCell

@Immutable
data class MediaCell(
    /** MediaKey string. */
    override val key: String,
    /** Position in [AlbumGrid.keys] (the viewer playlist). */
    val index: Int,
    val contentUri: String,
    val mimeType: String,
    val isVideo: Boolean,
    /** "0:42" for videos with a known duration, else null. */
    val duration: String?,
    val isGif: Boolean,
) : AlbumCell

/**
 * Everything the album grid draws, built once per data/sort change off the main thread.
 * Not a data class: equality is identity, so Compose skips work when the same instance is re-read.
 */
@Immutable
class AlbumGrid(
    val cells: List<AlbumCell>,
    /** Cell indices of headers, ascending. Runs of media between them become one `items` call. */
    val headerIndices: IntArray,
    /** Media keys in display order: the viewer's playlist. */
    val keys: List<MediaKey>,
    /** For every cell, its index into [sectionLabels] (fast-scroller bubble). */
    val sectionOfCell: IntArray,
    val sectionLabels: List<String>,
) {
    val mediaCount: Int get() = keys.size

    /** Bubble text for [cellIndex] (clamped). Allocation-free. */
    fun sectionLabelAt(cellIndex: Int): String {
        if (sectionOfCell.isEmpty()) return ""
        return sectionLabels[sectionOfCell[cellIndex.coerceIn(0, sectionOfCell.lastIndex)]]
    }

    companion object {
        val EMPTY = AlbumGrid(emptyList(), IntArray(0), emptyList(), IntArray(0), emptyList())
    }
}

/**
 * Builds the grid for [items] (already in [sort] order). Date order gets a header per local day;
 * name and size order have no headers, and their fast-scroller sections are runs of the same
 * initial / formatted size.
 */
internal fun buildAlbumGrid(
    items: List<GalleryItem>,
    sort: MediaSort,
    dates: DateHeaderFormatter,
    sizeLabel: (Long) -> String = { GalleryFormat.bytes(it) },
): AlbumGrid {
    if (items.isEmpty()) return AlbumGrid.EMPTY
    val withHeaders = sort.field == MediaSortField.DATE_TAKEN
    val cells = ArrayList<AlbumCell>(items.size + if (withHeaders) items.size / 8 + 1 else 0)
    val keys = ArrayList<MediaKey>(items.size)
    val headers = IntArrayBuilder()
    val sectionOfCell = IntArrayBuilder(items.size)
    val sectionLabels = ArrayList<String>()

    var lastDay = Long.MIN_VALUE
    var lastLabel: String? = null
    for ((index, item) in items.withIndex()) {
        if (withHeaders) {
            val day = dates.epochDay(item.dateTakenMs)
            if (day != lastDay) {
                lastDay = day
                val label = dates.label(day)
                sectionLabels += label
                headers.add(cells.size)
                sectionOfCell.add(sectionLabels.lastIndex)
                cells += HeaderCell(key = "h:$day", label = label)
            }
        } else {
            val label = when (sort.field) {
                MediaSortField.SIZE -> sizeLabel(item.sizeBytes)
                else -> GalleryFormat.initial(item.displayName)
            }
            if (label != lastLabel) {
                lastLabel = label
                sectionLabels += label
            }
        }
        sectionOfCell.add(sectionLabels.lastIndex)
        keys += item.key
        cells += MediaCell(
            key = item.key.value,
            index = index,
            contentUri = item.contentUri,
            mimeType = item.mimeType,
            isVideo = item.isVideo,
            duration = if (item.isVideo) GalleryFormat.duration(item.durationMs) else null,
            isGif = item.isGif,
        )
    }
    return AlbumGrid(cells, headers.toArray(), keys, sectionOfCell.toArray(), sectionLabels)
}

/** Growable IntArray, so per-cell bookkeeping doesn't box. */
private class IntArrayBuilder(capacity: Int = 16) {
    private var data = IntArray(maxOf(capacity, 4))
    private var size = 0

    fun add(value: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = value
    }

    fun toArray(): IntArray = data.copyOf(size)
}

/**
 * MIME type for a share intent over [mimeTypes]: the exact type if they all match, `image/` +
 * `*` or `video/` + `*` if they share a top-level type, else `*` + `/` + `*`.
 */
internal fun shareMimeType(mimeTypes: List<String>): String {
    if (mimeTypes.isEmpty()) return "*/*"
    val first = mimeTypes[0]
    if (mimeTypes.all { it == first } && first.isNotEmpty()) return first
    val top = first.substringBefore('/', missingDelimiterValue = "")
    if (top.isNotEmpty() && mimeTypes.all { it.substringBefore('/', missingDelimiterValue = "") == top }) return "$top/*"
    return "*/*"
}
