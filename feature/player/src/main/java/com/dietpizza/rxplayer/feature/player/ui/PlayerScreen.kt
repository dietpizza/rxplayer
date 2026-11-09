package com.dietpizza.rxplayer.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val AUTO_HIDE_MS = 3_000L
private const val BUFFERING_INDICATOR_DELAY_MS = 350L

@Composable
internal fun PlayerScreen(
    controller: PlaybackController,
    rotationLocked: Boolean,
    onToggleRotationLock: () -> Unit,
    onControlsVisibleChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val ui = remember { PlayerUiState() }
    val settings by controller.settings.collectAsStateWithLifecycle()
    val playWhenReady by controller.playWhenReady.collectAsStateWithLifecycle()
    val resumePrompt by controller.resumePrompt.collectAsStateWithLifecycle()
    val error by controller.error.collectAsStateWithLifecycle()

    val handler = remember(controller) { PlayerGestureHandler(controller, ui) }
    val viewConfiguration = LocalViewConfiguration.current
    val density = LocalDensity.current
    val machine = remember(handler) {
        PlayerGestureMachine(
            sink = handler,
            touchSlopPx = viewConfiguration.touchSlop,
            doubleTapSlopPx = with(density) { 100.dp.toPx() },
        )
    }
    SideEffect {
        handler.settings = settings
        machine.doubleTapEnabled = settings.doubleTapPlayPause
        machine.longPressEnabled = settings.longPressSpeedEnabled
        // Swipe-down (minimise) and pinch (zoom) arrive in M3; until then those gestures are inert.
        machine.verticalDownEnabled = false
        machine.pinchEnabled = false
    }

    // Auto-hide after 3 s of no interaction while playing; show them when playback ends.
    val ended by controller.isEnded.collectAsStateWithLifecycle()
    LaunchedEffect(ended) { if (ended) ui.showControls(true) }
    val blocking = resumePrompt != null || error?.fatal == true || ended
    LaunchedEffect(ui.controlsVisible, ui.interaction, playWhenReady, ui.isDragging, blocking) {
        if (ui.controlsVisible && playWhenReady && !ui.isDragging && !blocking) {
            delay(AUTO_HIDE_MS)
            ui.showControls(false)
        }
    }
    LaunchedEffect(ui.controlsVisible) { onControlsVisibleChange(ui.controlsVisible) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { handler.widthPx = it.width.toFloat() }
            .playerGestures(machine),
    ) {
        ContentFrame(
            player = controller.player,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )

        BufferingIndicator(controller, ui, Modifier.align(Alignment.Center))
        SwipeSeekOverlay(controller, ui, Modifier.align(Alignment.Center))
        DoubleTapPop(ui, Modifier.align(Alignment.Center))
        SpeedBoostPill(controller, Modifier.align(Alignment.TopCenter).padding(top = 72.dp))

        val motion = MaterialTheme.motionScheme
        AnimatedVisibility(
            visible = ui.controlsVisible && !ui.swipeSeeking,
            enter = fadeIn(motion.defaultEffectsSpec()),
            exit = fadeOut(motion.defaultEffectsSpec()),
        ) {
            PlayerControls(
                controller = controller,
                ui = ui,
                rotationLocked = rotationLocked,
                onToggleRotationLock = onToggleRotationLock,
                onBack = onBack,
            )
        }
        // Mute is first-class and always reachable (plan §3.3, §6.3): it stays on screen,
        // dimmed, while the rest of the controls are hidden.
        AlwaysOnMute(controller, ui, Modifier.align(Alignment.TopEnd))

        error?.let { err ->
            if (err.fatal) {
                ErrorPanel(err, controller, onBack, Modifier.align(Alignment.Center))
            } else {
                WarningBanner(err, onDismiss = controller::dismissError, Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    resumePrompt?.let { prompt -> ResumeDialog(prompt, controller, onBack) }
}

@Composable
private fun BufferingIndicator(controller: PlaybackController, ui: PlayerUiState, modifier: Modifier) {
    val buffering by controller.isBuffering.collectAsStateWithLifecycle()
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(buffering, ui.isDragging) {
        show = false
        if (buffering && !ui.isDragging) {
            delay(BUFFERING_INDICATOR_DELAY_MS)
            show = true
        }
    }
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
private fun SwipeSeekOverlay(controller: PlaybackController, ui: PlayerUiState, modifier: Modifier) {
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

/** Play/pause icon that pops in the centre after a double tap. */
@Composable
private fun DoubleTapPop(ui: PlayerUiState, modifier: Modifier) {
    val key = ui.popKey
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
        delay(450)
        alpha.animateTo(0f, motion.defaultEffectsSpec())
    }
    Surface(
        modifier = modifier
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
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (ui.popShowsPlay) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
            )
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
                Icon(Icons.Rounded.FastForward, contentDescription = null, Modifier.size(18.dp))
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
                Icons.Rounded.ErrorOutline,
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
            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Column {
                Text(error.title, style = MaterialTheme.typography.titleSmall)
                Text(error.message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
