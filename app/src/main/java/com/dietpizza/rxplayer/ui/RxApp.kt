package com.dietpizza.rxplayer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import com.dietpizza.rxplayer.feature.gallery.GalleryScreen
import com.dietpizza.rxplayer.feature.network.NetworkScreen
import com.dietpizza.rxplayer.feature.onboarding.OnboardingScreen
import com.dietpizza.rxplayer.feature.onboarding.hasRequiredPermissions
import com.dietpizza.rxplayer.feature.settings.SettingsScreen
import com.dietpizza.rxplayer.feature.videos.VideosScreen
import com.dietpizza.rxplayer.navigation.GalleryKey
import com.dietpizza.rxplayer.navigation.NetworkKey
import com.dietpizza.rxplayer.navigation.SettingsKey
import com.dietpizza.rxplayer.navigation.ShellKey
import com.dietpizza.rxplayer.navigation.TopLevelDestination
import com.dietpizza.rxplayer.navigation.TopLevelNavState
import com.dietpizza.rxplayer.navigation.VideosKey
import com.dietpizza.rxplayer.navigation.rememberTopLevelNavState

/**
 * App root: permission gate, then the navigation shell.
 *
 * Permissions are re-checked on every ON_RESUME, so revoking all-files access in system
 * settings sends the user back to onboarding when they return.
 */
@Composable
fun RxApp() {
    val context = LocalContext.current
    var permitted by remember { mutableStateOf(hasRequiredPermissions(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permitted = hasRequiredPermissions(context)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        val effects = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = permitted,
            transitionSpec = { fadeIn(effects.slowEffectsSpec()) togetherWith fadeOut(effects.fastEffectsSpec()) },
            label = "permissionGate",
        ) { granted ->
            if (granted) {
                RxNavHost()
            } else {
                OnboardingScreen(
                    onFinished = { permitted = hasRequiredPermissions(context) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Two-level Navigation 3 host.
 *
 * - Root [NavDisplay]: `[ShellKey, SettingsKey?]`. Full-screen destinations sit above the shell,
 *   so the navigation suite is hidden there and back/predictive back reveals it again.
 * - Shell: [NavigationSuiteScaffold] around a tab [NavDisplay] fed with the start tab's entries
 *   plus the current tab's entries. Each tab's entries are decorated by their own decorators
 *   (hoisted here, outside the root display) so a tab's saved state and ViewModels survive
 *   while it's off screen and while Settings covers the shell.
 */
@Composable
private fun RxNavHost() {
    val rootStack = rememberNavBackStack(ShellKey)
    val tabs = rememberTopLevelNavState()

    val openSettings: () -> Unit = {
        if (rootStack.lastOrNull() != SettingsKey) rootStack.add(SettingsKey)
    }
    val popRoot: () -> Unit = {
        if (rootStack.size > 1) rootStack.removeAt(rootStack.lastIndex)
    }

    val tabEntryProvider = entryProvider<NavKey> {
        entry<GalleryKey>(metadata = tabMetadata(TopLevelDestination.Gallery)) {
            GalleryScreen(onOpenSettings = openSettings)
        }
        entry<VideosKey>(metadata = tabMetadata(TopLevelDestination.Videos)) {
            VideosScreen(onOpenSettings = openSettings)
        }
        entry<NetworkKey>(metadata = tabMetadata(TopLevelDestination.Network)) {
            NetworkScreen(onOpenSettings = openSettings)
        }
    }
    val tabEntries: Map<TopLevelDestination, List<NavEntry<NavKey>>> =
        TopLevelDestination.entries.associateWith { tab ->
            key(tab) {
                rememberDecoratedNavEntries(
                    backStack = tabs.stacks.getValue(tab),
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = tabEntryProvider,
                )
            }
        }

    val rootTransitions = RootTransitions(MaterialTheme.motionScheme)
    NavDisplay(
        backStack = rootStack,
        onBack = popRoot,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = { rootTransitions.push() },
        popTransitionSpec = { rootTransitions.pop() },
        predictivePopTransitionSpec = { rootTransitions.predictivePop() },
        entryProvider = entryProvider {
            entry<ShellKey> {
                RxShell(
                    tabs = tabs,
                    entries = tabs.visibleTabs.flatMap { tabEntries.getValue(it) },
                )
            }
            entry<SettingsKey> {
                SettingsScreen(onBack = popRoot)
            }
        },
    )
}

private fun tabMetadata(tab: TopLevelDestination): Map<String, Any> = mapOf(TabMetadataKey to tab.name)

@Composable
private fun RxShell(
    tabs: TopLevelNavState,
    entries: List<NavEntry<NavKey>>,
    modifier: Modifier = Modifier,
) {
    val suiteType = rxNavigationSuiteType()
    val tabTransitions = TabTransitions(MaterialTheme.motionScheme)
    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteType = suiteType,
        navigationItems = {
            TopLevelDestination.entries.forEach { tab ->
                val selected = tab == tabs.currentTab
                NavigationSuiteItem(
                    selected = selected,
                    onClick = { tabs.select(tab) },
                    icon = {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(tab.label)) },
                    navigationSuiteType = suiteType,
                )
            }
        },
    ) {
        NavDisplay(
            entries = entries,
            onBack = tabs::pop,
            transitionSpec = { tabTransitions.forward(this) },
            popTransitionSpec = { tabTransitions.backward(this) },
            predictivePopTransitionSpec = { tabTransitions.backward(this) },
        )
    }
}

/** Expressive short bar on compact widths; a (collapsed) wide rail from medium width up, incl. phone landscape. */
@Composable
private fun rxNavigationSuiteType(): NavigationSuiteType {
    val sizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    return if (sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)) {
        NavigationSuiteType.WideNavigationRailCollapsed
    } else {
        NavigationSuiteType.ShortNavigationBarCompact
    }
}
