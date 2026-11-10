package com.dietpizza.rxplayer.core.designsystem.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The app's bars: a header (top app bar) with rounded bottom corners and a bottom navigation bar
 * with rounded top corners, both a tone above the deeper page colour so the rounding reads.
 */
object RxBars {
    val CornerRadius = 24.dp

    /** Header: square top (it runs under the status bar), rounded bottom. */
    val HeaderShape = RoundedCornerShape(bottomStart = CornerRadius, bottomEnd = CornerRadius)

    /** Bottom bar: rounded top, square bottom (it runs under the gesture/nav bar). */
    val BottomBarShape = RoundedCornerShape(topStart = CornerRadius, topEnd = CornerRadius)

    /** Extra space above and below the bottom bar's items. */
    val BottomBarVerticalPadding = 8.dp

    /** Bar colour (header and bottom bar). */
    val containerColor: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainer

    /**
     * Page colour between the bars: deeper than [containerColor] in light and dark themes
     * (true black with the pure-black option).
     */
    val pageColor: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceDim

    /** Cards and grouped (segmented) rows on a page: the same tone as the bars, above the page. */
    val cardColor: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainer

    /** Segmented rows drawn as cards on a page (settings groups, server lists). */
    @Composable
    fun cardListItemColors(): ListItemColors =
        ListItemDefaults.segmentedColors(containerColor = cardColor, disabledContainerColor = cardColor)

    /** Plain flat rows: no fill of their own, so they sit on the page colour in any theme. */
    @Composable
    fun flatListItemColors(): ListItemColors =
        ListItemDefaults.colors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent)

    /** Choice rows in a sheet: unfilled, only the selected one is highlighted. */
    @Composable
    fun sheetListItemColors(): ListItemColors =
        ListItemDefaults.segmentedColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent)

    /**
     * Connected toggle buttons: unchecked ones get the highest container tone so they stand out
     * from cards and sheets (both a tone or two lower); checked keeps the theme's highlight.
     */
    @Composable
    fun toggleButtonColors(): ToggleButtonColors =
        ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)

    /** Gap between the buttons of a connected group: small, but visible against their fills. */
    val ButtonGroupGap = 4.dp

    /**
     * Shapes for button [index] of a connected group whose last index is [lastIndex]. The shape
     * never changes: no fully rounded pill when checked, no morph when pressed, so the group
     * stays symmetric.
     */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun connectedShapes(index: Int, lastIndex: Int): ToggleButtonShapes {
        val shape = when (index) {
            0 -> ButtonGroupDefaults.connectedLeadingButtonShape
            lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShape
            else -> ShapeDefaults.Small
        }
        return ToggleButtonShapes(shape = shape, pressedShape = shape, checkedShape = shape)
    }

    /** Header colours; [container] overrides the colour (e.g. the selection bar). */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun headerColors(container: Color = containerColor): TopAppBarColors =
        TopAppBarDefaults.topAppBarColors(containerColor = container, scrolledContainerColor = container)
}

/** Clips a top app bar to [RxBars.HeaderShape]. */
fun Modifier.rxHeader(): Modifier = clip(RxBars.HeaderShape)
