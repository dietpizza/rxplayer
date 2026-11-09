package com.dietpizza.rxplayer.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.window.core.layout.WindowSizeClass
import com.dietpizza.rxplayer.feature.gallery.GalleryScreen
import com.dietpizza.rxplayer.feature.network.NetworkScreen
import com.dietpizza.rxplayer.feature.onboarding.OnboardingScreen
import com.dietpizza.rxplayer.feature.onboarding.hasRequiredPermissions
import com.dietpizza.rxplayer.feature.videos.VideosScreen
import com.dietpizza.rxplayer.navigation.TopLevelDestination

/**
 * MainActivity content: permission gate, then the tabbed shell.
 *
 * Permissions are re-checked on every ON_RESUME, so revoking all-files access in system
 * settings sends the user back to onboarding when they return.
 */
@Composable
fun RxApp(onOpenSettings: () -> Unit) {
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
                RxShell(onOpenSettings = onOpenSettings)
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
 * Bottom bar (or rail on wide windows) + the selected tab's content.
 *
 * Switching tabs is an instant swap with no transition (plan §9). Each tab's UI state (scroll
 * positions etc.) is kept in a [rememberSaveableStateHolder] so it survives switching away.
 * Back from a non-start tab returns to Gallery; back from Gallery leaves the app.
 */
@Composable
private fun RxShell(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    var currentTab by rememberSaveable { mutableStateOf(TopLevelDestination.Start) }
    val tabStates = rememberSaveableStateHolder()
    val suiteType = rxNavigationSuiteType()

    BackHandler(enabled = currentTab != TopLevelDestination.Start) {
        currentTab = TopLevelDestination.Start
    }

    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteType = suiteType,
        navigationItems = {
            TopLevelDestination.entries.forEach { tab ->
                val selected = tab == currentTab
                NavigationSuiteItem(
                    selected = selected,
                    onClick = { currentTab = tab },
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
        tabStates.SaveableStateProvider(currentTab.name) {
            when (currentTab) {
                TopLevelDestination.Gallery -> GalleryScreen(onOpenSettings = onOpenSettings)
                TopLevelDestination.Videos -> VideosScreen(onOpenSettings = onOpenSettings)
                TopLevelDestination.Network -> NetworkScreen(onOpenSettings = onOpenSettings)
            }
        }
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
