package com.dietpizza.rxplayer.feature.player.viewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.dietpizza.rxplayer.feature.player.ui.DISMISS_SHRINK
import com.dietpizza.rxplayer.feature.player.ui.FullPlayerState
import kotlin.math.roundToInt

/**
 * The one place the morph's motion is chosen (plan §4.1, §10). Swap this to try another spec;
 * nothing else depends on it. Expressive spatial spring from the theme's motion scheme.
 */
@Composable
internal fun morphSpec(): FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.defaultSpatialSpec()

/**
 * Progress of the viewer ↔ full player morph: 0 = inline in the pager page, 1 = full player.
 * Read [progress] only in layout/draw lambdas; the derived flags change at the ends only, so
 * composition sees two or three changes per morph, never one per frame.
 */
@Stable
internal class MorphState {
    val progress = Animatable(0f)

    /** A drag-up is driving [progress] by hand. */
    var dragging by mutableStateOf(false)

    /**
     * Collapse started from a swipe-down: the rect the video was at on release, used instead of
     * the full rect so the shrink continues from the finger. Read in layout only.
     */
    var collapseFrom by mutableStateOf<Rect?>(null)

    val isInline: Boolean by derivedStateOf { progress.value == 0f && !progress.isRunning && !dragging }
    val isFull: Boolean by derivedStateOf { progress.value == 1f && !progress.isRunning && !dragging }
}

/**
 * Where the inline card sits in a page. The page is the whole window (the pager fills it), so
 * the full rect is simply the window fitted to the video: the surface morphs in place, inside
 * its page, with no reparenting.
 */
@Stable
internal class ViewerGeometry {
    /** Card area insets, px (stable: system bars "ignoring visibility"). */
    var top by mutableFloatStateOf(0f)
    var bottom by mutableFloatStateOf(0f)
    var side by mutableFloatStateOf(0f)

    /** Inline corner radius, px. */
    var radius by mutableFloatStateOf(0f)

    fun inline(w: Float, h: Float, aspect: Float): Rect = MorphMath.fit(side, top, w - side, h - bottom, aspect)

    fun full(w: Float, h: Float, aspect: Float): Rect = MorphMath.fit(0f, 0f, w, h, aspect)
}

/** Lays the child out at the inline card rect of a page. */
internal fun Modifier.inlineCard(geo: ViewerGeometry, aspect: () -> Float): Modifier = layout { measurable, c ->
    val r = geo.inline(c.maxWidth.toFloat(), c.maxHeight.toFloat(), aspect())
    val p = measurable.measure(Constraints.fixed(r.width.roundToInt().coerceAtLeast(1), r.height.roundToInt().coerceAtLeast(1)))
    layout(c.maxWidth, c.maxHeight) { p.place(r.left.roundToInt(), r.top.roundToInt()) }
}

/**
 * Bounds of the single video surface (spike pick: "bounds + mask"). Layout rect lerps from the
 * inline card to the full-screen fit rect; on top, a layer applies the full player's aspect/zoom
 * scale (faded in with progress, so Crop/zoom modes don't jump) and its swipe-down transform.
 * Progress is read in the layout/placement lambdas only.
 */
internal fun Modifier.morphVideo(
    geo: ViewerGeometry,
    morph: MorphState,
    full: FullPlayerState,
    aspect: () -> Float,
): Modifier = layout { measurable, c ->
    val w = c.maxWidth.toFloat()
    val h = c.maxHeight.toFloat()
    val a = aspect()
    val t = morph.progress.value
    val from = geo.inline(w, h, a)
    val to = morph.collapseFrom ?: geo.full(w, h, a)
    val r = MorphMath.lerp(from, to, t)
    val p = measurable.measure(Constraints.fixed(r.width.roundToInt().coerceAtLeast(1), r.height.roundToInt().coerceAtLeast(1)))
    layout(c.maxWidth, c.maxHeight) {
        p.placeWithLayer(r.left.roundToInt(), r.top.roundToInt()) {
            val k = t.coerceIn(0f, 1f)
            val offset = full.dismissOffsetPx
            val f = if (h > 0f) (offset / h).coerceIn(0f, 1f) else 0f
            val s = 1f - DISMISS_SHRINK * f
            scaleX = MorphMath.lerp(1f, full.scaleX, k) * s
            scaleY = MorphMath.lerp(1f, full.scaleY, k) * s
            translationY = offset
        }
    }
}

/** Unit inverse corner (the square outside a quarter circle), drawn scaled at each corner. */
internal fun unitCornerPath(): Path = Path().apply {
    moveTo(0f, 0f)
    lineTo(1f, 0f)
    cubicTo(1f - KAPPA, 0f, 0f, 1f - KAPPA, 0f, 1f)
    close()
}

private const val KAPPA = 0.5522848f

/**
 * Masks the four corners in [color] over a SurfaceView (whose hole can't be clipped cheaply):
 * a unit path drawn with transforms, so nothing is allocated per frame.
 */
internal fun DrawScope.drawCornerMask(radius: Float, corner: Path, color: Color) {
    if (radius < 0.5f) return
    val w = size.width
    val h = size.height
    scale(radius, radius, pivot = Offset.Zero) { drawPath(corner, color) }
    translate(w, 0f) { rotate(90f, pivot = Offset.Zero) { scale(radius, radius, pivot = Offset.Zero) { drawPath(corner, color) } } }
    translate(w, h) { rotate(180f, pivot = Offset.Zero) { scale(radius, radius, pivot = Offset.Zero) { drawPath(corner, color) } } }
    translate(0f, h) { rotate(270f, pivot = Offset.Zero) { scale(radius, radius, pivot = Offset.Zero) { drawPath(corner, color) } } }
}
