package com.dietpizza.rxplayer.feature.player.ui

import com.dietpizza.rxplayer.core.designsystem.icon.RxIcons
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dietpizza.rxplayer.core.player.PlaybackController

private val Scrim = Color.Black.copy(alpha = 0.6f)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Modifier.controlsInsets(): Modifier =
    // Ignoring visibility keeps the layout still when the system bars fade in/out with the controls.
    windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout))

/** The control set (plan §6.3). */
@Composable
internal fun PlayerControls(
    controller: PlaybackController,
    ui: FullPlayerState,
    host: FullPlayerHost,
    onCycleAspect: () -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // Scrims only at the edges; the middle stays clear and doesn't catch touches.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(160.dp)
                .background(Brush.verticalGradient(listOf(Scrim, Color.Transparent))),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(200.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Scrim))),
        )

        TopBar(controller, ui, onBack, Modifier.align(Alignment.TopCenter).controlsInsets())
        CenterControls(controller, ui, Modifier.align(Alignment.Center))
        BottomBar(
            controller = controller,
            ui = ui,
            rotationLocked = host.rotationLocked,
            onToggleRotationLock = { host.toggleRotationLock(); ui.bump() },
            onCycleAspect = onCycleAspect,
            modifier = Modifier.align(Alignment.BottomCenter).controlsInsets(),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopBar(controller: PlaybackController, ui: FullPlayerState, onBack: () -> Unit, modifier: Modifier) {
    val nowPlaying by controller.nowPlaying.collectAsStateWithLifecycle()
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp + MuteSlotWidth, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(IconButtonDefaults.mediumContainerSize())) {
            Icon(RxIcons.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Text(
            text = nowPlaying?.title.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .basicMarquee(initialDelayMillis = 1_500),
        )
        val tracks by controller.trackMenu.collectAsStateWithLifecycle()
        if (tracks.audio.size > 1) {
            TopIcon(RxIcons.Audiotrack, "Audio track") { ui.sheet = PlayerSheet.Audio; ui.bump() }
        }
        TopIcon(
            if (tracks.textOff) RxIcons.ClosedCaptionDisabled else RxIcons.ClosedCaption,
            "Subtitles",
        ) { ui.sheet = PlayerSheet.Subtitles; ui.bump() }
        TopIcon(RxIcons.MoreVert, "More") { ui.sheet = PlayerSheet.More; ui.bump() }
    }
}

@Composable
private fun TopIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(IconButtonDefaults.mediumContainerSize())) {
        Icon(icon, contentDescription = description, tint = Color.White)
    }
}

/** Reserved at the end of the top bar for [AlwaysOnMute]. */
private val MuteSlotWidth = 64.dp

/** Mute stays visible (dimmed) when controls hide: it's a first-class control (plan §3.3). */
@Composable
internal fun AlwaysOnMute(controller: PlaybackController, ui: FullPlayerState, modifier: Modifier) {
    val muted by controller.isMuted.collectAsStateWithLifecycle()
    val alpha by animateFloatAsState(
        targetValue = if (ui.controlsVisible) 1f else 0.45f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "muteAlpha",
    )
    Box(modifier.controlsInsets().padding(end = 4.dp, top = 4.dp)) {
        IconButton(
            onClick = { controller.toggleMute(); ui.bump() },
            modifier = Modifier
                .size(IconButtonDefaults.mediumContainerSize())
                .graphicsLayer { this.alpha = alpha },
        ) {
            Icon(
                imageVector = if (muted) RxIcons.VolumeOff else RxIcons.VolumeUp,
                contentDescription = if (muted) "Unmute" else "Mute",
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun CenterControls(controller: PlaybackController, ui: FullPlayerState, modifier: Modifier) {
    val nowPlaying by controller.nowPlaying.collectAsStateWithLifecycle()
    val settings by controller.settings.collectAsStateWithLifecycle()
    val playWhenReady by controller.playWhenReady.collectAsStateWithLifecycle()
    val ended by controller.isEnded.collectAsStateWithLifecycle()
    val playing = playWhenReady && !ended

    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ControlButton(RxIcons.SkipPrevious, "Previous") { controller.previous(); ui.bump() }
        ControlButton(seekBackIcon(settings.seekStepSeconds), "Back ${settings.seekStepSeconds} seconds") {
            controller.seekBack(); ui.bump()
        }
        val iconFade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        // Expressive shape-morphing toggle: round while paused, squircle while playing.
        FilledIconToggleButton(
            checked = playing,
            onCheckedChange = { controller.togglePlayPause(); ui.bump() },
            shapes = IconButtonDefaults.toggleableShapes(),
            modifier = Modifier.size(IconButtonDefaults.largeContainerSize()),
        ) {
            AnimatedContent(
                targetState = if (ended) PlayIcon.Replay else if (playing) PlayIcon.Pause else PlayIcon.Play,
                transitionSpec = {
fadeIn(iconFade) togetherWith fadeOut(iconFade)
                },
                label = "playPause",
            ) { icon ->
                Icon(
                    imageVector = icon.vector,
                    contentDescription = icon.label,
                    modifier = Modifier.size(IconButtonDefaults.largeIconSize),
                )
            }
        }
        ControlButton(seekForwardIcon(settings.seekStepSeconds), "Forward ${settings.seekStepSeconds} seconds") {
            controller.seekForward(); ui.bump()
        }
        ControlButton(RxIcons.SkipNext, "Next", enabled = nowPlaying?.hasNext == true) {
            controller.next(); ui.bump()
        }
    }
}

private enum class PlayIcon(val vector: ImageVector, val label: String) {
    Play(RxIcons.PlayArrow, "Play"),
    Pause(RxIcons.Pause, "Pause"),
    Replay(RxIcons.Replay, "Replay"),
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.38f),
            modifier = Modifier.size(32.dp),
        )
    }
}

private fun seekBackIcon(seconds: Int) = when (seconds) {
    5 -> RxIcons.Replay5
    10 -> RxIcons.Replay10
    30 -> RxIcons.Replay30
    else -> RxIcons.FastRewind
}

private fun seekForwardIcon(seconds: Int) = when (seconds) {
    5 -> RxIcons.Forward5
    10 -> RxIcons.Forward10
    30 -> RxIcons.Forward30
    else -> RxIcons.FastForward
}

@Composable
private fun BottomBar(
    controller: PlaybackController,
    ui: FullPlayerState,
    rotationLocked: Boolean,
    onToggleRotationLock: () -> Unit,
    onCycleAspect: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ElapsedTime(controller)
            Spacer(Modifier.weight(1f))
            DurationTime(controller, ui)
        }
        SeekBar(controller, ui)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { ui.lock() },
                modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
            ) {
                Icon(RxIcons.LockOpen, contentDescription = "Lock controls", tint = Color.White)
            }
            IconButton(
                onClick = onCycleAspect,
                modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
            ) {
                Icon(RxIcons.AspectRatio, contentDescription = "Aspect: ${ui.aspectMode.label}", tint = Color.White)
            }
            SpeedChip(controller, ui)
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = onToggleRotationLock,
                modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
            ) {
                Icon(
                    if (rotationLocked) RxIcons.ScreenLockRotation else RxIcons.ScreenRotation,
                    contentDescription = if (rotationLocked) "Unlock rotation" else "Lock rotation",
                    tint = Color.White,
                )
            }
        }
    }
}

/** Leaf: the only composable (with [SeekBar]) that recomposes on position ticks. */
@Composable
private fun ElapsedTime(controller: PlaybackController) {
    val position by controller.positionMs.collectAsStateWithLifecycle()
    Text(formatTime(position), style = MaterialTheme.typography.labelLarge, color = Color.White)
}

/** Total duration, or remaining time (`-4:12`) after a tap. */
@Composable
private fun DurationTime(controller: PlaybackController, ui: FullPlayerState) {
    val duration by controller.durationMs.collectAsStateWithLifecycle()
    val text = if (ui.showRemaining) {
        val position by controller.positionMs.collectAsStateWithLifecycle()
        "-" + formatTime(duration - position)
    } else {
        formatTime(duration)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        modifier = Modifier
            .widthIn(min = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { ui.showRemaining = !ui.showRemaining; ui.bump() }
            .padding(vertical = 12.dp),
    )
}

/** Current speed when it isn't 1×; tap opens the ⋮ sheet. */
@Composable
private fun SpeedChip(controller: PlaybackController, ui: FullPlayerState) {
    val speed by controller.speed.collectAsStateWithLifecycle()
    val boosted by controller.isSpeedBoosted.collectAsStateWithLifecycle()
    if (speed == 1f || boosted) return
    TextButton(onClick = { ui.sheet = PlayerSheet.More; ui.bump() }) {
        Text(formatSpeed(speed), color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Seek bar: dragging is a live seek (closest-sync, coalesced); release does one exact seek.
 * While dragging, a thumbnail bubble (decision 31) shows the nearest ready sprite frame.
 */
@Composable
private fun SeekBar(controller: PlaybackController, ui: FullPlayerState) {
    val position by controller.positionMs.collectAsStateWithLifecycle()
    val duration by controller.durationMs.collectAsStateWithLifecycle()
    val max = duration.coerceAtLeast(1L).toFloat()
    val state = remember(max) { SliderState(value = 0f, trackRange = 0f..max) }
    SideEffect {
        if (!ui.seekBarDragging) state.value = position.toFloat().coerceIn(0f, max)
    }
    Box(Modifier.fillMaxWidth()) {
        Slider(
            state = state,
            enabled = duration > 0,
            onValueChange = { v ->
                if (!ui.seekBarDragging) {
                    ui.seekBarDragging = true
                    controller.beginScrub()
                }
                state.value = v
                controller.scrubTo(v.toLong())
            },
            onValueChangeFinished = {
                if (ui.seekBarDragging) {
                    ui.seekBarDragging = false
                    controller.endScrub(state.value.toLong())
                    ui.bump()
                }
            },
            colors = SliderDefaults.colors(inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        )
        SeekPreviewBubble(controller, ui, state)
    }
}
