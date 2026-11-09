package com.dietpizza.rxplayer.feature.gallery

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import kotlin.math.roundToInt

/*
 * Album grid gestures. Both run in the Initial pass once they own the gesture, so the grid's own
 * scrollable never sees (and never scrolls on) a pinch or a drag-select. Neither allocates per
 * pointer event: hit-testing walks the existing layout info, and all state is primitives.
 *
 * Coordinates: the grid has no top content padding (the Scaffold's top inset is applied outside
 * it), so item offsets and pointer positions share one space.
 */

/** Index of the grid cell at ([x], [y]), preferring a (sticky) header over the tile under it, or -1. */
internal fun LazyGridState.cellIndexAt(x: Float, y: Float): Int {
    val items = layoutInfo.visibleItemsInfo
    var hit = -1
    for (i in items.indices) {
        val item = items[i]
        val top = item.offset.y
        val left = item.offset.x
        if (y >= top && y < top + item.size.height && x >= left && x < left + item.size.width) {
            if (item.contentType == HEADER_CONTENT_TYPE) return item.index
            if (hit < 0) hit = item.index
        }
    }
    return hit
}

internal const val HEADER_CONTENT_TYPE = "header"
internal const val MEDIA_CONTENT_TYPE = "media"

internal const val MIN_COLUMNS = 2
internal const val MAX_COLUMNS = 6

/** Cumulative pinch scale that changes the column count by one. */
private const val ZOOM_STEP = 1.28f

/**
 * Pinch to change the column count between [MIN_COLUMNS] and [MAX_COLUMNS]. Spreading the
 * fingers shows fewer, bigger tiles. Each step keeps the tile under the pinch centroid at the
 * same spot on screen: the new column count and the matching scroll position are applied in the
 * same measure pass, so no frame shows the grid at the wrong position.
 */
internal fun Modifier.pinchToChangeColumns(
    state: LazyGridState,
    columns: () -> Int,
    onColumnsChange: (Int) -> Unit,
): Modifier = pointerInput(state) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var zoom = 1f
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            var pressed = 0
            event.changes.fastForEach { if (it.pressed) pressed++ }
            if (pressed >= 2) {
                zoom *= event.calculateZoom()
                val current = columns()
                val target = when {
                    zoom >= ZOOM_STEP && current > MIN_COLUMNS -> current - 1
                    zoom <= 1f / ZOOM_STEP && current < MAX_COLUMNS -> current + 1
                    else -> current
                }
                if (target != current) {
                    zoom = 1f
                    val centroid = event.calculateCentroid(useCurrent = true)
                    state.keepAnchorAcrossColumnChange(centroid, current, target)
                    onColumnsChange(target)
                }
                event.changes.fastForEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.fastAny { it.pressed })
    }
}

/**
 * Requests the scroll position that keeps the tile under [focus] in place once the grid
 * re-lays out at [to] columns. Tiles are square, so the new tile height scales with from/to.
 */
private fun LazyGridState.keepAnchorAcrossColumnChange(focus: Offset, from: Int, to: Int) {
    if (focus == Offset.Unspecified) return
    val items = layoutInfo.visibleItemsInfo
    if (items.isEmpty()) return
    var anchor = cellIndexAt(focus.x, focus.y)
    var anchorPos = -1
    for (i in items.indices) if (items[i].index == anchor) anchorPos = i
    if (anchorPos < 0 || items[anchorPos].contentType != MEDIA_CONTENT_TYPE) {
        // Between tiles or on a header: anchor on the nearest media tile by row.
        var best = Float.MAX_VALUE
        anchorPos = -1
        for (i in items.indices) {
            val item = items[i]
            if (item.contentType != MEDIA_CONTENT_TYPE) continue
            val d = kotlin.math.abs(item.offset.y + item.size.height / 2f - focus.y)
            if (d < best) {
                best = d
                anchorPos = i
            }
        }
        if (anchorPos < 0) return
        anchor = items[anchorPos].index
    }
    val item = items[anchorPos]
    val height = item.size.height.toFloat().coerceAtLeast(1f)
    val fraction = ((focus.y - item.offset.y) / height).coerceIn(0f, 1f)
    val newHeight = height * from / to
    val newTop = focus.y - fraction * newHeight
    // The item's line is placed at the viewport top minus scrollOffset; negative = further down.
    requestScrollToItem(anchor, scrollOffset = (-newTop).roundToInt())
}

/**
 * Drag-to-select state: where the finger is and how fast the grid should auto-scroll because
 * it's near an edge. Primitive fields only.
 */
@Stable
internal class DragSelectState {
    /** px per frame; 0 when not auto-scrolling. Read by the auto-scroll loop. */
    var autoScrollSpeed by mutableFloatStateOf(0f)
    var pointerX = 0f
    var pointerY = 0f
    var active = false
}

private val AutoScrollEdge = 72.dp
private val AutoScrollMaxSpeed = 24.dp

/**
 * Long-press a tile to start selecting, then drag across tiles to extend the range (and near
 * the top/bottom edge to auto-scroll). [onStart] returns false if the long press wasn't on a
 * tile, which lets the gesture go.
 */
internal fun Modifier.dragToSelect(
    state: LazyGridState,
    drag: DragSelectState,
    onStart: (cellIndex: Int) -> Boolean,
    onMove: (cellIndex: Int) -> Unit,
    onEnd: () -> Unit,
): Modifier = pointerInput(state, drag) {
    val edge = AutoScrollEdge.toPx()
    val maxSpeed = AutoScrollMaxSpeed.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
        val startIndex = state.cellIndexAt(longPress.position.x, longPress.position.y)
        if (startIndex < 0 || !onStart(startIndex)) return@awaitEachGesture
        longPress.consume()
        drag.active = true
        drag.pointerX = longPress.position.x
        drag.pointerY = longPress.position.y
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.fastFirstOrNull { it.id == down.id } ?: break
                change.consume()
                if (!change.pressed) break
                val p = change.position
                drag.pointerX = p.x
                drag.pointerY = p.y
                val height = size.height.toFloat()
                drag.autoScrollSpeed = when {
                    p.y < edge -> -maxSpeed * ((edge - p.y) / edge).coerceAtMost(1f)
                    p.y > height - edge -> maxSpeed * ((p.y - (height - edge)) / edge).coerceAtMost(1f)
                    else -> 0f
                }
                val index = state.cellIndexAt(p.x, p.y)
                if (index >= 0) onMove(index)
            }
        } finally {
            drag.active = false
            drag.autoScrollSpeed = 0f
            onEnd()
        }
    }
}
