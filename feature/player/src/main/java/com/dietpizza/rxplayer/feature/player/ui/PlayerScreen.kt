package com.dietpizza.rxplayer.feature.player.ui

import com.dietpizza.rxplayer.feature.player.AppThemed
import com.dietpizza.rxplayer.core.designsystem.icon.RxIcons
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.compose.ContentFrame
import com.dietpizza.rxplayer.core.player.PlaybackController
import com.dietpizza.rxplayer.core.player.PlayerError
import com.dietpizza.rxplayer.core.player.ResumePrompt
import com.dietpizza.rxplayer.core.player.gesture.PlayerGestureMachine
import com.dietpizza.rxplayer.core.player.zoom.ZoomSnap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val AUTO_HIDE_MS = 3_000L
private const val LOCK_BUTTON_HIDE_MS = 2_500L
private const val BUFFERING_INDICATOR_DELAY_MS = 350L
private const val TOAST_MS = 900L

/**
 * The full player (plan §6) as one composable that doesn't assume it owns the activity: the
 * video, gestures, controls, sheets and overlays. Activity concerns (PiP, orientation, immersive
 * bars, the media session) go through [host].
 *
 * @param onMinimize called when a swipe-down passes the threshold and is released (plan §4.1).
 *   With [animateExit] the video first shrinks and fades out (standalone player → `finish()`);
 *   without it, it's called right at release with [state] still holding the drag offset, so a
 *   host (the M4b viewer) can morph from there and then reset [FullPlayerState.dismissOffsetPx].
 * @param onBack the back button in the top bar (and the error/resume "close").
 * @param externalSurface the host draws the video itself, below this composable (the viewer's
 *   single hoisted surface, plan §4.1): no ContentFrame and no black background here. The host
 *   applies [FullPlayerState.scaleX]/[FullPlayerState.scaleY] and the swipe-down transform.
 */
@Composable
fun FullPlayer(
    controller: PlaybackController,
    host: FullPlayerHost,
    onMinimize: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    state: FullPlayerState = rememberFullPlayerState(),
    animateExit: Boolean = true,
    externalSurface: Boolean = false,
) {
    val ui = state
    val settings by controller.settings.collectAsStateWithLifecycle()
    val playWhenReady by controller.playWhenReady.collectAsStateWithLifecycle()
    val resumePrompt by controller.resumePrompt.collectAsStateWithLifecycle()
    val error by controller.error.collectAsStateWithLifecycle()
    val inPip = host.isInPip

    val scope = rememberCoroutineScope()
    val handler = remember(controller) { PlayerGestureHandler(controller, ui, scope) }
    val viewConfiguration = LocalViewConfiguration.current
    val density = LocalDensity.current
    val machine = remember(handler) {
        PlayerGestureMachine(
            sink = handler,
            touchSlopPx = viewConfiguration.touchSlop,
            doubleTapSlopPx = with(density) { 100.dp.toPx() },
        )
    }
    val motion = MaterialTheme.motionScheme
    val spatial = motion.defaultSpatialSpec<Float>()
    val currentOnMinimize by rememberUpdatedState(onMinimize)
    SideEffect {
        handler.settings = settings
        handler.spatialSpec = spatial
        handler.onDismiss = {
            if (animateExit) {
                scope.launch {
                    val from = ui.dismissOffsetPx
                    animate(from, from + handler.heightPx * 0.35f, animationSpec = motion.fastSpatialSpec()) { v, _ ->
                        ui.dismissOffsetPx = v
                    }
                    currentOnMinimize()
                }
            } else {
                currentOnMinimize()
            }
        }
        // Double tap always seeks on the sides; the play/pause middle zone follows the setting.
        machine.doubleTapEnabled = !ui.locked
        machine.longPressEnabled = settings.longPressSpeedEnabled && !ui.locked
        machine.verticalDownEnabled = settings.swipeDownToClose && !ui.locked
        machine.pinchEnabled = settings.pinchToZoom && !ui.locked
    }

    // Re-apply aspect/zoom whenever the container or the video size changes (rotation, new item).
    val videoSize by controller.videoSize.collectAsStateWithLifecycle()
    LaunchedEffect(videoSize) { handler.relayout() }

    // Auto-hide after 3 s of no interaction while playing; show them when playback ends.
    val ended by controller.isEnded.collectAsStateWithLifecycle()
    LaunchedEffect(ended) { if (ended && !ui.locked) ui.showControls(true) }
    val blocking = resumePrompt != null || error?.fatal == true || ended || ui.sheet != PlayerSheet.None
    LaunchedEffect(ui.controlsVisible, ui.interaction, playWhenReady, ui.isDragging, blocking) {
        if (ui.controlsVisible && playWhenReady && !ui.isDragging && !blocking) {
            delay(AUTO_HIDE_MS)
            ui.showControls(false)
        }
    }
    LaunchedEffect(ui.lockRevealKey) {
        if (ui.lockButtonVisible) {
            delay(LOCK_BUTTON_HIDE_MS)
            ui.lockButtonVisible = false
        }
    }
    val chromeVisible = ui.controlsVisible && !ui.locked && !inPip && !ui.isDismissDragging
    LaunchedEffect(chromeVisible, inPip) { if (!inPip) host.setSystemBarsVisible(chromeVisible) }
    LaunchedEffect(inPip) { if (inPip) ui.sheet = PlayerSheet.None }

    BackHandler(enabled = ui.locked && !inPip) { ui.revealLockButton() }

    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val view = LocalView.current
    val screenshot: () -> Unit = {
        ui.sheet = PlayerSheet.None
        scope.launch { Screenshots.captureAndOffer(context, view, controller, snackbar) }
    }

    Box(
        modifier
            .fillMaxSize()
            .then(if (externalSurface) Modifier else Modifier.background(Color.Black))
            .onSizeChanged {
                handler.widthPx = it.width.toFloat()
                handler.heightPx = it.height.toFloat()
                handler.relayout()
            }
            .then(if (inPip) Modifier else Modifier.playerGestures(machine)),
    ) {
        // Video + subtitles: follow the swipe-down drag (translate + slight shrink + fade).
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val h = size.height
                    val f = if (h > 0f) (ui.dismissOffsetPx / h).coerceIn(0f, 1f) else 0f
                    translationY = ui.dismissOffsetPx
                    val s = 1f - DISMISS_SHRINK * f
                    scaleX = s
                    scaleY = s
                    alpha = 1f - DISMISS_FADE * f
                },
        ) {
            if (!externalSurface) {
                ContentFrame(
                    player = controller.player,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = ui.scaleX
                            scaleY = ui.scaleY
                        },
                    contentScale = ContentScale.Fit,
                )
            }
            SubtitleOverlay(
                controller = controller,
                liftForControls = chromeVisible,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (!inPip) {
            BufferingIndicator(controller, ui, Modifier.align(Alignment.Center))
            SwipeSeekOverlay(controller, ui, Modifier.align(Alignment.Center))
            DoubleTapPop(ui.popKey, ui.popKind, ui.popSeconds)
            SpeedBoostPill(controller, Modifier.align(Alignment.TopCenter).padding(top = 72.dp))
            ZoomIndicator(ui, Modifier.align(Alignment.TopCenter).padding(top = 72.dp))
            CenterToast(ui, Modifier.align(Alignment.Center))

            AnimatedVisibility(
                visible = chromeVisible && !ui.swipeSeeking && !ui.pinching,
                enter = fadeIn(motion.defaultEffectsSpec()),
                exit = fadeOut(motion.defaultEffectsSpec()),
            ) {
                PlayerControls(
                    controller = controller,
                    ui = ui,
                    host = host,
                    onCycleAspect = { handler.cycleAspect(); ui.bump() },
                    onBack = onBack,
                )
            }
            LockOverlay(ui, Modifier.align(Alignment.BottomStart))

            error?.let { err ->
                if (err.fatal) {
                    ErrorPanel(err, controller, onBack, Modifier.align(Alignment.Center))
                } else {
                    WarningBanner(err, onDismiss = controller::dismissError, Modifier.align(Alignment.BottomCenter))
                }
            }
            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
            ) { data -> ScreenshotSnackbar(data) }
        }
    }

    if (!inPip) AppThemed {
        when (ui.sheet) {
            PlayerSheet.None -> Unit
            PlayerSheet.Audio -> AudioTrackSheet(controller, onDismiss = { ui.sheet = PlayerSheet.None; ui.bump() })
            PlayerSheet.Subtitles -> SubtitleSheet(
                controller = controller,
                onLoadFile = { ui.sheet = PlayerSheet.None; host.pickSubtitleFile() },
                onDismiss = { ui.sheet = PlayerSheet.None; ui.bump() },
            )
            PlayerSheet.More -> MoreSheet(
                controller = controller,
                ui = ui,
                host = host,
                onAspect = handler::setAspect,
                onScreenshot = screenshot,
                onDismiss = { ui.sheet = PlayerSheet.None; ui.bump() },
            )
        }
        resumePrompt?.let { prompt -> ResumeDialog(prompt, controller, onBack) }
    }
}

internal const val DISMISS_SHRINK = 0.2f
internal const val DISMISS_FADE = 0.6f

/** "125%" while pinching. Only this leaf recomposes, and only when the integer changes. */
@Composable
private fun ZoomIndicator(ui: FullPlayerState, modifier: Modifier) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = ui.pinching,
        modifier = modifier,
        enter = fadeIn(motion.fastEffectsSpec()),
        exit = fadeOut(motion.defaultEffectsSpec()),
    ) {
        val percent by remember { derivedStateOf { ZoomSnap.percent(ui.scaleX) } }
        Pill("$percent%")
    }
}

/** Brief centred label (aspect mode). */
@Composable
private fun CenterToast(ui: FullPlayerState, modifier: Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(ui.toastKey) {
        if (ui.toastKey == 0) return@LaunchedEffect
        visible = true
        delay(TOAST_MS)
        visible = false
    }
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(motion.fastEffectsSpec()),
        exit = fadeOut(motion.defaultEffectsSpec()),
    ) {
        Pill(ui.toastText, large = true)
    }
}

@Composable
internal fun Pill(text: String, large: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.6f),
        contentColor = Color.White,
    ) {
        Text(
            text,
            style = if (large) MaterialTheme.typography.titleLargeEmphasized else MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = if (large) 24.dp else 16.dp, vertical = if (large) 12.dp else 8.dp),
        )
    }
}

/** While locked: only an unlock button, faded in by a tap (plan §6.3). */
@Composable
private fun LockOverlay(ui: FullPlayerState, modifier: Modifier) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = ui.locked && ui.lockButtonVisible,
        modifier = modifier,
        enter = fadeIn(motion.defaultEffectsSpec()),
        exit = fadeOut(motion.defaultEffectsSpec()),
    ) {
        Box(Modifier.controlsInsets().padding(start = 16.dp, bottom = 24.dp)) {
            FilledTonalIconButton(
                onClick = { ui.unlock() },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
            ) {
                Icon(RxIcons.Lock, contentDescription = "Unlock controls")
            }
        }
    }
}

@Composable
private fun BufferingIndicator(controller: PlaybackController, ui: FullPlayerState, modifier: Modifier) {
    val buffering by controller.isBuffering.collectAsStateWithLifecycle()
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(buffering, ui.isDragging) {
        show = false
        if (buffering && !ui.isDragging) {
            delay(BUFFERING_INDICATOR_DELAY_MS)
            show = true
        }
    }
    SideEffect { ui.bufferingShown = show }
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = show,
        modifier = modifier,
        enter = fadeIn(motion.defaultEffectsSpec()),
        exit = fadeOut(motion.fastEffectsSpec()),
    ) {
        LoadingIndicator(Modifier.size(72.dp))
    }
}

/** `+0:42 / 12:31`: delta, then the target position. Only this leaf reads the drag target. */
@Composable
private fun SwipeSeekOverlay(controller: PlaybackController, ui: FullPlayerState, modifier: Modifier) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = ui.swipeSeeking,
        modifier = modifier,
        enter = fadeIn(motion.fastEffectsSpec()),
        exit = fadeOut(motion.fastEffectsSpec()),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.Black.copy(alpha = 0.6f),
            contentColor = Color.White,
        ) {
            Column(
                Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = formatDelta(ui.swipeTargetMs - ui.swipeStartMs) + " / " + formatTime(ui.swipeTargetMs),
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                )
                val duration by controller.durationMs.collectAsStateWithLifecycle()
                Text(
                    text = formatTime(duration),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/** Pops after a double tap: play/pause in the centre, ⏪/⏩ with the seconds on that side. */
@Composable
internal fun BoxScope.DoubleTapPop(key: Int, kind: PopKind, seconds: Int) {
    if (key == 0) return
    val scale = remember { Animatable(0.6f) }
    val alpha = remember { Animatable(0f) }
    val motion = MaterialTheme.motionScheme
    LaunchedEffect(key) {
        launch {
            scale.snapTo(0.6f)
            scale.animateTo(1f, motion.fastSpatialSpec())
        }
        alpha.snapTo(1f)
        delay(550)
        alpha.animateTo(0f, motion.defaultEffectsSpec())
    }
    val alignment = when (kind) {
        PopKind.Back -> BiasAlignment(-0.6f, 0f)
        PopKind.Forward -> BiasAlignment(0.6f, 0f)
        else -> Alignment.Center
    }
    Surface(
        modifier = Modifier
            .align(alignment)
            .size(96.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            },
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = Color.White,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(
                imageVector = when (kind) {
                    PopKind.Play -> RxIcons.PlayArrow
                    PopKind.Pause -> RxIcons.Pause
                    PopKind.Back -> RxIcons.FastRewind
                    PopKind.Forward -> RxIcons.FastForward
                },
                contentDescription = null,
                modifier = Modifier.size(if (kind.isSeek) 40.dp else 56.dp),
            )
            if (kind.isSeek) {
                Text(
                    (if (kind == PopKind.Back) "-" else "+") + seconds + "s",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun SpeedBoostPill(controller: PlaybackController, modifier: Modifier) {
    val boosted by controller.isSpeedBoosted.collectAsStateWithLifecycle()
    val speed by controller.speed.collectAsStateWithLifecycle()
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = boosted,
        modifier = modifier,
        enter = fadeIn(motion.fastEffectsSpec()),
        exit = fadeOut(motion.fastEffectsSpec()),
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.6f),
            contentColor = Color.White,
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val label = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()
                Text("${label}×", style = MaterialTheme.typography.labelLarge)
                Icon(RxIcons.FastForward, contentDescription = null, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ResumeDialog(prompt: ResumePrompt, controller: PlaybackController, onBack: () -> Unit) {
    AlertDialog(
        onDismissRequest = onBack,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text("Resume playback?") },
        text = {
            val title = controller.nowPlaying.collectAsStateWithLifecycle().value?.title.orEmpty()
            Text(title, maxLines = 2)
        },
        confirmButton = {
            FilledTonalButton(onClick = { controller.resolveResume(resume = true) }) {
                Text("Resume from ${formatTime(prompt.positionMs)}")
            }
        },
        dismissButton = {
            TextButton(onClick = { controller.resolveResume(resume = false) }) { Text("Start over") }
        },
    )
}

@Composable
private fun ErrorPanel(error: PlayerError, controller: PlaybackController, onBack: () -> Unit, modifier: Modifier) {
    val nowPlaying by controller.nowPlaying.collectAsStateWithLifecycle()
    Surface(
        modifier = modifier
            .padding(24.dp)
            .widthIn(max = 480.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                RxIcons.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp),
            )
            Text(error.title, style = MaterialTheme.typography.titleLargeEmphasized, textAlign = TextAlign.Center)
            Text(
                error.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onBack) { Text("Close") }
                if (nowPlaying?.hasNext == true) {
                    FilledTonalButton(onClick = controller::next) { Text("Play next") }
                }
            }
        }
    }
}

@Composable
private fun WarningBanner(error: PlayerError, onDismiss: () -> Unit, modifier: Modifier) {
    LaunchedEffect(error) {
        delay(6_000)
        onDismiss()
    }
    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 120.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        onClick = onDismiss,
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(RxIcons.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Column {
                Text(error.title, style = MaterialTheme.typography.titleSmall)
                Text(error.message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
