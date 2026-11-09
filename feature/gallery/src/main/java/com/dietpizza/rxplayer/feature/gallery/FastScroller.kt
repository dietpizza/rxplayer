package com.dietpizza.rxplayer.feature.gallery

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val ThumbTouchWidth = 48.dp
private val ThumbTouchHeight = 64.dp
private val ThumbWidth = 6.dp
private val ThumbDraggingWidth = 10.dp
private val ThumbHeight = 48.dp
private const val HIDE_DELAY_MS = 1_500L

/**
 * State of an album grid's fast scroller. Geometry is in the coordinates of the container that
 * holds both the grid and the [FastScroller] (they must fill it alike), written during layout.
 */
@Stable
internal class FastScrollerState(val grid: LazyGridState) {
    var dragging by mutableStateOf(false)
        internal set
    var visible by mutableStateOf(false)
        internal set

    /** Thumb position while dragging, 0..1 of the track. */
    internal var dragFraction by mutableFloatStateOf(0f)
    internal var dragIndex by mutableIntStateOf(0)
    internal var totalCells = 0

    // Layout-phase geometry (px); plain fields, never read by composition.
    internal var containerWidth = 0f
    internal var trackTop = 0f
    internal var trackLength = 1f
    internal var thumbTouchWidth = 0f
    internal var thumbTouchHeight = 0f

    internal fun fraction(): Float = if (dragging) dragFraction else grid.scrollFraction()

    internal fun thumbTop(): Float = trackTop + fraction() * trackLength
}

@Composable
internal fun rememberFastScrollerState(grid: LazyGridState): FastScrollerState = remember(grid) { FastScrollerState(grid) }

/**
 * Drag handling for the fast-scroller thumb. Goes on the container holding the grid and the
 * [FastScroller], so the pointer coordinates are stable while the thumb moves, and runs in the
 * Initial pass so a thumb drag never also scrolls the grid. Touches anywhere but the (visible)
 * thumb pass straight through to the grid.
 */
internal fun Modifier.fastScrollerInput(state: FastScrollerState): Modifier = pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!state.visible || state.totalCells < 2) return@awaitEachGesture
        val p = down.position
        val top = state.thumbTop()
        val onThumb = p.x >= state.containerWidth - state.thumbTouchWidth &&
            p.y >= top && p.y < top + state.thumbTouchHeight
        if (!onThumb) return@awaitEachGesture
        down.consume()
        val grab = p.y - top // keep the finger where it grabbed the thumb
        state.dragFraction = state.grid.scrollFraction()
        state.dragIndex = -1
        state.dragging = true
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.fastFirstOrNull { it.id == down.id } ?: break
                change.consume()
                if (!change.pressed) break
                val fraction = ((change.position.y - grab - state.trackTop) / state.trackLength).coerceIn(0f, 1f)
                state.dragFraction = fraction
                // Same mapping as scrollFraction(), so the thumb doesn't jump on release.
                val info = state.grid.layoutInfo
                val scrollable = (info.totalItemsCount - info.visibleItemsInfo.size).coerceAtLeast(0)
                val index = (scrollable * fraction).roundToInt()
                if (index != state.dragIndex) {
                    state.dragIndex = index
                    state.grid.requestScrollToItem(index)
                }
            }
        } finally {
            state.dragging = false
        }
    }
}

/**
 * Scroll thumb on the end edge of an album grid, with a bubble showing the section under the
 * thumb ([labelAt], e.g. the day) while dragging. Appears while the grid scrolls and fades
 * [HIDE_DELAY_MS] after. The thumb is placed in the layout phase from the scroll position, so
 * scrolling never recomposes it. Pair with [fastScrollerInput] on the shared container.
 */
@Composable
internal fun FastScroller(
    state: FastScrollerState,
    totalCells: Int,
    labelAt: (cellIndex: Int) -> String,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 0.dp,
) {
    state.totalCells = totalCells
    val scrolling by remember(state) { derivedStateOf { state.grid.isScrollInProgress } }
    LaunchedEffect(state, scrolling, state.dragging) {
        if (scrolling || state.dragging) {
            state.visible = true
        } else {
            delay(HIDE_DELAY_MS)
            state.visible = false
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (state.visible) 1f else 0f,
        animationSpec = tween(if (state.visible) 100 else 250),
        label = "fastScrollerAlpha",
    )
    if (alpha == 0f && !state.visible) return

    Box(
        modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                state.containerWidth = placeable.width.toFloat()
                state.thumbTouchWidth = ThumbTouchWidth.toPx()
                state.thumbTouchHeight = ThumbTouchHeight.toPx()
                state.trackTop = topPadding.toPx()
                state.trackLength = (placeable.height - state.trackTop - bottomPadding.toPx() - state.thumbTouchHeight)
                    .coerceAtLeast(1f)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .graphicsLayer { this.alpha = alpha },
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .layout { measurable, c ->
                    val placeable = measurable.measure(c)
                    layout(placeable.width, placeable.height) {
                        placeable.place(IntOffset(0, state.thumbTop().roundToInt()))
                    }
                }
                .size(ThumbTouchWidth, ThumbTouchHeight),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                Modifier
                    .padding(end = 4.dp)
                    .size(if (state.dragging) ThumbDraggingWidth else ThumbWidth, ThumbHeight)
                    .background(
                        if (state.dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        CircleShape,
                    ),
            )
        }

        if (state.dragging) {
            val label = labelAt(state.dragIndex.coerceAtLeast(0))
            if (label.isNotEmpty()) {
                Surface(
                    shape = BubbleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .layout { measurable, c ->
                            val placeable = measurable.measure(c.copy(minWidth = 0, minHeight = 0))
                            layout(placeable.width, placeable.height) {
                                val y = state.thumbTop() + (state.thumbTouchHeight - placeable.height) / 2f
                                placeable.place(IntOffset(0, y.roundToInt()))
                            }
                        }
                        .padding(end = ThumbTouchWidth),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

private val BubbleShape = RoundedCornerShape(percent = 50)

/** How far the grid is scrolled, 0..1, from the first visible cell. Allocation-free. */
internal fun LazyGridState.scrollFraction(): Float {
    val info = layoutInfo
    val scrollable = info.totalItemsCount - info.visibleItemsInfo.size
    if (scrollable <= 0) return 0f
    return (firstVisibleItemIndex.toFloat() / scrollable).coerceIn(0f, 1f)
}
