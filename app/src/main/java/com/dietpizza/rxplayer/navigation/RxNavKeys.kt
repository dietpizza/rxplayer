package com.dietpizza.rxplayer.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Root of the app-level stack: the tabbed shell (navigation suite + per-tab stacks). */
@Serializable
data object ShellKey : NavKey

/** Gallery tab root (albums). */
@Serializable
data object GalleryKey : NavKey

/** Videos tab root (MX-style folders). */
@Serializable
data object VideosKey : NavKey

/** Network tab root (saved servers). */
@Serializable
data object NetworkKey : NavKey

/** Settings, pushed full screen above the shell so the navigation suite is hidden. */
@Serializable
data object SettingsKey : NavKey
