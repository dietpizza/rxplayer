package com.dietpizza.rxplayer.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Brand seed: coral red. Used for the launcher glyph and anywhere a fixed brand accent is needed. */
val RxCoralSeed = Color(0xFFFF5A4E)

// Tonal palettes derived from the coral seed (HCT hue ~28), Material Theme Builder style.
// Primary: coral (kept a touch more chromatic than "tonal spot" so the brand reads as coral, not brick).
private val Primary10 = Color(0xFF3E0400)
private val Primary20 = Color(0xFF640E04)
private val Primary30 = Color(0xFF8A2316)
private val Primary40 = Color(0xFFAC3A2B)
private val Primary80 = Color(0xFFFFB4A8)
private val Primary90 = Color(0xFFFFDAD4)

// Secondary: desaturated coral.
private val Secondary10 = Color(0xFF2C1512)
private val Secondary20 = Color(0xFF442925)
private val Secondary30 = Color(0xFF5D3F3A)
private val Secondary40 = Color(0xFF775651)
private val Secondary80 = Color(0xFFE7BDB6)
private val Secondary90 = Color(0xFFFFDAD4)

// Tertiary: warm amber (hue +60).
private val Tertiary10 = Color(0xFF261A00)
private val Tertiary20 = Color(0xFF3E2E04)
private val Tertiary30 = Color(0xFF574419)
private val Tertiary40 = Color(0xFF705C2E)
private val Tertiary80 = Color(0xFFDFC38C)
private val Tertiary90 = Color(0xFFFCDFA6)

// Error (M3 baseline).
private val Error10 = Color(0xFF410002)
private val Error20 = Color(0xFF690005)
private val Error30 = Color(0xFF93000A)
private val Error40 = Color(0xFFBA1A1A)
private val Error80 = Color(0xFFFFB4AB)
private val Error90 = Color(0xFFFFDAD6)

val RxLightColorScheme: ColorScheme = lightColorScheme(
    primary = Primary40,
    onPrimary = Color.White,
    primaryContainer = Primary90,
    onPrimaryContainer = Primary30,
    inversePrimary = Primary80,
    secondary = Secondary40,
    onSecondary = Color.White,
    secondaryContainer = Secondary90,
    onSecondaryContainer = Secondary30,
    tertiary = Tertiary40,
    onTertiary = Color.White,
    tertiaryContainer = Tertiary90,
    onTertiaryContainer = Tertiary30,
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF231918),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF231918),
    surfaceVariant = Color(0xFFF5DDDA),
    onSurfaceVariant = Color(0xFF534341),
    surfaceTint = Primary40,
    inverseSurface = Color(0xFF392E2C),
    inverseOnSurface = Color(0xFFFFEDEA),
    error = Error40,
    onError = Color.White,
    errorContainer = Error90,
    onErrorContainer = Error30,
    outline = Color(0xFF857370),
    outlineVariant = Color(0xFFD8C2BE),
    scrim = Color.Black,
    surfaceBright = Color(0xFFFFF8F6),
    surfaceContainer = Color(0xFFFCEAE7),
    surfaceContainerHigh = Color(0xFFF7E4E1),
    surfaceContainerHighest = Color(0xFFF1DFDC),
    surfaceContainerLow = Color(0xFFFFF0EE),
    surfaceContainerLowest = Color.White,
    surfaceDim = Color(0xFFE8D6D3),
    primaryFixed = Primary90,
    primaryFixedDim = Primary80,
    onPrimaryFixed = Primary10,
    onPrimaryFixedVariant = Primary30,
    secondaryFixed = Secondary90,
    secondaryFixedDim = Secondary80,
    onSecondaryFixed = Secondary10,
    onSecondaryFixedVariant = Secondary30,
    tertiaryFixed = Tertiary90,
    tertiaryFixedDim = Tertiary80,
    onTertiaryFixed = Tertiary10,
    onTertiaryFixedVariant = Tertiary30,
)

val RxDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Primary80,
    onPrimary = Primary20,
    primaryContainer = Primary30,
    onPrimaryContainer = Primary90,
    inversePrimary = Primary40,
    secondary = Secondary80,
    onSecondary = Secondary20,
    secondaryContainer = Secondary30,
    onSecondaryContainer = Secondary90,
    tertiary = Tertiary80,
    onTertiary = Tertiary20,
    tertiaryContainer = Tertiary30,
    onTertiaryContainer = Tertiary90,
    background = Color(0xFF1A1110),
    onBackground = Color(0xFFF1DFDC),
    surface = Color(0xFF1A1110),
    onSurface = Color(0xFFF1DFDC),
    surfaceVariant = Color(0xFF534341),
    onSurfaceVariant = Color(0xFFD8C2BE),
    surfaceTint = Primary80,
    inverseSurface = Color(0xFFF1DFDC),
    inverseOnSurface = Color(0xFF392E2C),
    error = Error80,
    onError = Error20,
    errorContainer = Error30,
    onErrorContainer = Error90,
    outline = Color(0xFFA08C89),
    outlineVariant = Color(0xFF534341),
    scrim = Color.Black,
    surfaceBright = Color(0xFF423735),
    surfaceContainer = Color(0xFF271D1C),
    surfaceContainerHigh = Color(0xFF322827),
    surfaceContainerHighest = Color(0xFF3D3231),
    surfaceContainerLow = Color(0xFF231918),
    surfaceContainerLowest = Color(0xFF140C0B),
    surfaceDim = Color(0xFF1A1110),
    primaryFixed = Primary90,
    primaryFixedDim = Primary80,
    onPrimaryFixed = Primary10,
    onPrimaryFixedVariant = Primary30,
    secondaryFixed = Secondary90,
    secondaryFixedDim = Secondary80,
    onSecondaryFixed = Secondary10,
    onSecondaryFixedVariant = Secondary30,
    tertiaryFixed = Tertiary90,
    tertiaryFixedDim = Tertiary80,
    onTertiaryFixed = Tertiary10,
    onTertiaryFixedVariant = Tertiary30,
)

/**
 * AMOLED variant: background, surface and dim go to true black; containers keep a small,
 * monotonic lift so cards, sheets and list groups still separate from the background.
 * Works on any dark scheme (coral or dynamic).
 */
fun ColorScheme.toPureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0909),
    surfaceContainer = Color(0xFF121010),
    surfaceContainerHigh = Color(0xFF1A1717),
    surfaceContainerHighest = Color(0xFF231F1F),
    surfaceBright = Color(0xFF2A2626),
)
