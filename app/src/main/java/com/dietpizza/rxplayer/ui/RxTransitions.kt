package com.dietpizza.rxplayer.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene

/** NavEntry metadata key holding the [com.dietpizza.rxplayer.navigation.TopLevelDestination] an entry belongs to. */
internal const val TabMetadataKey = "rx.tab"

private val Scene<NavKey>.tab: Any?
    get() = entries.lastOrNull()?.metadata?.get(TabMetadataKey)

/**
 * Transitions for the tab NavDisplay: fade-through when the tab changes, shared-axis X when
 * moving within a tab's own stack.
 */
internal class TabTransitions(private val motion: MotionScheme) {

    fun forward(scope: AnimatedContentTransitionScope<Scene<NavKey>>): ContentTransform =
        if (scope.initialState.tab != scope.targetState.tab) fadeThrough() else sharedAxisX(forward = true)

    fun backward(scope: AnimatedContentTransitionScope<Scene<NavKey>>): ContentTransform =
        if (scope.initialState.tab != scope.targetState.tab) fadeThrough() else sharedAxisX(forward = false)

    /** M3 fade-through: the outgoing screen fades out quickly, then the new one fades and scales in. */
    private fun fadeThrough(): ContentTransform =
        (
            fadeIn(tween(durationMillis = 210, delayMillis = 90, easing = LinearOutSlowInEasing)) +
                scaleIn(motion.defaultSpatialSpec(), initialScale = 0.92f)
            ) togetherWith fadeOut(tween(durationMillis = 90, easing = FastOutLinearInEasing))

    private fun sharedAxisX(forward: Boolean): ContentTransform {
        val sign = if (forward) 1 else -1
        val slide = motion.defaultSpatialSpec<IntOffset>()
        return (
            slideInHorizontally(slide) { sign * it / 10 } +
                fadeIn(tween(durationMillis = 210, delayMillis = 90, easing = LinearOutSlowInEasing))
            ) togetherWith (
            slideOutHorizontally(slide) { -sign * it / 10 } +
                fadeOut(tween(durationMillis = 90, easing = FastOutLinearInEasing))
            )
    }
}

/**
 * Transitions for the root NavDisplay (shell ↔ full-screen destinations such as Settings):
 * the new screen slides in over a parallaxed, dimmed shell; back reverses it, keeping the
 * leaving screen on top. Predictive back shrinks the leaving screen as it follows the gesture.
 */
internal class RootTransitions(private val motion: MotionScheme) {

    fun push(): ContentTransform {
        val slide = motion.defaultSpatialSpec<IntOffset>()
        return slideInHorizontally(slide) { it } togetherWith
            (slideOutHorizontally(slide) { -it / 4 } + fadeOut(motion.defaultEffectsSpec(), targetAlpha = 0.6f))
    }

    fun pop(): ContentTransform {
        val slide = motion.defaultSpatialSpec<IntOffset>()
        return (
            (slideInHorizontally(slide) { -it / 4 } + fadeIn(motion.defaultEffectsSpec(), initialAlpha = 0.6f))
                togetherWith slideOutHorizontally(slide) { it }
            ).apply { targetContentZIndex = -1f }
    }

    fun predictivePop(): ContentTransform {
        val duration = 350
        return (
            (
                slideInHorizontally(tween(duration)) { -it / 4 } +
                    fadeIn(tween(duration), initialAlpha = 0.6f)
                ) togetherWith (
                scaleOut(tween(duration), targetScale = 0.9f) +
                    slideOutHorizontally(tween(duration)) { it }
                )
            ).apply { targetContentZIndex = -1f }
    }
}
