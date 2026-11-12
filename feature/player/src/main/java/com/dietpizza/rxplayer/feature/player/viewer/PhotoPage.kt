package com.dietpizza.rxplayer.feature.player.viewer

import androidx.compose.ui.geometry.Offset
import com.dietpizza.rxplayer.core.designsystem.icon.RxIcons
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.util.fastAny
import kotlin.math.exp
import kotlin.math.hypot
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.dietpizza.rxplayer.core.ui.thumbnail.MediaStoreThumbnail
import com.dietpizza.rxplayer.core.ui.thumbnail.rememberRxImageLoader
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Zoom/pan state of one photo page. Snapshot state, read only in graphicsLayer (draw phase), so
 * gestures never recompose the page.
 */
@Stable
internal class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offsetX by mutableFloatStateOf(0f)
        private set
    var offsetY by mutableFloatStateOf(0f)
        private set

    // Plain fields: written from layout/decode, read by gesture math only.
    var containerW = 0f
    var containerH = 0f
    var contentAspect = 0f

    private var animation: Job? = null

    val isZoomed: Boolean get() = scale > ZoomMath.MIN_SCALE + 0.01f

    private val contentW get() = ZoomMath.fitWidth(containerW, containerH, contentAspect)
    private val contentH get() = ZoomMath.fitHeight(containerW, containerH, contentAspect)

    /** Scale at which the photo covers the whole screen. */
    val fillScale: Float get() = ZoomMath.fillScale(contentW, contentH, containerW, containerH)

    fun stopAnimation() {
        animation?.cancel()
        animation = null
    }

    /**
     * One gesture frame: zoom by [zoom] around [focusX]/[focusY] (from the centre) and pan. Past
     * the zoom limits the pinch is damped (rubber band); [settle] brings it back.
     */
    fun transform(focusX: Float, focusY: Float, panX: Float, panY: Float, zoom: Float) {
        val old = scale
        val new = ZoomMath.rubberBandScale(old, zoom)
        val ox = ZoomMath.offsetAfterZoom(offsetX, focusX, old, new, panX)
        val oy = ZoomMath.offsetAfterZoom(offsetY, focusY, old, new, panY)
        scale = new
        offsetX = ZoomMath.clampOffset(ox, contentW, containerW, new)
        offsetY = ZoomMath.clampOffset(oy, contentH, containerH, new)
    }

    /**
     * Animates to [target] scale around [focusX]/[focusY] (from the centre). The content point
     * under the focus glides straight to where it ends up, so the tapped spot doesn't wander
     * while the edges clamp.
     */
    fun animateTo(scope: CoroutineScope, target: Float, focusX: Float, focusY: Float, spec: AnimationSpec<Float>) {
        stopAnimation()
        val fromS = scale
        val fromX = offsetX
        val fromY = offsetY
        val toX = ZoomMath.clampOffset(ZoomMath.offsetAfterZoom(fromX, focusX, fromS, target, 0f), contentW, containerW, target)
        val toY = ZoomMath.clampOffset(ZoomMath.offsetAfterZoom(fromY, focusY, fromS, target, 0f), contentH, containerH, target)
        // The content point under the focus, and where it lands on screen at the end.
        val px = (focusX - fromX) / fromS
        val py = (focusY - fromY) / fromS
        val endX = px * target + toX
        val endY = py * target + toY
        animation = scope.launch {
            animate(0f, 1f, animationSpec = spec) { t, _ ->
                val s = MorphMath.lerp(fromS, target, t)
                scale = s
                offsetX = MorphMath.lerp(focusX, endX, t) - px * s
                offsetY = MorphMath.lerp(focusY, endY, t) - py * s
            }
        }
    }

    /**
     * Keeps a pan going after the finger lifts, with the platform's scroll deceleration, until it
     * runs out or both axes hit an edge. [velocityX]/[velocityY] in px/s.
     */
    fun fling(scope: CoroutineScope, velocityX: Float, velocityY: Float, decay: DecayAnimationSpec<Float>) {
        stopAnimation()
        val speed = hypot(velocityX, velocityY)
        if (speed < MIN_FLING_PX_S || !isZoomed) return
        val ux = velocityX / speed
        val uy = velocityY / speed
        animation = scope.launch {
            var last = 0f
            AnimationState(0f, speed).animateDecay(decay) {
                val d = value - last
                last = value
                val nx = ZoomMath.clampOffset(offsetX + ux * d, contentW, containerW, scale)
                val ny = ZoomMath.clampOffset(offsetY + uy * d, contentH, containerH, scale)
                val moved = abs(nx - offsetX) > 0.01f || abs(ny - offsetY) > 0.01f
                offsetX = nx
                offsetY = ny
                if (!moved) cancelAnimation()
            }
        }
    }

    /** After a gesture: spring back from a pinch past either limit, around [focusX]/[focusY]. */
    fun settle(scope: CoroutineScope, focusX: Float, focusY: Float, spec: AnimationSpec<Float>): Boolean {
        val target = when {
            scale < ZoomMath.MIN_SCALE -> ZoomMath.MIN_SCALE
            scale > ZoomMath.MAX_SCALE -> ZoomMath.MAX_SCALE
            else -> return false
        }
        animateTo(scope, target, focusX, focusY, spec)
        return true
    }

    fun reset() {
        stopAnimation()
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    private companion object {
        const val MIN_FLING_PX_S = 50f
    }
}

/**
 * A photo page (plan §4 #4): Fit-centred, pinch/pan zoom, swipe-up for info, the pager only
 * swipes at fit (the gestures consume moves while zoomed in). A single tap toggles the
 * chrome straight away (no double-tap wait); a quick second tap undoes that toggle and zooms in
 * on the tapped point (or back out). Favourite is the top bar's heart.
 *
 * Decodes at screen size off the main thread; once zoomed past ~1.6× it asks once for a sharper
 * decode (up to [DETAIL_MAX_LONG_EDGE]) and swaps it in. Animated images run only while
 * [settled]; otherwise they hold their first frame.
 */
@Composable
internal fun PhotoPage(
    item: ViewerItem,
    images: ViewerImages,
    settled: Boolean,
    onTap: () -> Unit,
    onSwipeUp: () -> Unit,
    onZoomedChange: (Boolean) -> Unit,
    onDecoded: (DecodedImage) -> Unit,
) {
    val window = LocalWindowInfo.current.containerSize
    val target = max(window.width, window.height).coerceAtLeast(1)
    val zoom = remember(item.key) { ZoomState() }
    var image by remember(item.key) { mutableStateOf<DecodedImage?>(images.cached(item, target)) }
    var failed by remember(item.key) { mutableStateOf(false) }
    val currentOnDecoded by rememberUpdatedState(onDecoded)

    LaunchedEffect(item.key, target) {
        image?.let { currentOnDecoded(it); return@LaunchedEffect }
        try {
            val decoded = images.decode(item, target)
            image = decoded
            currentOnDecoded(decoded)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            failed = true
        }
    }

    // Sharper decode once zoomed in (statics only; one attempt per page).
    val wantsDetail by remember { derivedStateOf { zoom.scale > DETAIL_ZOOM } }
    var detailTried by remember(item.key) { mutableStateOf(false) }
    LaunchedEffect(wantsDetail, image) {
        val img = image as? StaticImage ?: return@LaunchedEffect
        if (!wantsDetail || detailTried) return@LaunchedEffect
        val want = min(img.sourceLongEdge, min(target * 3, DETAIL_MAX_LONG_EDGE))
        if (max(img.width, img.height) * 1.25f >= want) return@LaunchedEffect
        detailTried = true
        runCatching { images.decode(item, want, cache = false) }.getOrNull()?.let { image = it }
    }

    // Leaving the page resets zoom; the settled page reports whether it's zoomed (pager lock).
    LaunchedEffect(settled) {
        if (!settled) {
            zoom.reset()
            return@LaunchedEffect
        }
        snapshotFlow { zoom.isZoomed }.collect(onZoomedChange)
    }

    val scope = rememberCoroutineScope()
    // No bounce: an overshooting zoom flashes the black edges.
    val spec = remember { spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = ZOOM_STIFFNESS) }
    val decay = rememberSplineBasedDecay<Float>()
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged {
                zoom.containerW = it.width.toFloat()
                zoom.containerH = it.height.toFloat()
            }
            .photoGestures(zoom, scope, spec, decay, onTap = { currentOnTap() }, onSwipeUp = { currentOnSwipeUp() }),
        contentAlignment = Alignment.Center,
    ) {
        val img = image
        if (img == null) {
            val thumb = item.gallery?.contentUri
            if (thumb != null) {
                val context = LocalContext.current
                val request = remember(thumb) {
                    ImageRequest.Builder(context).data(MediaStoreThumbnail(thumb)).size(PLACEHOLDER_PX, PLACEHOLDER_PX).build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    imageLoader = rememberRxImageLoader(),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (failed) {
                LoadFailed()
            } else if (thumb == null) {
                LoadingIndicator(Modifier.size(48.dp))
            }
            return@Box
        }
        zoom.contentAspect = img.aspect
        val painter = when (img) {
            is StaticImage -> null
            is AnimatedImage -> rememberAnimatedPainter(img, running = settled)
        }
        val layer = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = zoom.scale
                scaleY = zoom.scale
                translationX = zoom.offsetX
                translationY = zoom.offsetY
            }
        if (img is StaticImage) {
            Image(bitmap = img.bitmap, contentDescription = item.title, contentScale = ContentScale.Fit, modifier = layer)
        } else if (painter != null) {
            Image(painter = painter, contentDescription = item.title, contentScale = ContentScale.Fit, modifier = layer)
        }
    }
}

@Composable
private fun LoadFailed() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(RxIcons.BrokenImage, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(48.dp))
        Text("Can't open this image", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * All touch on a photo page, in one detector so taps, zoom and pan never fight:
 * - Tap: toggles the chrome straight away (no double-tap wait).
 * - Double tap: puts the chrome back and zooms in on the tapped point (to fill the screen, at
 *   least [ZoomMath.DOUBLE_TAP_SCALE]), or back out to fit.
 * - Double tap and drag (one finger): drag down zooms in, up zooms out, around the tapped point.
 * - Pinch: zooms around the fingers, rubber-banding past the limits and springing back.
 * - Drag while zoomed: pans, and keeps going after release (fling).
 * - Swipe up at fit: [onSwipeUp] (info).
 *
 * At fit a one-finger horizontal drag isn't consumed, so it reaches the pager; once the pager
 * takes it (consumed in the Final pass), the gesture is left alone.
 */
private fun Modifier.photoGestures(
    zoom: ZoomState,
    scope: CoroutineScope,
    spec: AnimationSpec<Float>,
    decay: DecayAnimationSpec<Float>,
    onTap: () -> Unit,
    onSwipeUp: () -> Unit,
): Modifier = pointerInput(zoom) {
    val swipeThreshold = SWIPE_UP_DP.dp.toPx()
    val doubleTapSlop = DOUBLE_TAP_SLOP.toPx()
    val quickZoomPx = QUICK_ZOOM_DP.dp.toPx()
    val touchSlop = viewConfiguration.touchSlop
    val tracker = VelocityTracker()
    var lastTapUpMs = 0L
    var lastTapPos = Offset.Zero

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        zoom.stopAnimation()
        val secondTap = down.uptimeMillis - lastTapUpMs < viewConfiguration.doubleTapTimeoutMillis &&
            (down.position - lastTapPos).getDistance() < doubleTapSlop
        lastTapUpMs = 0L
        val centre = Offset(size.width / 2f, size.height / 2f)
        tracker.resetTracking()
        tracker.addPosition(down.uptimeMillis, down.position)

        var mode = Mode.Undecided
        var trackedId = down.id
        var lastCount = 1
        var focus = down.position - centre
        var travel = Offset.Zero
        var swipeX = 0f
        var swipeY = 0f
        var swiped = false
        var upChange: PointerInputChange? = null

        while (true) {
            val event = awaitPointerEvent()
            var pressedCount = 0
            event.changes.fastForEach { if (it.pressed) pressedCount++ }
            if (pressedCount == 0) {
                upChange = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                break
            }

            // Velocity follows one finger; restart it whenever the fingers change.
            val primary = event.changes.firstOrNull { it.id == trackedId && it.pressed }
                ?: event.changes.first { it.pressed }
            if (primary.id != trackedId || pressedCount != lastCount) {
                trackedId = primary.id
                tracker.resetTracking()
            }
            tracker.addPosition(primary.uptimeMillis, primary.position)
            lastCount = pressedCount

            val pan = event.calculatePan()
            travel += pan
            val moved = travel.getDistance() > touchSlop

            if (mode == Mode.Undecided) {
                mode = when {
                    pressedCount >= 2 -> Mode.Transform
                    secondTap && moved -> Mode.QuickZoom.also { onTap() } // undo the first tap's toggle
                    zoom.isZoomed && moved -> Mode.Transform
                    moved -> Mode.Fit
                    else -> Mode.Undecided
                }
            }

            when (mode) {
                Mode.QuickZoom -> {
                    if (pressedCount >= 2) {
                        mode = Mode.Transform
                    } else {
                        val z = exp(pan.y / quickZoomPx)
                        zoom.transform(focus.x, focus.y, 0f, 0f, z)
                        event.changes.fastForEach { if (it.positionChanged()) it.consume() }
                    }
                }
                Mode.Transform -> {
                    val c = event.calculateCentroid(useCurrent = true)
                    if (c.isSpecified) {
                        focus = c - centre
                        zoom.transform(focus.x, focus.y, pan.x, pan.y, event.calculateZoom())
                    }
                    event.changes.fastForEach { if (it.positionChanged()) it.consume() }
                }
                Mode.Fit -> {
                    if (pressedCount >= 2) {
                        mode = Mode.Transform
                    } else if (!swiped) {
                        swipeX += pan.x
                        swipeY += pan.y
                        if (-swipeY > swipeThreshold && abs(swipeY) > 2f * abs(swipeX)) {
                            swiped = true
                            onSwipeUp()
                        }
                    }
                }
                Mode.Undecided, Mode.Pager -> Unit
            }

            // Did a parent (the pager) take it? Seen only after everyone had the event.
            val final = awaitPointerEvent(PointerEventPass.Final)
            if (mode == Mode.Fit || mode == Mode.Undecided) {
                if (final.changes.fastAny { it.isConsumed }) {
                    mode = Mode.Pager
                }
            }
        }

        when (mode) {
            Mode.Undecided -> {
                val up = upChange ?: return@awaitEachGesture
                if (up.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis) return@awaitEachGesture
                // Instant: on a double tap this second toggle puts the chrome back.
                onTap()
                if (secondTap) {
                    up.consume()
                    val target = ZoomMath.doubleTapScale(zoom.scale, zoom.fillScale)
                    zoom.animateTo(scope, target, up.position.x - centre.x, up.position.y - centre.y, spec)
                } else {
                    lastTapUpMs = up.uptimeMillis
                    lastTapPos = up.position
                }
            }
            Mode.QuickZoom -> zoom.settle(scope, focus.x, focus.y, spec)
            Mode.Transform -> {
                if (!zoom.settle(scope, focus.x, focus.y, spec) && lastCount == 1) {
                    val v = tracker.calculateVelocity()
                    zoom.fling(scope, v.x, v.y, decay)
                }
            }
            Mode.Fit, Mode.Pager -> Unit
        }
    }
}

private enum class Mode {
    /** Not moved past the touch slop yet: may still be a tap. */
    Undecided,

    /** One finger at fit: the pager's horizontal swipe, or our swipe up. */
    Fit,

    /** The pager took the gesture. */
    Pager,

    /** Pinch, or pan while zoomed. */
    Transform,

    /** Double tap and drag. */
    QuickZoom,
}

// region animated images

/** Draws an [AnimatedImage]; runs it only while [running]. RenderThread drives the frames. */
@Composable
private fun rememberAnimatedPainter(image: AnimatedImage, running: Boolean): Painter {
    val painter = remember(image) { DrawablePainter(image.drawable, image.width, image.height) }
    DisposableEffect(image, running) {
        val d = image.drawable
        if (running) d.start() else d.stop()
        onDispose { d.stop() }
    }
    DisposableEffect(painter) { onDispose { painter.detach() } }
    return painter
}

/** Minimal Drawable → Painter: invalidations bump a counter read in draw (no recomposition). */
private class DrawablePainter(private val drawable: Drawable, w: Int, h: Int) : Painter() {
    private var tick by mutableIntStateOf(0)
    private val handler = Handler(Looper.getMainLooper())

    private val callback = object : Drawable.Callback {
        override fun invalidateDrawable(who: Drawable) {
            tick++
        }

        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
            handler.postAtTime(what, `when`)
        }

        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
            handler.removeCallbacks(what)
        }
    }

    init {
        drawable.callback = callback
    }

    override val intrinsicSize: Size = Size(w.toFloat(), h.toFloat())

    override fun DrawScope.onDraw() {
        @Suppress("UNUSED_VARIABLE")
        val t = tick
        drawIntoCanvas { canvas ->
            drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
            drawable.draw(canvas.nativeCanvas)
        }
    }

    fun detach() {
        (drawable as? Animatable)?.stop()
        drawable.callback = null
        handler.removeCallbacksAndMessages(null)
    }
}

// endregion

private const val DETAIL_ZOOM = 1.6f
private const val DETAIL_MAX_LONG_EDGE = 4096
private const val PLACEHOLDER_PX = 512
private const val SWIPE_UP_DP = 72

/** Double tap and drag: dragging this far zooms by a factor of e (~2.7×). */
private const val QUICK_ZOOM_DP = 160

/** Zoom animations: quick, no overshoot. */
private const val ZOOM_STIFFNESS = 500f

private val DOUBLE_TAP_SLOP = 100.dp
