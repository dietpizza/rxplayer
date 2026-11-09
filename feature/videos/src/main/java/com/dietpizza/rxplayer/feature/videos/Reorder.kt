package com.dietpizza.rxplayer.feature.videos

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Drag-to-reorder for a LazyColumn whose item indices are the list's indices (no headers).
 * The dragged item follows the finger through a draw-phase translation (no recomposition per
 * frame); neighbours slide with `animateItem`. Each layout pass moves the item at most one slot,
 * which keeps the bookkeeping exact even when pointer events outrun layout.
 */
@Stable
internal class ReorderState(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
) {
    var draggingKey: Any? by mutableStateOf(null)
        private set

    /** Translation of the dragged item from its laid-out slot. Read only in the draw phase. */
    var offset by mutableFloatStateOf(0f)
        private set

    /** Called with list indices when the dragged item crosses a neighbour's midpoint. */
    var onMove: (from: Int, to: Int) -> Unit = { _, _ -> }

    /** Called once the item has settled after the finger lifts. */
    var onDragEnd: () -> Unit = {}

    private var expectedIndex = -1
    private var settle: Job? = null

    fun start(key: Any) {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        settle?.cancel()
        draggingKey = key
        offset = 0f
        expectedIndex = info.index
    }

    fun drag(dy: Float) {
        val key = draggingKey ?: return
        offset += dy
        val items = listState.layoutInfo.visibleItemsInfo
        val pos = items.indexOfFirst { it.key == key }
        if (pos < 0) return
        val current = items[pos]
        // The last move hasn't been laid out yet: just accumulate the offset.
        if (current.index != expectedIndex) return
        val top = current.offset + offset
        val bottom = top + current.size
        val next = items.getOrNull(pos + 1)
        val prev = items.getOrNull(pos - 1)
        val target = when {
            next != null && bottom > next.offset + next.size / 2f -> next
            prev != null && top < prev.offset + prev.size / 2f -> prev
            else -> return
        }
        // LazyColumn keeps the first visible item anchored by key; pin the scroll position so
        // moving that item doesn't scroll the list.
        val first = listState.firstVisibleItemIndex
        if (current.index == first || target.index == first) {
            listState.requestScrollToItem(first, listState.firstVisibleItemScrollOffset)
        }
        val newTop = if (target.index > current.index) {
            target.offset + target.size - current.size
        } else {
            target.offset
        }
        offset -= newTop - current.offset
        expectedIndex = target.index
        onMove(current.index, target.index)
    }

    fun end() {
        if (draggingKey == null) return
        val from = offset
        settle = scope.launch {
            try {
                animate(from, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ -> offset = value }
            } finally {
                draggingKey = null
                offset = 0f
                onDragEnd()
            }
        }
    }
}

@Composable
internal fun rememberReorderState(listState: LazyListState): ReorderState {
    val scope = rememberCoroutineScope()
    return remember(listState) { ReorderState(listState, scope) }
}

/** Item modifier: lifts and translates the dragged item, animates the others into place. */
@Composable
internal fun LazyItemScope.reorderableItem(state: ReorderState, key: Any): Modifier =
    if (state.draggingKey == key) {
        Modifier
            .zIndex(1f)
            .graphicsLayer { translationY = state.offset }
    } else {
        Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
    }

/** Drag-handle modifier. */
internal fun Modifier.reorderHandle(state: ReorderState, key: Any): Modifier =
    pointerInput(state, key) {
        detectDragGestures(
            onDragStart = { state.start(key) },
            onDragEnd = { state.end() },
            onDragCancel = { state.end() },
        ) { change, amount ->
            change.consume()
            state.drag(amount.y)
        }
    }
