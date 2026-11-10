package com.dietpizza.rxplayer.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.dietpizza.rxplayer.core.model.AppearanceSettings
import com.dietpizza.rxplayer.core.designsystem.theme.RxTheme

/**
 * The user's own appearance. The player screen itself is always dark and pure black, but its
 * sheets, menus and dialogs use this, so they look the same as everywhere else in the app.
 */
internal val LocalAppAppearance = staticCompositionLocalOf<AppearanceSettings?> { null }

/** Wraps a sheet, menu or dialog in the app's theme rather than the player's black one. */
@Composable
internal fun AppThemed(content: @Composable () -> Unit) {
    val appearance = LocalAppAppearance.current
    if (appearance == null) content() else RxTheme(appearance, content)
}
