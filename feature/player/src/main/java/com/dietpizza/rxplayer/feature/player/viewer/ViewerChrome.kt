package com.dietpizza.rxplayer.feature.player.viewer

import com.dietpizza.rxplayer.feature.player.AppThemed
import com.dietpizza.rxplayer.core.designsystem.icon.RxIcons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dietpizza.rxplayer.core.designsystem.component.RxMenu
import com.dietpizza.rxplayer.core.designsystem.component.RxMenuItem
import com.dietpizza.rxplayer.core.player.PlaybackController
import com.dietpizza.rxplayer.feature.player.R

/** Top bar height (below the status bar). */
internal val TopBarHeight = 64.dp

private val Scrim = Color.Black.copy(alpha = 0.55f)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Modifier.chromeInsets(): Modifier =
    // Ignoring visibility: the chrome doesn't jump when the bars hide with it.
    windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout))

/**
 * Viewer chrome (plan §4 #4): one top bar with back, title and date, a favourite heart, and an
 * overflow menu holding the rest (slideshow, share, details, delete). There's no bottom action
 * row; the bottom scrim only keeps the navigation bar (and a video's inline controls) legible.
 * Fades in and out with a tap, and out with the morph (alpha in a layer, read per frame in draw
 * only).
 */
@Composable
internal fun ViewerChrome(
    visible: Boolean,
    item: ViewerItem?,
    subtitle: String?,
    isFavourite: Boolean,
    morphAlpha: () -> Float,
    onBack: () -> Unit,
    onFavourite: () -> Unit,
    onSlideshow: () -> Unit,
    onShare: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(motion.defaultEffectsSpec()),
        exit = fadeOut(motion.defaultEffectsSpec()),
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = morphAlpha()
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Brush.verticalGradient(listOf(Scrim, Color.Transparent))),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Scrim))),
            )
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .chromeInsets()
                    .fillMaxWidth()
                    .height(TopBarHeight)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(IconButtonDefaults.mediumContainerSize())) {
                    Icon(RxIcons.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(
                        item?.title.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 1,
                        )
                    }
                }
                if (item != null) {
                    if (item.canFavourite) {
                        IconButton(onClick = onFavourite, modifier = Modifier.size(IconButtonDefaults.mediumContainerSize())) {
                            Icon(
                                if (isFavourite) RxIcons.Favorite else RxIcons.FavoriteBorder,
                                contentDescription = stringResource(
                                    if (isFavourite) R.string.viewer_unfavourite else R.string.viewer_favourite,
                                ),
                                tint = Color.White,
                            )
                        }
                    }
                    ViewerOverflowMenu(item, onSlideshow, onShare, onInfo, onDelete)
                }
            }
        }
    }
}

/** The top bar's three-dot button and its menu: slideshow, share, details; delete on its own. */
@Composable
private fun ViewerOverflowMenu(
    item: ViewerItem,
    onSlideshow: () -> Unit,
    onShare: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val slideshow = stringResource(R.string.viewer_slideshow)
    val share = stringResource(R.string.viewer_share)
    val details = stringResource(R.string.viewer_details)
    val delete = stringResource(R.string.viewer_delete)
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(IconButtonDefaults.mediumContainerSize())) {
            Icon(RxIcons.MoreVert, contentDescription = stringResource(R.string.viewer_more_options), tint = Color.White)
        }
        AppThemed {
        RxMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            groups = listOf(
                buildList {
                    add(RxMenuItem(slideshow, onSlideshow, RxIcons.Slideshow))
                    if (item.shareUri != null) add(RxMenuItem(share, onShare, RxIcons.Share))
                    add(RxMenuItem(details, onInfo, RxIcons.Info))
                },
                if (item.canDelete) listOf(RxMenuItem(delete, onDelete, RxIcons.Delete, destructive = true)) else emptyList(),
            ),
        )
        }
    }
}

/** Info sheet (plan §4 #12): file and camera details. Never shows location. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ViewerInfoSheet(
    item: ViewerItem,
    meta: ImageMeta?,
    images: ViewerImages,
    controller: PlaybackController,
    videoLoaded: Boolean,
    onDismiss: () -> Unit,
) {
    val rows by produceState<List<Pair<String, String>>?>(null, item, meta, videoLoaded) {
        val base = images.info(item, meta)
        val codec = if (item.isVideo && videoLoaded) {
            controller.mediaInfo()?.rows.orEmpty().filter { it.first in VIDEO_ROWS }
        } else {
            emptyList()
        }
        value = if (codec.isEmpty()) {
            base
        } else {
            val at = base.indexOfFirst { it.first == "Type" }.let { if (it < 0) base.size else it + 1 }
            base.take(at) + codec + base.drop(at)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                "Details",
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            val list = rows
            if (list == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(40.dp))
                }
            } else {
                SelectionContainer {
                    Column(
                        Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        for ((label, value) in list) {
                            Column {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(value, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

private val VIDEO_ROWS = setOf("Video", "Audio", "Frame rate", "HDR", "Video bitrate", "Overall bitrate")

/** Permanent delete confirmation (decision 20: no trash). */
@Composable
internal fun DeleteDialog(item: ViewerItem, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item.isVideo) "Delete this video?" else "Delete this photo?") },
        text = { Text("“${item.title}” will be permanently deleted from this device.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
