package com.dietpizza.rxplayer.core.designsystem.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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

    /** Header colours; [container] overrides the colour (e.g. the selection bar). */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun headerColors(container: Color = containerColor): TopAppBarColors =
        TopAppBarDefaults.topAppBarColors(containerColor = container, scrolledContainerColor = container)
}

/** Clips a top app bar to [RxBars.HeaderShape]. */
fun Modifier.rxHeader(): Modifier = clip(RxBars.HeaderShape)
