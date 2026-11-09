package com.dietpizza.rxplayer.feature.player

import android.graphics.Outline
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.state.rememberPresentationState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Variants
// ---------------------------------------------------------------------------------------------

internal enum class SpikeSurface(val label: String, val type: Int) {
    SURFACE("SurfaceView", SURFACE_TYPE_SURFACE_VIEW),
    TEXTURE("TextureView", SURFACE_TYPE_TEXTURE_VIEW),
}

/**
 * BOUNDS: surface stays in the page; its layout rect is lerped card -> fullscreen every frame
 *         (the SurfaceView/TextureView is resized every frame).
 * SCALE:  surface stays in the page, is laid out ONCE at the fullscreen-fit size, and is shrunk to
 *         the card with a graphicsLayer scale + translation (no buffer resize during the morph).
 * MOVABLE: surface is a movableContentOf that is reparented from the pager page into a root
 *         overlay when the morph starts (this is what a real "full player at activity root" would
 *         do), then bounds-lerped there.
 */
internal enum class SpikeMode { BOUNDS, SCALE, MOVABLE }

/**
 * MASK:    draw the four inverse corners in the background colour ON TOP of the surface.
 * CLIP:    Compose graphicsLayer(shape, clip = true) around the PlayerSurface.
 * OUTLINE: View.setOutlineProvider + clipToOutline on the underlying SurfaceView/TextureView.
 * NONE:    square corners (baseline).
 */
internal enum class SpikeClip { MASK, CLIP, OUTLINE, NONE }

internal enum class SpikeSpec { DEFAULT, FAST, SLOW }

internal data class MorphSpikeConfig(
    val surface: SpikeSurface = SpikeSurface.SURFACE,
    val mode: SpikeMode = SpikeMode.BOUNDS,
    val clip: SpikeClip = SpikeClip.MASK,
    val spec: SpikeSpec = SpikeSpec.DEFAULT,
) {
    val label: String get() = "${surface.label}/${mode.name.lowercase()}/${clip.name.lowercase()}/${spec.name.lowercase()}"

    companion object {
        fun fromIntent(surface: String?, mode: String?, clip: String?, spec: String?) = MorphSpikeConfig(
            surface = if (surface.equals("texture", ignoreCase = true)) SpikeSurface.TEXTURE else SpikeSurface.SURFACE,
            mode = SpikeMode.entries.firstOrNull { it.name.equals(mode, ignoreCase = true) } ?: SpikeMode.BOUNDS,
            clip = when (clip?.lowercase()) {
                "clip" -> SpikeClip.CLIP
                "outline" -> SpikeClip.OUTLINE
                "none" -> SpikeClip.NONE
                else -> SpikeClip.MASK
            },
            spec = SpikeSpec.entries.firstOrNull { it.name.equals(spec, ignoreCase = true) } ?: SpikeSpec.DEFAULT,
        )
    }
}

private inline fun <reified E : Enum<E>> E.next(): E = enumValues<E>().let { it[(ordinal + 1) % it.size] }

// ---------------------------------------------------------------------------------------------
// Geometry: card rect and fullscreen rect have the SAME aspect (the video's), so any lerp between
// them is also exactly that aspect. The surface is always sized to the video: aspect-fit with no
// letterbox inside the surface, so there is never a stretch frame (±1 px rounding).
// ---------------------------------------------------------------------------------------------

private class SpikeGeometry(private val density: Density) {
    var pageW = 1f
    var pageH = 1f
    var aspect by mutableFloatStateOf(16f / 9f)

    private fun px(dp: Float) = dp * density.density

    fun card(): Rect = fit(Rect(px(16f), px(132f), pageW - px(16f), pageH - px(232f)))
    fun full(): Rect = fit(Rect(0f, 0f, pageW, pageH))
    fun at(t: Float): Rect {
        val a = card()
        val b = full()
        return Rect(lerpF(a.left, b.left, t), lerpF(a.top, b.top, t), lerpF(a.right, b.right, t), lerpF(a.bottom, b.bottom, t))
    }

    fun radius(t: Float): Float = px(28f) * (1f - t.coerceIn(0f, 1f))

    /** Corner radius in the surface's own (unscaled) coordinates. */
    fun localRadius(t: Float, mode: SpikeMode): Float =
        if (mode == SpikeMode.SCALE) radius(t) / (at(t).width / full().width).coerceAtLeast(0.01f) else radius(t)

    private fun fit(area: Rect): Rect {
        val w: Float
        val h: Float
        if (area.width / area.height > aspect) {
            h = area.height; w = h * aspect
        } else {
            w = area.width; h = w / aspect
        }
        return Rect(Offset(area.center.x - w / 2f, area.center.y - h / 2f), androidx.compose.ui.geometry.Size(w, h))
    }
}

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Lays the child out at geometry.at(t) inside a full-page box. Reads t in the layout phase only. */
private fun Modifier.morphBounds(geo: SpikeGeometry, t: () -> Float) = layout { measurable, c ->
    geo.pageW = c.maxWidth.toFloat()
    geo.pageH = c.maxHeight.toFloat()
    val r = geo.at(t())
    val w = r.width.roundToInt().coerceAtLeast(1)
    val h = (w / geo.aspect).roundToInt().coerceAtLeast(1)
    val p = measurable.measure(Constraints.fixed(w, h))
    layout(c.maxWidth, c.maxHeight) { p.place(r.left.roundToInt(), r.top.roundToInt()) }
}

/** Lays the child out ONCE at the fullscreen rect and scales/translates it to geometry.at(t). */
private fun Modifier.morphScale(geo: SpikeGeometry, t: () -> Float) = layout { measurable, c ->
    geo.pageW = c.maxWidth.toFloat()
    geo.pageH = c.maxHeight.toFloat()
    val full = geo.full()
    val w = full.width.roundToInt().coerceAtLeast(1)
    val h = (w / geo.aspect).roundToInt().coerceAtLeast(1)
    val p = measurable.measure(Constraints.fixed(w, h))
    layout(c.maxWidth, c.maxHeight) {
        p.placeWithLayer(full.left.roundToInt(), full.top.roundToInt()) {
            val r = geo.at(t())
            val s = r.width / w
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = s
            scaleY = s
            translationX = r.left - full.left
            translationY = r.top - full.top
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------------------------

@Composable
internal fun MorphSpikeScreen(
    player: Player,
    stats: MorphSpikeStats,
    status: String,
    initialConfig: MorphSpikeConfig,
    window: Window,
) {
    var config by remember { mutableStateOf(initialConfig) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current
    val geo = remember(density) { SpikeGeometry(density) }

    val presentation = rememberPresentationState(player)
    val videoSize = presentation.videoSizeDp
    SideEffect {
        if (videoSize != null && videoSize.isSpecified && videoSize.width > 0f && videoSize.height > 0f) {
            geo.aspect = videoSize.width / videoSize.height
        }
    }

    val pageBg = MaterialTheme.colorScheme.surface
    val pageBgState = rememberUpdatedState(pageBg)
    val bgAt: (Float) -> Color = { t -> lerp(pageBgState.value, Color.Black, t.coerceIn(0f, 1f)) }

    val spec: FiniteAnimationSpec<Float> = when (config.spec) {
        SpikeSpec.DEFAULT -> MaterialTheme.motionScheme.defaultSpatialSpec()
        SpikeSpec.FAST -> MaterialTheme.motionScheme.fastSpatialSpec()
        SpikeSpec.SLOW -> tween(1600, easing = FastOutSlowInEasing) // slow-mo, for eyeballing artifacts
    }

    var dragging by remember { mutableStateOf(false) }
    val morphActive by remember { derivedStateOf { progress.value != 0f || progress.isRunning || dragging } }
    val settledInline by remember { derivedStateOf { progress.value == 0f && !progress.isRunning && !dragging } }
    val settledFull by remember { derivedStateOf { progress.value == 1f && !progress.isRunning && !dragging } }

    fun morphLabel(target: Float) = config.label + if (target > 0.5f) " expand" else " collapse"

    fun animateTo(target: Float, velocity: Float = 0f) {
        stats.start(morphLabel(target), view.display?.refreshRate ?: 60f)
        stats.relabel(morphLabel(target))
        scope.launch {
            // If interrupted (new tap/drag), animateTo throws and the stats window keeps running.
            progress.animateTo(target, spec, initialVelocity = velocity)
            stats.stop()
        }
    }

    // ---- gestures (shared by the page and the MOVABLE overlay) ----
    var dragValue by remember { mutableFloatStateOf(0f) }
    val dragDistance = { geo.pageH * 0.6f }
    val dragState = rememberDraggableState { delta ->
        dragValue = (dragValue - delta / dragDistance()).coerceIn(0f, 1f)
        val v = dragValue
        scope.launch { progress.snapTo(v) }
    }
    val gestures = Modifier
        .clickable(
            enabled = !morphActive,
            interactionSource = null,
            indication = null,
        ) { animateTo(1f) }
        .draggable(
            state = dragState,
            orientation = Orientation.Vertical,
            onDragStarted = {
                dragging = true
                dragValue = progress.value
                stats.start(config.label + " drag", view.display?.refreshRate ?: 60f)
            },
            onDragStopped = { velocityPx ->
                dragging = false
                val v = -velocityPx / dragDistance() // progress units per second, up = positive
                val target = when {
                    v > 1.5f -> 1f
                    v < -1.5f -> 0f
                    else -> if (progress.value > 0.5f) 1f else 0f
                }
                animateTo(target, v)
            },
        )

    BackHandler(enabled = !settledInline) { animateTo(0f) }

    // Immersive only once fully settled, so the system-bar animation never overlaps a measured morph.
    LaunchedEffect(settledFull, settledInline) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (settledFull) controller.hide(WindowInsetsCompat.Type.systemBars())
        if (settledInline) controller.show(WindowInsetsCompat.Type.systemBars())
    }

    // The ONE surface. movableContentOf so MOVABLE mode can reparent it without recreating it; the
    // other modes only ever compose it in one place.
    val video = remember(player, geo) {
        movableContentOf { modifier: Modifier, cfg: MorphSpikeConfig, cover: Boolean ->
            SpikeVideo(
                player = player,
                cfg = cfg,
                cover = cover,
                radius = { geo.localRadius(progress.value, cfg.mode) },
                maskColor = { bgAt(progress.value) },
                modifier = modifier,
            )
        }
    }
    val surfaceModifier = when (config.mode) {
        SpikeMode.SCALE -> Modifier.morphScale(geo) { progress.value }
        else -> Modifier.morphBounds(geo) { progress.value }
    }
    val inOverlay = config.mode == SpikeMode.MOVABLE && morphActive

    Box(Modifier.fillMaxSize().drawBehind { drawRect(bgAt(progress.value)) }) {
        val pager = rememberPagerState(initialPage = 1) { 3 }
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !morphActive,
            beyondViewportPageCount = 1,
        ) { page ->
            Box(Modifier.fillMaxSize()) {
                if (page == 1) {
                    PageChrome(
                        Modifier.fillMaxSize().graphicsLayer {
                            val t = progress.value.coerceIn(0f, 1f)
                            alpha = 1f - t
                            scaleX = 1f - 0.06f * t
                            scaleY = 1f - 0.06f * t
                        },
                    )
                    if (!inOverlay) video(surfaceModifier, config, presentation.coverSurface)
                    Box(Modifier.morphBounds(geo) { progress.value }.then(gestures))
                } else {
                    FakePhotoPage(page, geo)
                }
            }
        }

        if (inOverlay) {
            video(surfaceModifier, config, presentation.coverSurface)
            Box(Modifier.morphBounds(geo) { progress.value }.then(gestures))
        }

        FullPlayerChrome(Modifier.fillMaxSize().graphicsLayer { alpha = ((progress.value - 0.6f) / 0.4f).coerceIn(0f, 1f) })

        SpikeHud(
            config = config,
            onConfig = { config = it },
            stats = stats,
            status = status,
            videoSize = if (videoSize != null && videoSize.isSpecified) "${videoSize.width.roundToInt()}x${videoSize.height.roundToInt()}dp" else "?",
            onMorph = { animateTo(if (progress.targetValue > 0.5f) 0f else 1f) },
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// The surface + corner treatment
// ---------------------------------------------------------------------------------------------

@Composable
private fun SpikeVideo(
    player: Player,
    cfg: MorphSpikeConfig,
    cover: Boolean,
    radius: () -> Float,
    maskColor: () -> Color,
    modifier: Modifier,
) {
    val clip = if (cfg.clip == SpikeClip.CLIP) {
        Modifier.graphicsLayer {
            shape = RoundedCornerShape(radius())
            clip = true
        }
    } else {
        Modifier
    }
    Box(modifier.then(clip)) {
        key(cfg.surface) {
            PlayerSurface(player = player, modifier = Modifier.fillMaxSize(), surfaceType = cfg.surface.type)
        }
        // Shutter until the first frame (so a slow first frame is black, not garbage/transparent).
        if (cover) Box(Modifier.fillMaxSize().background(Color.Black))
        if (cfg.clip == SpikeClip.MASK) {
            Spacer(Modifier.fillMaxSize().drawBehind { drawCornerMask(radius(), maskColor()) })
        }
        if (cfg.clip == SpikeClip.OUTLINE) OutlineClipEffect(cfg.surface, radius)
    }
}

/**
 * Inverse rounded corners in the colour of whatever is behind the card. Works for any surface
 * type because it is just window pixels drawn over the SurfaceView hole; costs one path per frame.
 * Requires the background behind the corners to be a known solid colour (here: the page bg, which
 * itself lerps to black, and the mask lerps with it).
 */
private fun DrawScope.drawCornerMask(r: Float, color: Color) {
    if (r < 0.5f) return
    val bounds = Rect(Offset.Zero, size)
    val path = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(bounds)
        addRoundRect(RoundRect(bounds, CornerRadius(r)))
    }
    drawPath(path, color)
}

/** Finds the SurfaceView/TextureView that PlayerSurface created and clips it with a View outline. */
@Composable
private fun OutlineClipEffect(surface: SpikeSurface, radius: () -> Float) {
    val host = LocalView.current
    val radiusState = rememberUpdatedState(radius)
    val target = remember { arrayOfNulls<View>(2) } // [videoView, its AndroidView holder]
    DisposableEffect(host, surface) {
        val provider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, radiusState.value())
            }
        }
        var tries = 0
        val find = object : Runnable {
            override fun run() {
                val found = findVideoView(host.rootView, surface)
                if (found == null) {
                    if (tries++ < 30) host.postDelayed(this, 16)
                    return
                }
                target[0] = found
                target[1] = found.parent as? View
                target.forEach { v -> v?.outlineProvider = provider; v?.clipToOutline = true }
            }
        }
        host.post(find)
        onDispose {
            host.removeCallbacks(find)
            target.forEach { v -> v?.clipToOutline = false; v?.outlineProvider = ViewOutlineProvider.BACKGROUND }
            target.fill(null)
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { radiusState.value() }.collect { target.forEach { it?.invalidateOutline() } }
    }
}

private fun findVideoView(root: View, surface: SpikeSurface): View? {
    val match = when (surface) {
        SpikeSurface.SURFACE -> root is SurfaceView
        SpikeSurface.TEXTURE -> root is TextureView
    }
    if (match) return root
    if (root is ViewGroup) {
        for (i in 0 until root.childCount) findVideoView(root.getChildAt(i), surface)?.let { return it }
    }
    return null
}

// ---------------------------------------------------------------------------------------------
// Fake chrome
// ---------------------------------------------------------------------------------------------

@Composable
private fun PageChrome(modifier: Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Box(modifier) {
        Column(Modifier.statusBarsPadding().padding(start = 20.dp, top = 56.dp)) {
            Text("Today, 14:32", style = MaterialTheme.typography.titleLarge, color = onSurface)
            Text("VID_20261109_143210.mp4", style = MaterialTheme.typography.bodyMedium, color = onSurface.copy(alpha = 0.7f))
        }
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 32.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf(Icons.Outlined.Share, Icons.Outlined.Edit, Icons.Outlined.Info, Icons.Outlined.Delete).forEach {
                Icon(it, contentDescription = null, tint = onSurface)
            }
        }
    }
}

@Composable
private fun FakePhotoPage(page: Int, geo: SpikeGeometry) {
    val colors = if (page == 0) listOf(Color(0xFF3A6EA5), Color(0xFF8FC1E3)) else listOf(Color(0xFFB5543C), Color(0xFFF2B880))
    Box(
        Modifier.morphBounds(geo) { 0f }.background(Brush.linearGradient(colors), RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("Photo ${page + 1}", color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun FullPlayerChrome(modifier: Modifier) {
    Box(modifier) {
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp)) {
            Box(Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))) {
                Box(Modifier.fillMaxWidth(0.35f).height(4.dp).background(Color.White, RoundedCornerShape(2.dp)))
            }
            Spacer(Modifier.height(8.dp))
            Text("Full player (swipe down or back)", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// HUD
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpikeHud(
    config: MorphSpikeConfig,
    onConfig: (MorphSpikeConfig) -> Unit,
    stats: MorphSpikeStats,
    status: String,
    videoSize: String,
    onMorph: () -> Unit,
    modifier: Modifier,
) {
    var open by remember { mutableStateOf(true) }
    val mono = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp)
    Column(
        modifier
            .statusBarsPadding()
            .padding(8.dp)
            .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(12.dp))
            .padding(8.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HudChip(config.surface.label) { onConfig(config.copy(surface = config.surface.next())) }
            HudChip("mode:" + config.mode.name.lowercase()) { onConfig(config.copy(mode = config.mode.next())) }
            HudChip("clip:" + config.clip.name.lowercase()) { onConfig(config.copy(clip = config.clip.next())) }
            HudChip("spec:" + config.spec.name.lowercase()) { onConfig(config.copy(spec = config.spec.next())) }
            HudChip("MORPH", accent = true, onClick = onMorph)
            HudChip(if (open) "hide" else "stats") { open = !open }
        }
        if (open) {
            Spacer(Modifier.height(6.dp))
            Text(status, style = mono, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
            Text(
                "surface ${stats.surfaceSize}  video $videoSize  lost(all) ${stats.lifetimeSurfaceLost}  1stFrame(all) ${stats.lifetimeFirstFrames}",
                style = mono,
                color = Color.White,
            )
            if (stats.recording) Text("recording...", style = mono, color = Color(0xFFFFD54F))
            stats.last?.let { r ->
                Text(
                    "LAST ${r.label}  ${r.durationMs}ms  vsync ${"%.1f".format(r.vsyncMs)}ms\n" +
                        "choreo: ${r.frames} frames  avg ${"%.1f".format(r.avgMs)}  p95 ${"%.1f".format(r.p95Ms)}  max ${"%.1f".format(r.maxMs)}  MISSED ${r.missedVsyncs}\n" +
                        "frameMetrics: ${r.fmFrames} frames  JANKY ${r.fmJanky}  max ${"%.1f".format(r.fmMaxMs)}ms\n" +
                        "surface: resize ${r.surfaceResizes}  LOST ${r.surfaceLost}  firstFrame ${r.firstFrames}  buffering ${r.buffering}",
                    style = mono,
                    color = if (r.surfaceLost > 0 || r.buffering > 0 || r.missedVsyncs > 2) Color(0xFFFF8A80) else Color(0xFFB9F6CA),
                )
            }
            stats.history.drop(1).forEach { r ->
                Text(
                    "${r.label}: missed ${r.missedVsyncs} janky ${r.fmJanky} max ${"%.0f".format(r.maxMs)} lost ${r.surfaceLost}",
                    style = mono,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun HudChip(text: String, accent: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier
            .background(if (accent) Color(0xFFFF6E5A) else Color.White.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
    )
}
