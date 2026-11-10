package com.dietpizza.rxplayer.feature.player.viewer

import com.dietpizza.rxplayer.core.designsystem.icon.RxIcons
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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

    fun stopAnimation() {
        animation?.cancel()
        animation = null
    }

    /** One gesture frame: zoom by [zoom] around [focusX]/[focusY] (from the centre) and pan. */
    fun transform(focusX: Float, focusY: Float, panX: Float, panY: Float, zoom: Float) {
        val old = scale
        val new = ZoomMath.clampScale(old * zoom, ZoomMath.MIN_GESTURE_SCALE, ZoomMath.MAX_SCALE)
        val ox = ZoomMath.offsetAfterZoom(offsetX, focusX, old, new, panX)
        val oy = ZoomMath.offsetAfterZoom(offsetY, focusY, old, new, panY)
        scale = new
        offsetX = ZoomMath.clampOffset(ox, contentW, containerW, new)
        offsetY = ZoomMath.clampOffset(oy, contentH, containerH, new)
    }

    /** Animates to [target] scale keeping the point at [focusX]/[focusY] (from the centre) fixed. */
    fun animateTo(scope: CoroutineScope, target: Float, focusX: Float, focusY: Float, spec: AnimationSpec<Float>) {
        stopAnimation()
        val fromS = scale
        val fromX = offsetX
        val fromY = offsetY
        val toX = ZoomMath.clampOffset(ZoomMath.offsetAfterZoom(fromX, focusX, fromS, target, 0f), contentW, containerW, target)
        val toY = ZoomMath.clampOffset(ZoomMath.offsetAfterZoom(fromY, focusY, fromS, target, 0f), contentH, containerH, target)
        animation = scope.launch {
            animate(0f, 1f, animationSpec = spec) { t, _ ->
                scale = MorphMath.lerp(fromS, target, t)
                offsetX = MorphMath.lerp(fromX, toX, t)
                offsetY = MorphMath.lerp(fromY, toY, t)
            }
        }
    }

    /** After a gesture: spring back from a below-fit pinch. */
    fun settle(scope: CoroutineScope, spec: AnimationSpec<Float>) {
        if (scale < ZoomMath.MIN_SCALE) animateTo(scope, ZoomMath.MIN_SCALE, 0f, 0f, spec)
    }

    fun reset() {
        stopAnimation()
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }
}

/**
 * A photo page (plan §4 #4): Fit-centred, pinch/pan zoom, swipe-up for info, the pager only
 * swipes at fit (the gestures consume moves while zoomed in). Double-tap toggles favourite
 * ([onFavourite]) with a heart pop over the photo; items that can't be favourited (null) keep
 * double-tap zoom instead. [isFavourite] is read at tap time, to pick the heart.
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
    onFavourite: (() -> Unit)?,
    isFavourite: () -> Boolean,
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
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    val currentOnFavourite by rememberUpdatedState(onFavourite)
    val currentIsFavourite by rememberUpdatedState(isFavourite)
    val heart = remember(item.key) { HeartPop() }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged {
                zoom.containerW = it.width.toFloat()
                zoom.containerH = it.height.toFloat()
            }
            .pointerInput(zoom) {
                detectTapGestures(
                    onTap = { currentOnTap() },
                    onDoubleTap = { p ->
                        val favourite = currentOnFavourite
                        if (favourite != null) {
                            heart.filled = !currentIsFavourite()
                            favourite()
                            heart.play(scope)
                            return@detectTapGestures
                        }
                        zoom.animateTo(
                            scope,
                            ZoomMath.doubleTapScale(zoom.scale),
                            p.x - size.width / 2f,
                            p.y - size.height / 2f,
                            spec,
                        )
                    },
                )
            }
            .zoomGestures(zoom, scope, spec) { currentOnSwipeUp() },
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
        HeartPopIcon(heart)
    }
}

/** The double-tap heart: [progress] runs 0 → 1 once per pop; idle at 0. */
@Stable
private class HeartPop {
    val progress = androidx.compose.animation.core.Animatable(0f)

    /** Filled heart (just favourited) or outline (just removed). */
    var filled by mutableStateOf(true)

    private var job: Job? = null

    fun play(scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(HEART_POP_MS, easing = LinearEasing))
            progress.snapTo(0f)
        }
    }
}

/**
 * Heart centred on the photo: springs up past full size, settles, then fades. Scale and alpha
 * are read in the layer only, so the pop never recomposes.
 */
@Composable
private fun HeartPopIcon(pop: HeartPop) {
    Icon(
        if (pop.filled) RxIcons.Favorite else RxIcons.FavoriteBorder,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier
            .size(96.dp)
            .graphicsLayer {
                val t = pop.progress.value
                val s = when {
                    t < 0.3f -> MorphMath.lerp(0.4f, 1.15f, t / 0.3f)
                    t < 0.5f -> MorphMath.lerp(1.15f, 1f, (t - 0.3f) / 0.2f)
                    else -> 1f
                }
                scaleX = s
                scaleY = s
                alpha = when {
                    t <= 0f || t >= 1f -> 0f
                    t < 0.15f -> t / 0.15f
                    t < 0.6f -> 1f
                    else -> 1f - (t - 0.6f) / 0.4f
                }
            },
    )
}

@Composable
private fun LoadFailed() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(RxIcons.BrokenImage, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(48.dp))
        Text("Can't open this image", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Pinch-zoom, pan and swipe-up. Moves are consumed only while transforming (two fingers, or one
 * while zoomed in), so at fit a horizontal drag reaches the pager and a vertical one is ours.
 */
private fun Modifier.zoomGestures(
    zoom: ZoomState,
    scope: CoroutineScope,
    spec: AnimationSpec<Float>,
    onSwipeUp: () -> Unit,
): Modifier = pointerInput(zoom) {
    val swipeThreshold = SWIPE_UP_DP.dp.toPx()
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        zoom.stopAnimation()
        var transforming = false
        var dx = 0f
        var dy = 0f
        var swiped = false
        while (true) {
            val event = awaitPointerEvent()
            var anyPressed = false
            var pressedCount = 0
            var consumedElsewhere = false
            for (c in event.changes) {
                if (c.pressed) {
                    anyPressed = true
                    pressedCount++
                }
                if (c.isConsumed) consumedElsewhere = true
            }
            if (!anyPressed) break
            // The pager (or a tap detector cancelling) took this gesture.
            if (consumedElsewhere && !transforming) break
            if (pressedCount >= 2 || zoom.isZoomed) {
                transforming = true
                val z = event.calculateZoom()
                val pan = event.calculatePan()
                val c = event.calculateCentroid(useCurrent = true)
                if (c.isSpecified) {
                    zoom.transform(c.x - size.width / 2f, c.y - size.height / 2f, pan.x, pan.y, z)
                }
                for (ch in event.changes) if (ch.positionChanged()) ch.consume()
            } else if (!swiped) {
                val pan = event.calculatePan()
                dx += pan.x
                dy += pan.y
                if (-dy > swipeThreshold && abs(dy) > 2f * abs(dx)) {
                    swiped = true
                    onSwipeUp()
                }
            }
        }
        if (transforming) zoom.settle(scope, spec)
    }
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
private const val HEART_POP_MS = 600
