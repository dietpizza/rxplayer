package com.dietpizza.rxplayer.feature.player.viewer

import com.dietpizza.rxplayer.core.designsystem.icon.RxIcons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.dietpizza.rxplayer.core.player.PlaybackController
import com.dietpizza.rxplayer.core.ui.thumbnail.MediaStoreThumbnail
import com.dietpizza.rxplayer.core.ui.thumbnail.rememberRxImageLoader
import com.dietpizza.rxplayer.feature.player.ui.FullPlayerState
import com.dietpizza.rxplayer.feature.player.ui.formatTime

/** Height of the slim inline controls row under the card. */
internal val InlineControlsHeight: Dp = 56.dp

/**
 * A video page (plan §4 #4, §4.1). Neighbours show the poster only. The settled page whose item
 * the controller has loaded ([active]) hosts the window's single video surface, which morphs to
 * the full player in place ([morphVideo]); its poster is the surface's shutter, so there's never
 * a black frame before the first video frame.
 *
 * Tap toggles the chrome; a drag up drives the morph by hand and a fling commits it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VideoPage(
    item: ViewerItem,
    active: Boolean,
    controller: PlaybackController,
    geo: ViewerGeometry,
    morph: MorphState,
    fullState: FullPlayerState,
    chromeVisible: Boolean,
    aspect: () -> Float,
    onTap: () -> Unit,
    onExpand: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragStopped: (Float) -> Unit,
    onDragStarted: () -> Unit,
) {
    val corner = remember { unitCornerPath() }
    val currentOnTap by rememberUpdatedState(onTap)
    val dragState = rememberDraggableState { delta -> onDrag(delta) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { currentOnTap() }) }
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                enabled = active,
                onDragStarted = { onDragStarted() },
                onDragStopped = { v -> onDragStopped(v) },
            ),
    ) {
        // One surface box whose modifier switches between the inline card and the morphing
        // video. The poster stays composed in the same slot across that switch (no reload,
        // no blank frame) and only fades once this page's own first video frame is on screen.
        val firstFrameKey by controller.firstFrameKey.collectAsStateWithLifecycle()
        val posterUp = !active || firstFrameKey != item.key
        val radius = with(LocalDensity.current) { geo.radius.toDp() }
        Box(
            if (active) {
                Modifier
                    .morphVideo(geo, morph, fullState, aspect)
                    .drawWithContent {
                        drawContent()
                        drawCornerMask(MorphMath.cornerRadius(morph.progress.value, geo.radius), corner, Color.Black)
                    }
            } else {
                Modifier.inlineCard(geo, aspect).clip(RoundedCornerShape(radius))
            },
        ) {
            if (active) {
                ContentFrame(
                    player = controller.player,
                    modifier = Modifier.fillMaxSize(),
                    surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                    contentScale = ContentScale.Fit,
                    keepContentOnReset = false,
                    shutter = {},
                )
            }
            Poster(item, Modifier.fillMaxSize().graphicsLayer { alpha = if (posterUp) 1f else 0f })
        }
        if (active) InlineError(controller, geo, morph, aspect)
        if (active) {
            InlineControls(
                controller = controller,
                visible = chromeVisible,
                morph = morph,
                onExpand = onExpand,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                    .padding(bottom = ActionRowHeight),
            )
        }
    }
}

/** The MediaStore poster frame, or plain black for items without one (network). */
@Composable
private fun Poster(item: ViewerItem, modifier: Modifier) {
    val uri = item.gallery?.contentUri
    if (uri == null) {
        Box(modifier.background(Color.Black))
        return
    }
    val context = LocalContext.current
    val request = remember(uri) {
        ImageRequest.Builder(context).data(MediaStoreThumbnail(uri)).size(POSTER_PX, POSTER_PX).build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        imageLoader = rememberRxImageLoader(),
        contentScale = ContentScale.Crop,
        modifier = modifier.background(Color.Black),
    )
}

/** A clear error on the card (the codec, from PlaybackErrors) instead of a silent black box. */
@Composable
private fun InlineError(controller: PlaybackController, geo: ViewerGeometry, morph: MorphState, aspect: () -> Float) {
    val error by controller.error.collectAsStateWithLifecycle()
    val e = error?.takeIf { it.fatal } ?: return
    Box(
        Modifier
            .inlineCard(geo, aspect)
            .graphicsLayer { alpha = MorphMath.inlineChromeAlpha(morph.progress.value) }
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Icon(RxIcons.ErrorOutline, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text(e.title, color = Color.White)
        }
    }
}

/**
 * Slim inline controls (plan §4 #4): play/pause, scrub, global sticky mute, expand. Fades with
 * the chrome and out with the morph (alpha in a layer: no recomposition per frame).
 */

@Composable
private fun InlineControls(
    controller: PlaybackController,
    visible: Boolean,
    morph: MorphState,
    onExpand: () -> Unit,
    modifier: Modifier,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(motion.defaultEffectsSpec()),
        exit = fadeOut(motion.defaultEffectsSpec()),
    ) {
        InlineControlsRow(controller, morph, onExpand)
    }
}

@Composable
private fun InlineControlsRow(controller: PlaybackController, morph: MorphState, onExpand: () -> Unit) {
    val playWhenReady by controller.playWhenReady.collectAsStateWithLifecycle()
    val ended by controller.isEnded.collectAsStateWithLifecycle()
    val muted by controller.isMuted.collectAsStateWithLifecycle()
    Row(
        Modifier
            .fillMaxWidth()
            .height(InlineControlsHeight)
            .padding(horizontal = 8.dp)
            .graphicsLayer {
                alpha = MorphMath.inlineChromeAlpha(morph.progress.value)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val playing = playWhenReady && !ended
        IconButton(onClick = controller::togglePlayPause) {
            Icon(
                if (playing) RxIcons.Pause else RxIcons.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint = Color.White,
            )
        }
        InlineScrubBar(controller, Modifier.weight(1f))
        IconButton(onClick = controller::toggleMute) {
            Icon(
                if (muted) RxIcons.VolumeOff else RxIcons.VolumeUp,
                contentDescription = if (muted) "Unmute" else "Mute",
                tint = Color.White,
            )
        }
        IconButton(onClick = onExpand) {
            Icon(RxIcons.OpenInFull, contentDescription = "Full screen", tint = Color.White)
        }
    }
}

/** Position text + a scrub slider using the controller's live seek (only this leaf ticks). */

@Composable
private fun InlineScrubBar(controller: PlaybackController, modifier: Modifier) {
    val position by controller.positionMs.collectAsStateWithLifecycle()
    val duration by controller.durationMs.collectAsStateWithLifecycle()
    val max = duration.coerceAtLeast(1L).toFloat()
    val state = remember(max) { SliderState(value = 0f, trackRange = 0f..max) }
    var dragging by remember { mutableStateOf(false) }
    SideEffect { if (!dragging) state.value = position.toFloat().coerceIn(0f, max) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            formatTime(if (dragging) state.value.toLong() else position),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(end = 8.dp),
        )
        Slider(
            state = state,
            enabled = duration > 0,
            onValueChange = { v ->
                if (!dragging) {
                    dragging = true
                    controller.beginScrub()
                }
                state.value = v
                controller.scrubTo(v.toLong())
            },
            onValueChangeFinished = {
                if (dragging) {
                    dragging = false
                    controller.endScrub(state.value.toLong())
                }
            },
            colors = SliderDefaults.colors(inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
            modifier = Modifier.weight(1f).height(40.dp),
        )
    }
}

private const val POSTER_PX = 512
