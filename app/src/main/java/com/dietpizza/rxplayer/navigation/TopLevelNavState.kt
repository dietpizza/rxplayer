package com.dietpizza.rxplayer.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.dietpizza.rxplayer.R

enum class TopLevelDestination(
    val rootKey: NavKey,
    @StringRes val label: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Gallery(GalleryKey, R.string.tab_gallery, Icons.Rounded.PhotoLibrary, Icons.Outlined.PhotoLibrary),
    Videos(VideosKey, R.string.tab_videos, Icons.Rounded.VideoLibrary, Icons.Outlined.VideoLibrary),
    Network(NetworkKey, R.string.tab_network, Icons.Rounded.Lan, Icons.Outlined.Lan),
    ;

    companion object {
        val Start = Gallery
    }
}

/**
 * One back stack per tab plus the selected tab.
 *
 * The visible stack is the start tab's stack followed by the current tab's stack (when they
 * differ), so back from another tab's root lands on Gallery, and back from Gallery exits.
 */
@Stable
class TopLevelNavState(
    val stacks: Map<TopLevelDestination, NavBackStack<NavKey>>,
    initialTab: TopLevelDestination,
    private val onTabChanged: (TopLevelDestination) -> Unit,
) {
    var currentTab by mutableStateOf(initialTab)
        private set

    /** Tabs whose stacks are currently visible, bottom first. */
    val visibleTabs: List<TopLevelDestination>
        get() = if (currentTab == TopLevelDestination.Start) {
            listOf(TopLevelDestination.Start)
        } else {
            listOf(TopLevelDestination.Start, currentTab)
        }

    /** Switches tabs; re-selecting the current tab pops it to its root. */
    fun select(tab: TopLevelDestination) {
        if (tab == currentTab) {
            val stack = stacks.getValue(tab)
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
        } else {
            setTab(tab)
        }
    }

    /** Pushes onto the current tab's stack. */
    fun push(key: NavKey) {
        stacks.getValue(currentTab).add(key)
    }

    /** Pops within the current tab, or falls back to the start tab from another tab's root. */
    fun pop() {
        val stack = stacks.getValue(currentTab)
        when {
            stack.size > 1 -> stack.removeAt(stack.lastIndex)
            currentTab != TopLevelDestination.Start -> setTab(TopLevelDestination.Start)
        }
    }

    private fun setTab(tab: TopLevelDestination) {
        currentTab = tab
        onTabChanged(tab)
    }
}

@Composable
fun rememberTopLevelNavState(): TopLevelNavState {
    val gallery = rememberNavBackStack(GalleryKey)
    val videos = rememberNavBackStack(VideosKey)
    val network = rememberNavBackStack(NetworkKey)
    var savedTab by rememberSaveable { mutableStateOf(TopLevelDestination.Start) }
    return remember(gallery, videos, network) {
        TopLevelNavState(
            stacks = mapOf(
                TopLevelDestination.Gallery to gallery,
                TopLevelDestination.Videos to videos,
                TopLevelDestination.Network to network,
            ),
            initialTab = savedTab,
            onTabChanged = { savedTab = it },
        )
    }
}
