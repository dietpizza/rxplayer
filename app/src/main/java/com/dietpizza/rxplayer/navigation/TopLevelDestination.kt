package com.dietpizza.rxplayer.navigation

import com.dietpizza.rxplayer.core.designsystem.icon.RxIcons
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.dietpizza.rxplayer.R

/**
 * The bottom-nav tabs. Tabs are plain state inside MainActivity (an instant content swap);
 * every deeper screen (Settings, album, folder, player…) is its own Activity — see plan §3.2.
 */
enum class TopLevelDestination(
    @StringRes val label: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Gallery(R.string.tab_gallery, RxIcons.GalleryTabFilled, RxIcons.GalleryTab),
    Videos(R.string.tab_videos, RxIcons.VideosTabFilled, RxIcons.VideosTab),
    Network(R.string.tab_network, RxIcons.NetworkTabFilled, RxIcons.NetworkTab),
    ;

    /** UI-test / baseline-profile selector ("tab_gallery", …). */
    val testTag: String get() = "tab_" + name.lowercase()

    companion object {
        val Start = Gallery
    }
}
