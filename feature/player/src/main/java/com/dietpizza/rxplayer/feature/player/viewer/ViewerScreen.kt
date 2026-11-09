package com.dietpizza.rxplayer.feature.player.viewer

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
import androidx.compose.ui.unit.dp
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
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    // A new request (onNewIntent) starts a fresh pager at its start index.
    key(request) {
        ViewerContent(viewModel, items, request, controller, playerHost, host)
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
) {
    val pager = rememberPagerState(initialPage = request.startIndex.coerceIn(0, items.lastIndex)) { items.size }
    val scope = rememberCoroutineScope()
    val morph = remember { MorphState() }
    val fullState = rememberFullPlayerState()
    val spec = morphSpec()
    val density = LocalDensity.current
    val windowSize by rememberUpdatedState(LocalWindowInfo.current.containerSize)

    // Card geometry: under the top bar, above the slim controls + action row; stable insets.
    val geo = remember { ViewerGeometry() }
    val statusTop = WindowInsets.statusBarsIgnoringVisibility.getTop(density)
    val navBottom = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density)
    // Set during composition (read only in layout), so the very first frame is already right.
    remember(statusTop, navBottom, density) {
        with(density) {
            geo.top = statusTop + TopBarHeight.toPx()
            geo.bottom = navBottom + ActionRowHeight.toPx() + InlineControlsHeight.toPx()
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

    // Inline playback follows the settled page (plan §4.1); anything else releases the player.
    LaunchedEffect(settledItem.key, settledItem.kind) {
        zoomed = false // a photo page that settles reports its own zoom right after
        if (settledItem.isVideo) {
            if (controller.nowPlaying.value?.key != settledItem.key) controller.playInline(settledItem.key)
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

    fun expand(velocity: Float = 0f) {
        if (activeState.value == null) return
        controller.promoteInline()
        fullState.isDismissDragging = false
        fullState.showControls(true)
        morph.collapseFrom = null
        scope.launch {
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

    var dragStart by remember { mutableFloatStateOf(0f) }
    var dragTotal by remember { mutableFloatStateOf(0f) }
    val dragDistance = { windowSize.height * MorphMath.DRAG_DISTANCE_FRACTION }

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

    // System bars follow the viewer chrome while at the pager (FullPlayer drives them when full).
    LaunchedEffect(chromeVisible, morph.isInline) {
        if (morph.isInline) playerHost.setSystemBarsVisible(chromeVisible)
    }

    // Ultra HDR: window colour mode follows the settled photo.
    val settledHdr = !settledItem.isVideo && metas[settledItem.key]?.hdr == true
    LaunchedEffect(settledHdr) { host.setHdrPhoto(settledHdr) }

    BackHandler(enabled = !morph.isInline) { collapse(fromSwipe = false) }

    val userScroll by remember { derivedStateOf { morph.isInline && !zoomed } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
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
                        }
                    },
                    onDrag = { delta ->
                        if (morph.dragging) {
                            dragTotal += delta
                            val p = MorphMath.dragProgress(dragStart, dragTotal, dragDistance())
                            scope.launch { morph.progress.snapTo(p) }
                        }
                    },
                    onDragStopped = { v ->
                        if (morph.dragging) {
                            morph.dragging = false
                            val pv = MorphMath.velocityToProgress(v, dragDistance())
                            if (MorphMath.settleTarget(morph.progress.value, pv) == 1f) {
                                expand(pv)
                            } else {
                                scope.launch { morph.progress.animateTo(0f, spec, initialVelocity = pv) }
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

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
    }

    if (showInfo && morph.isInline) {
        ViewerInfoSheet(
            item = settledItem,
            meta = metas[settledItem.key],
            images = viewModel.images,
            controller = controller,
            videoLoaded = activeKey != null,
            onDismiss = { showInfo = false },
        )
    }

    confirmDelete?.let { item ->
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
    }
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
