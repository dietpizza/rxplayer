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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.IconToggleButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/**
 * The control set (plan §6.3), in the gallery viewer's slim style: plain white icons, a top
 * row, and two slim rows at the bottom (play · time · seek bar · duration, then actions).
 * Nothing sits over the middle of the video.
 */
@Composable
internal fun PlayerControls(
    controller: PlaybackController,
    ui: FullPlayerState,
    host: FullPlayerHost,
    onCycleAspect: () -> Unit,
    onBack: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            // YouTube-style: a faint overall dim keeps the plain centre icons legible.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
        }
        // Light scrims only at the edges; the middle stays clear and doesn't catch touches.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(120.dp)
                .background(Brush.verticalGradient(listOf(Scrim, Color.Transparent))),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(160.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Scrim))),
        )
        TopBar(controller, ui, onBack, Modifier.align(Alignment.TopCenter).controlsInsets())
        if (landscape) CenterTransport(controller, ui, Modifier.align(Alignment.Center))
        BottomBar(
            controller = controller,
            ui = ui,
            landscape = landscape,
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
            .padding(start = 4.dp, end = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
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
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = description, tint = Color.White)
    }
}

/** Mute is first-class (plan §3.3): it lives in the bottom row, next to rotation. */
@Composable
private fun MuteButton(controller: PlaybackController, ui: FullPlayerState) {
    val muted by controller.isMuted.collectAsStateWithLifecycle()
    IconButton(
        onClick = { controller.toggleMute(); ui.bump() },
    ) {
        Icon(
            imageVector = if (muted) RxIcons.VolumeOff else RxIcons.VolumeUp,
            contentDescription = if (muted) "Unmute" else "Mute",
            tint = Color.White,
        )
    }
}

/** Landscape only: previous · play/pause · next as plain, shadowless icons in the centre. */
@Composable
private fun CenterTransport(controller: PlaybackController, ui: FullPlayerState, modifier: Modifier) {
    val nowPlaying by controller.nowPlaying.collectAsStateWithLifecycle()
    val playWhenReady by controller.playWhenReady.collectAsStateWithLifecycle()
    val ended by controller.isEnded.collectAsStateWithLifecycle()
    val settings by controller.settings.collectAsStateWithLifecycle()
    val playing = playWhenReady && !ended
    val multiple = (nowPlaying?.count ?: 0) > 1
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        BigPlainIcon(RxIcons.SkipPrevious, "Previous", size = 36.dp) { controller.previous(); ui.bump() }
        // The buffering loader takes this spot: hide play/pause (keep its space) while it shows.
        val loading = ui.bufferingShown
        Box(Modifier.graphicsLayer { alpha = if (loading) 0f else 1f }) {
            BigPlainIcon(
                icon = if (ended) RxIcons.Replay else if (playing) RxIcons.Pause else RxIcons.PlayArrow,
                description = if (ended) "Replay" else if (playing) "Pause" else "Play",
                size = 56.dp,
                enabled = !loading,
            ) { controller.togglePlayPause(); ui.bump() }
        }
        BigPlainIcon(
            RxIcons.SkipNext,
            "Next",
            size = 36.dp,
            enabled = nowPlaying?.hasNext == true || (settings.shuffle && multiple),
        ) { controller.next(); ui.bump() }
    }
}

@Composable
private fun BigPlainIcon(
    icon: ImageVector,
    description: String,
    size: androidx.compose.ui.unit.Dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size + 24.dp)) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.38f),
            modifier = Modifier.size(size),
        )
    }
}

@Composable
private fun BottomBar(
    controller: PlaybackController,
    ui: FullPlayerState,
    landscape: Boolean,
    rotationLocked: Boolean,
    onToggleRotationLock: () -> Unit,
    onCycleAspect: () -> Unit,
    modifier: Modifier,
) {
    val nowPlaying by controller.nowPlaying.collectAsStateWithLifecycle()
    val playWhenReady by controller.playWhenReady.collectAsStateWithLifecycle()
    val ended by controller.isEnded.collectAsStateWithLifecycle()
    val settings by controller.settings.collectAsStateWithLifecycle()
    val playing = playWhenReady && !ended
    val multiple = (nowPlaying?.count ?: 0) > 1
    val shuffle = settings.shuffle
    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        // Row 1: [play ·] elapsed · seek · duration (play moves to the centre in landscape).
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!landscape) {
                PlainIcon(
                    icon = if (ended) RxIcons.Replay else if (playing) RxIcons.Pause else RxIcons.PlayArrow,
                    description = if (ended) "Replay" else if (playing) "Pause" else "Play",
                ) { controller.togglePlayPause(); ui.bump() }
            } else {
                Spacer(Modifier.size(8.dp))
            }
            ElapsedTime(controller)
            Box(Modifier.weight(1f).padding(horizontal = 8.dp)) { SeekBar(controller, ui) }
            DurationTime(controller, ui)
            Spacer(Modifier.size(8.dp))
        }
        // Row 2: lock, aspect, speed · [prev, next] · mute, rotation, shuffle (bottom-right).
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlainIcon(RxIcons.LockOpen, "Lock controls") { ui.lock() }
            PlainIcon(RxIcons.AspectRatio, "Aspect: ${ui.aspectMode.label}", onClick = onCycleAspect)
            SpeedChip(controller, ui)
            Spacer(Modifier.weight(1f))
            if (!landscape) {
                PlainIcon(RxIcons.SkipPrevious, "Previous") { controller.previous(); ui.bump() }
                PlainIcon(RxIcons.SkipNext, "Next", enabled = nowPlaying?.hasNext == true || (shuffle && multiple)) {
                    controller.next(); ui.bump()
                }
                Spacer(Modifier.weight(1f))
            }
            MuteButton(controller, ui)
            PlainIcon(
                if (rotationLocked) RxIcons.ScreenLockRotation else RxIcons.ScreenRotation,
                if (rotationLocked) "Unlock rotation" else "Lock rotation",
                onClick = onToggleRotationLock,
            )
            // Toggle: on = Next and auto-next pick a random unplayed video.
            IconToggleButton(
                checked = shuffle,
                onCheckedChange = { controller.toggleShuffle(); ui.bump() },
                modifier = Modifier.semantics { contentDescription = if (shuffle) "Shuffle on" else "Shuffle off" },
            ) {
                Icon(
                    if (shuffle) RxIcons.ShuffleOn else RxIcons.Shuffle,
                    contentDescription = null,
                    tint = if (shuffle) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
        }
    }
}

@Composable
private fun PlainIcon(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.38f),
        )
    }
}

/** Leaf: the only composable (with [SeekBar]) that recomposes on position ticks. */
@Composable
private fun ElapsedTime(controller: PlaybackController) {
    val position by controller.positionMs.collectAsStateWithLifecycle()
    Text(formatTime(position), style = MaterialTheme.typography.labelMedium, color = Color.White)
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
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = Modifier
            .widthIn(min = 40.dp)
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
            modifier = Modifier.fillMaxWidth().height(40.dp),
        )
        SeekPreviewBubble(controller, ui, state)
    }
}
