package com.dietpizza.rxplayer.feature.player.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import android.content.res.Configuration
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.text.Cue
import com.dietpizza.rxplayer.core.player.PlaybackController
import com.dietpizza.rxplayer.core.player.zoom.VideoScaling
import kotlin.math.roundToInt

/** Base text size as a fraction of the video's displayed height (Media3's default, 5.33%). */
private const val BASE_TEXT_FRACTION = 0.0533f

/**
 * Draws subtitle cues (decision 9: basic). Text cues are flattened to plain text (ASS styling
 * is lost, accepted), bottom-centred, or top when the cue sits in the upper half; bitmap cues
 * (PGS/VobSub/DVB) are placed on the fitted video rect. Recomposes only when cues change.
 */
@Composable
internal fun SubtitleOverlay(controller: PlaybackController, liftForControls: Boolean, modifier: Modifier) {
    val cues by controller.cues.collectAsStateWithLifecycle()
    val subtitleSettings by controller.subtitleSettings.collectAsStateWithLifecycle()
    val video by controller.videoSize.collectAsStateWithLifecycle()
    if (cues.isEmpty() || !subtitleSettings.enabled) return
    // Portrait stacks transport + seek bar + action row at the bottom, so lift further there.
    val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val lift by animateDpAsState(
        if (liftForControls) (if (portrait) 260.dp else 120.dp) else 24.dp,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "subtitleLift",
    )

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val fitH = VideoScaling.fitHeight(wPx, hPx, video.width.toFloat(), video.height.toFloat())
            .takeIf { it > 0f } ?: minOf(wPx, hPx)
        val fontSize = with(density) { (fitH * BASE_TEXT_FRACTION * subtitleSettings.textScale).toSp() }
        val style = remember(fontSize, subtitleSettings.background) {
            TextStyle(
                color = Color.White,
                fontSize = fontSize,
                lineHeight = fontSize * 1.2f,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                shadow = if (subtitleSettings.background) null else Shadow(Color.Black, Offset(0f, 2f), 6f),
            )
        }

        val bottom = ArrayList<String>(2)
        val top = ArrayList<String>(1)
        val bitmaps = ArrayList<Cue>(0)
        for (cue in cues) {
            if (cue.bitmap != null) {
                bitmaps += cue
                continue
            }
            val text = cue.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) continue
            val upper = cue.lineType == Cue.LINE_TYPE_FRACTION && cue.line != Cue.DIMEN_UNSET && cue.line < 0.4f
            if (upper) top += text else bottom += text
        }

        if (bitmaps.isNotEmpty()) BitmapCues(bitmaps, video.width.toFloat(), video.height.toFloat())
        if (top.isNotEmpty()) {
            CueColumn(top, style, subtitleSettings.background, Modifier.align(Alignment.TopCenter).padding(top = 48.dp))
        }
        if (bottom.isNotEmpty()) {
            CueColumn(bottom, style, subtitleSettings.background, Modifier.align(Alignment.BottomCenter).padding(bottom = lift))
        }
    }
}

@Composable
private fun CueColumn(lines: List<String>, style: TextStyle, background: Boolean, modifier: Modifier) {
    Column(
        modifier.widthIn(max = 900.dp).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (line in lines) {
            Text(
                line,
                style = style,
                modifier = if (background) {
                    Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                } else {
                    Modifier
                },
            )
        }
    }
}

/** Image subtitles positioned with the cue's fractional geometry inside the fitted video rect. */
@Composable
private fun BitmapCues(cues: List<Cue>, videoW: Float, videoH: Float) {
    val images: List<Pair<Cue, ImageBitmap>> = remember(cues) { cues.map { it to it.bitmap!!.asImageBitmap() } }
    Canvas(Modifier.fillMaxSize()) {
        val fw = VideoScaling.fitWidth(size.width, size.height, videoW, videoH).takeIf { it > 0f } ?: size.width
        val fh = VideoScaling.fitHeight(size.width, size.height, videoW, videoH).takeIf { it > 0f } ?: size.height
        val left0 = (size.width - fw) / 2f
        val top0 = (size.height - fh) / 2f
        for ((cue, image) in images) {
            val w = if (cue.size != Cue.DIMEN_UNSET) cue.size * fw else image.width.toFloat()
            val h = if (cue.bitmapHeight != Cue.DIMEN_UNSET) cue.bitmapHeight * fh else w * image.height / image.width
            val x = if (cue.position != Cue.DIMEN_UNSET) cue.position * fw else (fw - w) / 2f
            val y = if (cue.line != Cue.DIMEN_UNSET) cue.line * fh else fh - h
            val ax = when (cue.positionAnchor) {
                Cue.ANCHOR_TYPE_MIDDLE -> x - w / 2f
                Cue.ANCHOR_TYPE_END -> x - w
                else -> x
            }
            val ay = when (cue.lineAnchor) {
                Cue.ANCHOR_TYPE_MIDDLE -> y - h / 2f
                Cue.ANCHOR_TYPE_END -> y - h
                else -> y
            }
            drawImage(
                image,
                dstOffset = IntOffset((left0 + ax).roundToInt(), (top0 + ay).roundToInt()),
                dstSize = IntSize(w.roundToInt(), h.roundToInt()),
            )
        }
    }
}
