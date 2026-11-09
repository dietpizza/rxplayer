package com.dietpizza.rxplayer.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dietpizza.rxplayer.core.player.PlaybackController

private val BubbleWidth = 144.dp
private val ThumbInset = 10.dp
private val BubbleGap = 4.dp

/**
 * Thumbnail bubble above the seek bar while it's dragged (decision 31). Follows the thumb in
 * the placement phase and draws a sub-rect of the sprite sheet in the draw phase, so a drag
 * frame neither recomposes nor allocates bitmaps. Shows the nearest frame that's ready.
 */
@Composable
internal fun SeekPreviewBubble(controller: PlaybackController, ui: FullPlayerState, slider: SliderState) {
    val settings by controller.settings.collectAsStateWithLifecycle()
    val spritesOrNull by controller.seekSprites.collectAsStateWithLifecycle()
    val sprites = spritesOrNull ?: return
    if (!settings.seekBarThumbnails) return
    val ready by sprites.readyCount.collectAsStateWithLifecycle()
    val motion = MaterialTheme.motionScheme

    AnimatedVisibility(
        visible = ui.seekBarDragging && ready > 0,
        enter = fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.9f),
        exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.9f),
        modifier = Modifier.layout { measurable, constraints ->
            val p = measurable.measure(Constraints())
            val w = constraints.maxWidth
            layout(0, 0) {
                val range = slider.trackRange
                val span = (range.endInclusive - range.start).coerceAtLeast(1f)
                val fraction = ((slider.value - range.start) / span).coerceIn(0f, 1f)
                val inset = ThumbInset.roundToPx()
                val cx = inset + fraction * (w - 2 * inset)
                val x = (cx - p.width / 2f).toInt().coerceIn(0, (w - p.width).coerceAtLeast(0))
                p.place(x, -p.height - BubbleGap.roundToPx())
            }
        },
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.75f),
            contentColor = Color.White,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val height = BubbleWidth * (sprites.frameHeight.toFloat() / sprites.frameWidth)
                Canvas(Modifier.size(BubbleWidth, height)) {
                    // Reading `ready` here re-draws as frames arrive; slider.value per drag frame.
                    if (ready <= 0) return@Canvas
                    val i = sprites.nearestReady(slider.value.toLong())
                    if (i < 0) return@Canvas
                    drawImage(
                        image = sprites.image,
                        srcOffset = IntOffset(sprites.srcX(i), sprites.srcY(i)),
                        srcSize = IntSize(sprites.frameWidth, sprites.frameHeight),
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        filterQuality = FilterQuality.Low,
                    )
                }
                Text(
                    formatTime(slider.value.toLong()),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}
