package com.dietpizza.rxplayer.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.dietpizza.rxplayer.core.model.AppearanceSettings
import com.dietpizza.rxplayer.core.model.ThemeMode

/**
 * App theme: M3 Expressive (springy expressive motion, expressive shapes, emphasized type).
 * Dynamic colour when enabled (minSdk 34, so always available), otherwise the coral scheme.
 */
@Composable
fun RxTheme(
    appearance: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit,
) {
    val dark = appearance.themeMode.isDark()
    val context = LocalContext.current
    // ColorScheme has identity equality and dynamic*ColorScheme() builds a new one per call, so
    // everything must be remembered. Otherwise each recomposition of RxTheme hands
    // MaterialTheme a "new" scheme, and since LocalColorScheme is a static local that
    // invalidates the entire UI tree.
    val colorScheme = remember(appearance.dynamicColor, dark, appearance.pureBlack) {
        val base = when {
            appearance.dynamicColor && dark -> dynamicDarkColorScheme(context)
            appearance.dynamicColor -> dynamicLightColorScheme(context)
            dark -> RxDarkColorScheme
            else -> RxLightColorScheme
        }
        if (dark && appearance.pureBlack) base.toPureBlack() else base
    }
    CompositionLocalProvider(LocalRxSpacing provides DefaultSpacing) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = ExpressiveMotion,
            shapes = RxShapes,
            typography = RxTypography,
            content = content,
        )
    }
}

private val DefaultSpacing = RxSpacing()
private val ExpressiveMotion = MotionScheme.expressive()

/** Resolves [ThemeMode] against the system setting. */
@Composable
@ReadOnlyComposable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Accessors for Rx-specific tokens, mirroring `MaterialTheme.*`. */
object RxTheme {
    val spacing: RxSpacing
        @Composable @ReadOnlyComposable get() = LocalRxSpacing.current
}
