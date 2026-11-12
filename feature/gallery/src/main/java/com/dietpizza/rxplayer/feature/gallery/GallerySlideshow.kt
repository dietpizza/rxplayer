package com.dietpizza.rxplayer.feature.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.FloatingActionButton
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
internal val SlideshowFabClearance = 88.dp

/** The Gallery / album "Slideshow" FAB; scales away while [visible] is false (selection mode). */
@Composable
internal fun SlideshowFab(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
        exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        modifier = modifier,
    ) {
        FloatingActionButton(onClick = onClick) {
            Icon(RxIcons.PlayArrow, contentDescription = stringResource(R.string.gallery_slideshow))
        }
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
