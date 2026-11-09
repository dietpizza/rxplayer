package com.dietpizza.rxplayer.feature.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dietpizza.rxplayer.core.player.zoom.AspectMode

/** Which bottom sheet is open over the full player. */
internal enum class PlayerSheet { None, Audio, Subtitles, More }

/**
 * Screen-local state of the full player (things the controller doesn't own): controls
 * visibility, lock, aspect/zoom, swipe-down, sheets. Hoistable so a host (the M4b viewer) can
 * observe the swipe-down drag while it morphs: see [dismissOffsetPx] / [isDismissDragging].
 */
@Stable
class FullPlayerState internal constructor() {
    // region public, read-only for hosts

    /** Current swipe-down offset of the video, px (0 at rest). Snapshot state. */
    var dismissOffsetPx by mutableFloatStateOf(0f)
        internal set

    /** A swipe-down drag is in progress (or settling back). */
    var isDismissDragging by mutableStateOf(false)
        internal set

    /** Controls are locked (only the unlock button can be revealed). */
    var locked by mutableStateOf(false)
        internal set
    // endregion

    internal var controlsVisible by mutableStateOf(true)
        private set

    /** Bumped on every interaction; restarts the auto-hide timer. */
    internal var interaction by mutableIntStateOf(0)
        private set

    internal var seekBarDragging by mutableStateOf(false)
    internal var swipeSeeking by mutableStateOf(false)

    /** Read only inside the overlay leaf; written per drag frame. */
    internal var swipeStartMs by mutableLongStateOf(0L)
    internal var swipeTargetMs by mutableLongStateOf(0L)

    /** Pop after a double tap: what it shows, and a counter that restarts the animation. */
    internal var popKind by mutableStateOf(PopKind.Play)
        private set

    /** Seconds shown by a seek pop; repeated double taps on the same side add up. */
    internal var popSeconds by mutableIntStateOf(0)
        private set
    private var lastPopAtMs = 0L
    internal var popKey by mutableIntStateOf(0)
        private set

    internal var showRemaining by mutableStateOf(false)

    /** Lock button revealed by a tap while locked; bumped to restart its fade-out timer. */
    internal var lockRevealKey by mutableIntStateOf(0)
        private set
    internal var lockButtonVisible by mutableStateOf(false)

    // region aspect / zoom

    internal var aspectMode by mutableStateOf(AspectMode.Fit)

    /** Free pinch zoom relative to Fit, or 0 when [aspectMode] applies. */
    internal var customZoom by mutableFloatStateOf(0f)

    /** Displayed scale of the video relative to Fit. Read in graphicsLayer only. */
    internal var scaleX by mutableFloatStateOf(1f)
    internal var scaleY by mutableFloatStateOf(1f)

    internal var pinching by mutableStateOf(false)
    // endregion

    /** Short centred label ("Crop", "125%"), restarted by [toastKey]. */
    internal var toastText by mutableStateOf("")
        private set
    internal var toastKey by mutableIntStateOf(0)
        private set

    internal var sheet by mutableStateOf(PlayerSheet.None)

    internal val isDragging: Boolean get() = seekBarDragging || swipeSeeking || isDismissDragging || pinching

    private var visibilityBeforeTap = true

    internal fun bump() {
        interaction++
    }

    internal fun showControls(visible: Boolean) {
        controlsVisible = visible
        bump()
    }

    internal fun toggleControls() {
        visibilityBeforeTap = controlsVisible
        showControls(!controlsVisible)
    }

    /** The first tap of a double tap already toggled the controls: put them back. */
    internal fun undoTapToggle() {
        controlsVisible = visibilityBeforeTap
        bump()
    }

    internal fun pop(kind: PopKind, seconds: Int = 0) {
        val now = android.os.SystemClock.uptimeMillis()
        val chained = kind == popKind && kind.isSeek && now - lastPopAtMs < POP_CHAIN_MS
        popSeconds = if (chained) popSeconds + seconds else seconds
        popKind = kind
        lastPopAtMs = now
        popKey++
    }

    internal fun toast(text: String) {
        toastText = text
        toastKey++
    }

    internal fun lock() {
        locked = true
        controlsVisible = false
        sheet = PlayerSheet.None
        revealLockButton()
    }

    internal fun unlock() {
        locked = false
        lockButtonVisible = false
        showControls(true)
    }

    internal fun revealLockButton() {
        lockButtonVisible = true
        lockRevealKey++
    }
}

/** Remembers a [FullPlayerState] for [FullPlayer]. */
@Composable
fun rememberFullPlayerState(): FullPlayerState = remember { FullPlayerState() }

internal enum class PopKind(val isSeek: Boolean) { Play(false), Pause(false), Back(true), Forward(true) }

private const val POP_CHAIN_MS = 1_200L
