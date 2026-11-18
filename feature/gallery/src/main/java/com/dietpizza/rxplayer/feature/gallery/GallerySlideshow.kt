package com.dietpizza.rxplayer.feature.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dietpizza.rxplayer.core.designsystem.icon.RxIcons
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.SlideshowCounts
import com.dietpizza.rxplayer.core.model.SlideshowMediaType
import com.dietpizza.rxplayer.core.model.SlideshowOptions
import com.dietpizza.rxplayer.core.ui.RxIntents
import com.dietpizza.rxplayer.core.ui.slideshow.SlideshowOptionsSheet
import com.dietpizza.rxplayer.core.ui.slideshow.rememberSheetLauncher
import kotlinx.coroutines.flow.StateFlow

/** Extra bottom padding for a grid under the slideshow FAB, so its last row can scroll clear. */
internal val SlideshowFabClearance = 104.dp

/**
 * Tracks scroll direction for [SlideshowFab]: collapsed to its icon while the content scrolls
 * down, expanded with its label again on any scroll back up. Attach [connection] with
 * `Modifier.nestedScroll` around the scrolling content.
 */
@Stable
internal class SlideshowFabState {
    var expanded by mutableStateOf(true)
        private set

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < -SCROLL_SLOP) expanded = false
            else if (available.y > SCROLL_SLOP) expanded = true
            return Offset.Zero
        }
    }

    private companion object {
        const val SCROLL_SLOP = 1f
    }
}

@Composable
internal fun rememberSlideshowFabState(): SlideshowFabState = remember { SlideshowFabState() }

/**
 * The Gallery / album "Slideshow" FAB: a medium extended FAB that folds to its icon while
 * scrolling down ([state]) and scales away while [visible] is false (selection mode).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SlideshowFab(
    visible: Boolean,
    onClick: () -> Unit,
    state: SlideshowFabState,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
        exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        modifier = modifier,
    ) {
        MediumExtendedFloatingActionButton(
            onClick = onClick,
            expanded = state.expanded,
            icon = { Icon(RxIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp)) },
            text = { Text(stringResource(R.string.gallery_slideshow)) },
        )
    }
}

/**
 * Where a slideshow's items come from (the Gallery tab, an album). [options] are the last used
 * ones; [counts] is collected only while the sheet shows; [prepare] saves the chosen options
 * and returns the matching keys in display order.
 */
internal interface SlideshowSource {
    val options: StateFlow<SlideshowOptions>
    val counts: StateFlow<SlideshowCounts?>

    suspend fun prepare(options: SlideshowOptions): List<MediaKey>
}

/** The options sheet for [source]; Start saves, filters and launches the slideshow. */
@Composable
internal fun SlideshowSheetHost(source: SlideshowSource, onDismiss: () -> Unit) {
    val options by source.options.collectAsStateWithLifecycle()
    val counts by source.counts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launcher = rememberSheetLauncher()
    SlideshowOptionsSheet(
        initial = options,
        counts = counts,
        onStart = { chosen ->
            onDismiss()
            launcher.launch {
                val keys = source.prepare(chosen)
                if (keys.isEmpty()) null else RxIntents.slideshow(context, keys, RxIntents.SLIDESHOW_ANY_START, chosen)
            }
        },
        onDismissRequest = onDismiss,
    )
}

/** Slideshow type of a grid tile. */
internal val MediaCell.slideshowType: SlideshowMediaType
    get() = if (isGif) SlideshowMediaType.GIF else SlideshowMediaType.of(mimeType, isVideo)

/** Counts per type over the grid's tiles. */
internal fun AlbumGrid.slideshowCounts(): SlideshowCounts =
    SlideshowCounts.of(cells.filterIsInstance<MediaCell>()) { it.slideshowType }

/** The grid's keys [options] includes, in display order. */
internal fun AlbumGrid.slideshowKeys(options: SlideshowOptions): List<MediaKey> {
    val out = ArrayList<MediaKey>(keys.size)
    for (cell in cells) {
        if (cell is MediaCell && options.includes(cell.slideshowType)) out += keys[cell.index]
    }
    return out
}
