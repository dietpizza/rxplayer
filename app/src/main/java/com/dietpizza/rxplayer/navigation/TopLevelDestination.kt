package com.dietpizza.rxplayer.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.VideoLibrary
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
    Gallery(R.string.tab_gallery, Icons.Rounded.PhotoLibrary, Icons.Outlined.PhotoLibrary),
    Videos(R.string.tab_videos, Icons.Rounded.VideoLibrary, Icons.Outlined.VideoLibrary),
    Network(R.string.tab_network, Icons.Rounded.Lan, Icons.Outlined.Lan),
    ;

    companion object {
        val Start = Gallery
    }
}
