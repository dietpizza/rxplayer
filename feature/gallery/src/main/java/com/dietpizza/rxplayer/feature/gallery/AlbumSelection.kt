package com.dietpizza.rxplayer.feature.gallery

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Multi-selection over an album grid's media keys, including Google-Photos-style drag ranges:
 * long-press a tile, then drag; every tile between the anchor and the finger takes the anchor's
 * new state, and moving back restores the tiles you un-cross.
 *
 * Lives in the ViewModel (survives rotation). [selected] is one snapshot state holding the whole
 * set, so anything reading it recomposes on every change. Grid tiles therefore read it through a
 * per-tile `derivedStateOf { key in selected }` and read [isActive] (itself derived), so a toggle
 * or drag step only recomposes the tiles whose membership flipped, plus every tile once when
 * selection mode starts or ends.
 */
@Stable
class AlbumSelection {
    var selected: Set<String> by mutableStateOf(emptySet())
        private set

    /** Derived, so readers are invalidated only when selection mode starts or ends. */
    val isActive: Boolean by derivedStateOf { selected.isNotEmpty() }

    private var anchor = -1
    private var lastHover = -1
    private var base: Set<String> = emptySet()
    private var selecting = true

    fun toggle(key: String) {
        selected = if (key in selected) selected - key else selected + key
    }

    fun clear() {
        selected = emptySet()
        anchor = -1
    }

    /** Keeps only keys still present (after the album changes underneath the selection). */
    fun retainAll(present: Set<String>) {
        if (selected.isEmpty()) return
        val kept = selected.filterTo(HashSet()) { it in present }
        if (kept.size != selected.size) selected = kept
    }

    /** Starts a drag range at [cellIndex]; returns false if it isn't a media cell. */
    fun beginDrag(cellIndex: Int, cells: List<AlbumCell>): Boolean {
        val cell = cells.getOrNull(cellIndex) as? MediaCell ?: return false
        anchor = cellIndex
        lastHover = -1
        base = selected
        selecting = cell.key !in base
        dragTo(cellIndex, cells)
        return true
    }

    /** Extends the range to [cellIndex]. Cheap no-op while the finger stays over the same cell. */
    fun dragTo(cellIndex: Int, cells: List<AlbumCell>) {
        if (anchor < 0 || cellIndex < 0 || cellIndex == lastHover) return
        lastHover = cellIndex
        val from = minOf(anchor, cellIndex)
        val to = maxOf(anchor, cellIndex).coerceAtMost(cells.lastIndex)
        val next = HashSet<String>(base.size + (to - from + 1))
        next.addAll(base)
        for (i in from..to) {
            val cell = cells[i] as? MediaCell ?: continue
            if (selecting) next.add(cell.key) else next.remove(cell.key)
        }
        selected = next
    }

    fun endDrag() {
        anchor = -1
        lastHover = -1
        base = emptySet()
    }
}
