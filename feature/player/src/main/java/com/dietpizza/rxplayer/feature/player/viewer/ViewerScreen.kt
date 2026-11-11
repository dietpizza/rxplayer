package com.dietpizza.rxplayer.feature.player.viewer

import com.dietpizza.rxplayer.feature.player.AppThemed
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import com.dietpizza.rxplayer.core.designsystem.icon.RxIcons
import com.dietpizza.rxplayer.feature.player.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

import com.dietpizza.rxplayer.core.model.SlideshowCounts
import com.dietpizza.rxplayer.core.model.SlideshowMediaType
import com.dietpizza.rxplayer.core.model.filterForSlideshow
import com.dietpizza.rxplayer.core.ui.RxIntents
import com.dietpizza.rxplayer.core.ui.slideshow.SlideshowOptionsSheet
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.player.PlaybackController
import com.dietpizza.rxplayer.feature.player.ui.FullPlayer
import com.dietpizza.rxplayer.feature.player.ui.FullPlayerHost
import com.dietpizza.rxplayer.feature.player.ui.PlayerSheet
import com.dietpizza.rxplayer.feature.player.ui.rememberFullPlayerState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** What the viewer needs from `PlayerActivity` besides the [FullPlayerHost] services. */
internal interface ViewerHost {
    /**
     * True once the morph has settled in the full player (orientation follows the video, auto-PiP
     * may arm, HDR video may switch the window); false as soon as a collapse starts.
     */
    fun setFullPlayerShown(shown: Boolean)

    /** Back at the pager after a collapse: orientation goes back to the user/sensor. */
    fun onInlineSettled()

    /** The settled photo is HDR (gain map or HDR colour space). */
    fun setHdrPhoto(hdr: Boolean)

    fun share(item: ViewerItem)

    /** Nothing left to show (last item deleted) or back pressed at the pager. */
    fun close()
}

/**
 * The gallery viewer (plan §4 #4, §4.1) and, in the same composition, the full player it morphs
 * into. One pager over photos and videos; the settled video page hosts the window's single
 * surface and inline playback; expand / rotate-to-landscape / fling-up morph that surface into
 * [FullPlayer] (drawn as chrome over it, `externalSurface = true`), swipe-down morphs it back.
 *
 * The overflow menu's Slideshow opens the options sheet; Start plays this pager's items
 * (filtered by type), beginning with the page on screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ViewerScreen(
    viewModel: ViewerViewModel,
    request: ViewerRequest,
    controller: PlaybackController,
    playerHost: FullPlayerHost,
    host: ViewerHost,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var slideshowFrom by remember { mutableStateOf<ViewerItem?>(null) }
    slideshowFrom?.let { from -> AppThemed { ViewerSlideshowSheet(viewModel, items, from, onDismiss = { slideshowFrom = null }) } }
    val onSlideshow: (ViewerItem) -> Unit = { slideshowFrom = it }
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    // A new request (onNewIntent) starts a fresh pager at its start index.
    key(request) {
        ViewerContent(viewModel, items, request, controller, playerHost, host, onSlideshow)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ViewerContent(
    viewModel: ViewerViewModel,
    items: List<ViewerItem>,
    request: ViewerRequest,
    controller: PlaybackController,
    playerHost: FullPlayerHost,
    host: ViewerHost,
    onSlideshow: (ViewerItem) -> Unit,
) {
    val pager = rememberPagerState(initialPage = request.startIndex.coerceIn(0, items.lastIndex)) { items.size }
    val scope = rememberCoroutineScope()
    val morph = remember { MorphState() }
    val fullState = rememberFullPlayerState()
    val spec = morphSpec()
    val density = LocalDensity.current
    val windowSize by rememberUpdatedState(LocalWindowInfo.current.containerSize)

    // Card geometry: under the top bar, above the slim controls; stable insets.
    val geo = remember { ViewerGeometry() }
    val statusTop = WindowInsets.statusBarsIgnoringVisibility.getTop(density)
    val navBottom = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density)
    // Set during composition (read only in layout), so the very first frame is already right.
    remember(statusTop, navBottom, density) {
        with(density) {
            geo.top = statusTop + TopBarHeight.toPx()
            geo.bottom = navBottom + InlineControlsBottomGap.toPx() + InlineControlsHeight.toPx()
            geo.side = CARD_SIDE.toPx()
            geo.radius = CARD_RADIUS.toPx()
        }
    }

    var chromeVisible by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<ViewerItem?>(null) }
    val metas = remember { mutableStateMapOf<MediaKey, ImageMeta>() }
    val snackbar = remember { SnackbarHostState() }
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()
    val nowPlaying by controller.nowPlaying.collectAsStateWithLifecycle()
    val videoSize by controller.videoSize.collectAsStateWithLifecycle()

    val settledIndex = pager.settledPage.coerceIn(0, items.lastIndex)
    val settledItem = items[settledIndex]
    val activeKey = settledItem.key.takeIf { settledItem.isVideo && nowPlaying?.key == it }

    // Slideshow: the same pager, auto-advancing. [slideshowOn] until stopped; [slideshowPlaying]
    // is pause/resume. Videos play once inline (their normal controls still work), then advance.
    val slideshow = request.slideshow
    var slideshowOn by remember { mutableStateOf(slideshow != null) }
    var slideshowPlaying by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { if (slideshow != null) chromeVisible = false }

    LaunchedEffect(settledItem.key) {
        zoomed = false // a photo page that settles reports its own zoom right after
    }

    // Inline playback follows the page the pager is landing on (plan §4.1). There's one video
    // surface, so the next video may only load once the outgoing page has fully left the screen
    // (else it would vanish mid-slide); that's still ahead of the settle, so its first frame
    // decodes during the animation's tail. Anything else releases the player.
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    val landingIndex by remember {
        derivedStateOf {
            val target = pager.targetPage
            if (dragged || target == pager.settledPage) return@derivedStateOf pager.settledPage
            val info = pager.layoutInfo
            val stride = (info.pageSize + info.pageSpacing).toFloat()
            val offsetPx = abs(target - (pager.currentPage + pager.currentPageOffsetFraction)) * stride
            if (stride > 0f && offsetPx <= info.pageSpacing) target else pager.settledPage
        }
    }
    val landingItem = items[landingIndex.coerceIn(0, items.lastIndex)]
    LaunchedEffect(landingItem.key, landingItem.kind) {
        if (landingItem.isVideo) {
            if (controller.nowPlaying.value?.key != landingItem.key) controller.playInline(landingItem.key, loop = !slideshowOn)
        } else {
            controller.stop()
        }
    }

    // Aspect of the active video: the decoder's once known, else MediaStore's, else 16:9.
    val aspectState = rememberUpdatedState(
        if (activeKey != null && videoSize.width > 0) videoSize.aspectRatio else settledItem.aspect.takeIf { it > 0f } ?: DEFAULT_ASPECT,
    )
    val activeAspect: () -> Float = { aspectState.value }

    // region morph

    val activeState = rememberUpdatedState(activeKey)
    val shownFull = remember { mutableStateOf(false) }
    fun setShown(shown: Boolean) {
        if (shownFull.value != shown) {
            shownFull.value = shown
            host.setFullPlayerShown(shown)
        }
    }

    fun expand(velocity: Float = 0f, from: Float? = null) {
        if (activeState.value == null) return
        controller.promoteInline()
        // Next/previous/auto-next run through this album's videos (a folder album = that folder).
        controller.adoptPlaylist(items.filter { it.isVideo }.map { it.key })
        fullState.isDismissDragging = false
        fullState.showControls(true)
        morph.collapseFrom = null
        scope.launch {
            if (from != null) morph.progress.snapTo(from)
            morph.progress.animateTo(1f, spec, initialVelocity = velocity)
            setShown(true)
        }
    }

    fun collapse(fromSwipe: Boolean, velocity: Float = 0f) {
        setShown(false)
        fullState.sheet = PlayerSheet.None
        if (fromSwipe) {
            // Continue from where the finger left the video (FullPlayer keeps the offset).
            val w = windowSize.width.toFloat()
            val h = windowSize.height.toFloat()
            val full = geo.full(w, h, activeAspect())
            morph.collapseFrom = MorphMath.dismissed(full, fullState.dismissOffsetPx, h)
            fullState.dismissOffsetPx = 0f
            // isDismissDragging stays true: the full chrome stays hidden while it fades.
        }
        scope.launch {
            try {
                morph.progress.animateTo(0f, spec, initialVelocity = velocity)
            } finally {
                if (morph.progress.value == 0f) {
                    morph.collapseFrom = null
                    fullState.isDismissDragging = false
                    host.onInlineSettled()
                }
            }
        }
    }

    // Next/previous in the full player change the item: keep the pager on it, so the surface
    // stays on the active page and swiping down lands on the video that's playing.
    LaunchedEffect(nowPlaying?.key, shownFull.value) {
        val key = nowPlaying?.key ?: return@LaunchedEffect
        if (!shownFull.value || key == settledItem.key) return@LaunchedEffect
        val target = items.indexOfFirst { it.key == key }
        if (target >= 0) pager.scrollToPage(target)
    }

    var dragStart by remember { mutableFloatStateOf(0f) }
    var dragTotal by remember { mutableFloatStateOf(0f) }
    // Latest hand-driven progress; the settle starts from this, not from a possibly-pending snap.
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val dragDistance = { windowSize.height * MorphMath.DRAG_DISTANCE_FRACTION }
    // One long-lived consumer applies drag progress (conflated: at most one pending value), so a
    // drag doesn't launch a coroutine per delta.
    val dragFeed = remember { Channel<Float>(Channel.CONFLATED) }
    LaunchedEffect(dragFeed) {
        for (p in dragFeed) morph.progress.snapTo(p)
    }

    // endregion

    // Rotating to landscape on a playing video page expands it (plan §4.1). Only the transition
    // counts, so collapsing while held in landscape doesn't bounce straight back.
    val orientation = LocalConfiguration.current.orientation
    var lastOrientation by remember { mutableIntStateOf(orientation) }
    LaunchedEffect(orientation) {
        val wasLandscape = lastOrientation == Configuration.ORIENTATION_LANDSCAPE
        lastOrientation = orientation
        // Landscape videos only: a portrait video would be turned straight back (decision 18).
        if (orientation == Configuration.ORIENTATION_LANDSCAPE && !wasLandscape && morph.isInline &&
            activeState.value != null && activeAspect() >= 1f
        ) {
            expand()
        }
    }

    // At the pager the system bars always stay (transparent): hiding the chrome only fades the
    // app's bars, so edge back gestures keep working. FullPlayer drives them when full.
    LaunchedEffect(morph.isInline) {
        if (morph.isInline) playerHost.setSystemBarsVisible(true)
    }

    // Only the settled page's entry: a neighbour's decode writes [metas] without recomposing here.
    val settledMeta by remember(settledItem.key) { derivedStateOf { metas[settledItem.key] } }

    // Ultra HDR: window colour mode follows the settled photo.
    val settledHdr = !settledItem.isVideo && settledMeta?.hdr == true
    LaunchedEffect(settledHdr) { host.setHdrPhoto(settledHdr) }

    BackHandler(enabled = !morph.isInline) { collapse(fromSwipe = false) }

    val userScroll by remember { derivedStateOf { morph.isInline && !zoomed } }

    // Slideshow advance: photos after N seconds, videos when they end (or fail). Restarts on any
    // page change (a manual swipe too); holds while paused, zoomed or in the full player.
    if (slideshow != null) {
        val view = LocalView.current
        DisposableEffect(slideshowOn, slideshowPlaying) {
            view.keepScreenOn = slideshowOn && slideshowPlaying
            onDispose { view.keepScreenOn = false }
        }
        LaunchedEffect(settledIndex, slideshowOn, slideshowPlaying, zoomed, morph.isInline) {
            if (!slideshowOn || !slideshowPlaying || zoomed || !morph.isInline) return@LaunchedEffect
            if (settledItem.isVideo) {
                val key = settledItem.key
                combine(controller.nowPlaying, controller.isEnded, controller.error) { np, ended, err ->
                    (np?.key == key && ended) || err?.fatal == true
                }.first { it }
            } else {
                delay(slideshow.secondsPerPhoto * 1_000L)
            }
            val next = settledIndex + 1
            when {
                next <= items.lastIndex -> pager.animateScrollToPage(next)
                slideshow.loop -> pager.scrollToPage(0)
                else -> slideshowOn = false
            }
        }
    }

    // The single video surface (see ViewerVideoSurface): which page it belongs to, and its aspect.
    val nowPlayingKey = nowPlaying?.key
    val surfaceIndex = remember(items, nowPlayingKey) {
        nowPlayingKey?.let { k -> items.indexOfFirst { it.isVideo && it.key == k } } ?: -1
    }
    val surfaceIndexState = rememberUpdatedState(surfaceIndex)
    val surfaceAspectState = rememberUpdatedState(
        if (videoSize.width > 0) {
            videoSize.aspectRatio
        } else {
            items.getOrNull(surfaceIndex)?.aspect?.takeIf { it > 0f } ?: DEFAULT_ASPECT
        },
    )
    val pageSpacingPx = with(density) { PAGE_SPACING.toPx() }
    // Swipes that start in the system back-gesture strips belong to back, not to paging.
    val gestureInsets = WindowInsets.systemGestures
    val edgeLeftPx = maxOf(gestureInsets.getLeft(density, LayoutDirection.Ltr).toFloat(), with(density) { MIN_EDGE.toPx() })
    val edgeRightPx = maxOf(gestureInsets.getRight(density, LayoutDirection.Ltr).toFloat(), with(density) { MIN_EDGE.toPx() })
    val currentZoomed by rememberUpdatedState(zoomed)
    val hasVideo = remember(items) { items.any { it.isVideo } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasVideo) {
            ViewerVideoSurface(
                controller = controller,
                pager = pager,
                surfaceIndex = { surfaceIndexState.value },
                pageSpacingPx = pageSpacingPx,
                geo = geo,
                morph = morph,
                fullState = fullState,
                aspect = { surfaceAspectState.value },
            )
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier
                .fillMaxSize()
                .testTag("viewer_pager")
                .ignoreEdgeSwipes(edgeLeftPx, edgeRightPx, enabled = { !currentZoomed }),
            key = { items[it].key.value },
            beyondViewportPageCount = 1,
            pageSpacing = PAGE_SPACING,
            userScrollEnabled = userScroll,
        ) { page ->
            val item = items[page]
            val settled = page == pager.settledPage
            when (item.kind) {
                PageKind.Photo -> PhotoPage(
                    item = item,
                    images = viewModel.images,
                    settled = settled,
                    onTap = { chromeVisible = !chromeVisible },
                    onSwipeUp = { showInfo = true },
                    onZoomedChange = { zoomed = it },
                    onDecoded = { metas[item.key] = it.meta() },
                )
                PageKind.Video -> VideoPage(
                    item = item,
                    active = settled && item.key == activeKey,
                    controller = controller,
                    geo = geo,
                    morph = morph,
                    fullState = fullState,
                    chromeVisible = chromeVisible,
                    aspect = if (settled) activeAspect else ({ item.aspect.takeIf { it > 0f } ?: DEFAULT_ASPECT }),
                    onTap = { chromeVisible = !chromeVisible },
                    onExpand = { expand() },
                    onDragStarted = {
                        if (morph.isInline) {
                            morph.dragging = true
                            dragStart = morph.progress.value
                            dragTotal = 0f
                            dragProgress = dragStart
                        }
                    },
                    onDrag = { delta ->
                        if (morph.dragging) {
                            dragTotal += delta
                            dragProgress = MorphMath.dragProgress(dragStart, dragTotal, dragDistance())
                            dragFeed.trySend(dragProgress)
                        }
                    },
                    onDragStopped = { v ->
                        if (morph.dragging) {
                            morph.dragging = false
                            // Drop a snap still queued: it would cancel the settle below.
                            dragFeed.tryReceive()
                            val from = dragProgress
                            val pv = MorphMath.velocityToProgress(v, dragDistance())
                            if (MorphMath.settleTarget(from, pv) == 1f) {
                                expand(pv, from = from)
                            } else {
                                scope.launch {
                                    morph.progress.snapTo(from)
                                    morph.progress.animateTo(0f, spec, initialVelocity = pv)
                                }
                            }
                        }
                    },
                )
            }
        }

        // Its own scope: only the chrome recomposes when the swipe crosses half a page.
        CurrentPageChrome(
            pager = pager,
            items = items,
            visible = chromeVisible,
            favourites = favourites,
            morph = morph,
            onBack = host::close,
            onFavourite = viewModel::toggleFavourite,
            onSlideshow = onSlideshow,
            onShare = host::share,
            onInfo = { showInfo = true },
            onDelete = { confirmDelete = it },
        )

        // The full player's chrome over the morphing surface. Composed only while not inline.
        if (!morph.isInline && activeKey != null) {
            FullPlayer(
                controller = controller,
                host = playerHost,
                onMinimize = { collapse(fromSwipe = true) },
                onBack = { collapse(fromSwipe = false) },
                modifier = Modifier.graphicsLayer {
                    alpha = MorphMath.fullChromeAlpha(morph.progress.value)
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
                state = fullState,
                animateExit = false,
                externalSurface = true,
            )
        }

        if (slideshowOn && morph.isInline) {
            SlideshowPill(
                visible = chromeVisible,
                playing = slideshowPlaying,
                onPlayPause = {
                    slideshowPlaying = !slideshowPlaying
                    // Pausing the slideshow pauses the video on screen too, and vice versa.
                    if (settledItem.isVideo) {
                        if (slideshowPlaying) controller.play() else controller.pause()
                    }
                },
                onStop = { slideshowOn = false },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
    }

    if (showInfo && morph.isInline) AppThemed {
        ViewerInfoSheet(
            item = settledItem,
            meta = settledMeta,
            images = viewModel.images,
            controller = controller,
            videoLoaded = activeKey != null,
            onDismiss = { showInfo = false },
        )
    }

    confirmDelete?.let { item -> AppThemed {
        DeleteDialog(
            item = item,
            onDismiss = { confirmDelete = null },
            onConfirm = {
                confirmDelete = null
                // Release the file first if it's the playing video.
                if (controller.nowPlaying.value?.key == item.key) controller.stop()
                scope.launch {
                    val ok = viewModel.delete(item.key)
                    if (!ok) {
                        snackbar.showSnackbar("Couldn't delete “${item.title}”")
                    } else if (viewModel.items.value.isEmpty()) {
                        host.close()
                    }
                }
            },
        )
    } }
}

@Composable
private fun CurrentPageChrome(
    pager: PagerState,
    items: List<ViewerItem>,
    visible: Boolean,
    favourites: Set<MediaKey>,
    morph: MorphState,
    onBack: () -> Unit,
    onFavourite: (MediaKey) -> Unit,
    onSlideshow: (ViewerItem) -> Unit,
    onShare: (ViewerItem) -> Unit,
    onInfo: () -> Unit,
    onDelete: (ViewerItem) -> Unit,
) {
    val item = items[pager.currentPage.coerceIn(0, items.lastIndex)]
    val subtitle = remember(item.gallery?.dateTakenMs) { item.gallery?.dateTakenMs?.takeIf { it > 0 }?.let(::formatDate) }
    ViewerChrome(
        visible = visible,
        item = item,
        subtitle = subtitle,
        isFavourite = item.key in favourites,
        morphAlpha = { MorphMath.inlineChromeAlpha(morph.progress.value) },
        onBack = onBack,
        onFavourite = { onFavourite(item.key) },
        onSlideshow = { onSlideshow(item) },
        onShare = { onShare(item) },
        onInfo = onInfo,
        onDelete = { onDelete(item) },
    )
}

private fun formatDate(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))

private const val DEFAULT_ASPECT = 16f / 9f
private val CARD_SIDE = 12.dp
private val CARD_RADIUS = 12.dp
private val PAGE_SPACING = 16.dp
private val MIN_EDGE = 24.dp

/**
 * Swallows the drag of a finger that lands within [leftPx]/[rightPx] of the sides, before the
 * pager sees it, so an edge back swipe never slides to the next item. Taps there still work.
 */
private fun Modifier.ignoreEdgeSwipes(leftPx: Float, rightPx: Float, enabled: () -> Boolean): Modifier =
    pointerInput(leftPx, rightPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val x = down.position.x
            if (!enabled() || (x > leftPx && x < size.width - rightPx)) return@awaitEachGesture
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.positionChanged()) change.consume()
                if (!change.pressed) break
            }
        }
    }

/** The slideshow options sheet over the viewer's own items; Start saves the options and launches. */
@Composable
private fun ViewerSlideshowSheet(viewModel: ViewerViewModel, items: List<ViewerItem>, from: ViewerItem, onDismiss: () -> Unit) {
    val options by viewModel.slideshowOptions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val typeOf: (ViewerItem) -> SlideshowMediaType = { SlideshowMediaType.of(it.mimeType, it.kind == PageKind.Video) }
    val counts = remember(items) { SlideshowCounts.of(items, typeOf) }
    SlideshowOptionsSheet(
        initial = options,
        counts = counts,
        onStart = { chosen ->
            onDismiss()
            viewModel.saveSlideshowOptions(chosen)
            val kept = items.filterForSlideshow(chosen, typeOf)
            if (kept.isNotEmpty()) {
                // Start on the page on screen when it's included; otherwise anywhere.
                val start = kept.indexOfFirst { it.key == from.key }
                context.startActivity(RxIntents.slideshow(context, kept.map { it.key }, start, chosen))
            }
        },
        onDismissRequest = onDismiss,
    )
}

/**
 * The slideshow's only control: a small pill on the right edge with pause/resume and stop. It
 * shows and hides with the viewer chrome (tap the photo), so the slideshow itself stays clean.
 */
@Composable
private fun SlideshowPill(
    visible: Boolean,
    playing: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(motion.defaultEffectsSpec()),
        exit = fadeOut(motion.defaultEffectsSpec()),
        modifier = modifier.padding(end = 12.dp),
    ) {
        Column(
            Modifier
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (playing) RxIcons.Pause else RxIcons.PlayArrow,
                    contentDescription = stringResource(if (playing) R.string.viewer_slideshow_pause else R.string.viewer_slideshow_resume),
                    tint = Color.White,
                )
            }
            IconButton(onClick = onStop) {
                Icon(RxIcons.Close, contentDescription = stringResource(R.string.viewer_slideshow_stop), tint = Color.White)
            }
        }
    }
}
