package com.dietpizza.rxplayer.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 4dp-based spacing scale. Read via `RxTheme.spacing` (or `LocalRxSpacing.current`). */
@Immutable
data class RxSpacing(
    val none: Dp = 0.dp,
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
    /** Horizontal screen gutter. */
    val gutter: Dp = 16.dp,
)

val LocalRxSpacing = staticCompositionLocalOf { RxSpacing() }
