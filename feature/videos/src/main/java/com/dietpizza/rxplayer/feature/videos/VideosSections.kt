package com.dietpizza.rxplayer.feature.videos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dietpizza.rxplayer.core.designsystem.component.RxEmptyState
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.ui.RxIntents
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

// ---- Playlists ---------------------------------------------------------------------------------

@Composable
internal fun PlaylistsSection(
    contentPadding: PaddingValues,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var creating by rememberSaveable { mutableStateOf(false) }
    val current = rows
    if (current == null) {
        DelayedLoadingIndicator(Modifier.padding(contentPadding))
    } else {
        // "New playlist" is the first row of the list, so the action sits where the playlists are.
        val count = current.size + 1
        LazyColumn(
            state = rememberLazyListState(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "new", contentType = "new") {
                SegmentedListItem(
                    onClick = { creating = true },
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = count),
                    verticalAlignment = Alignment.CenterVertically,
                    leadingContent = { IconTile(Icons.Rounded.Add) },
                ) { Text(stringResource(R.string.playlist_new)) }
            }
            itemsIndexed(current, key = { _, p -> p.id }, contentType = { _, _ -> "playlist" }) { index, playlist ->
                SegmentedListItem(
                    onClick = { context.startActivity(PlaylistActivity.intent(context, playlist.id, playlist.name)) },
                    shapes = ListItemDefaults.segmentedShapes(index = index + 1, count = count),
                    verticalAlignment = Alignment.CenterVertically,
                    leadingContent = {
                        if (playlist.coverUri != null) {
                            VideoThumbnail(
                                contentUri = playlist.coverUri,
                                duration = null,
                                resumeFraction = 0f,
                                shape = MaterialTheme.shapes.medium,
                                requestWidth = FolderThumbWidth,
                                modifier = Modifier.size(FolderThumbWidth, FolderThumbWidth * 9 / 16),
                            )
                        } else {
                            IconTile(Icons.AutoMirrored.Rounded.PlaylistPlay)
                        }
                    },
                    supportingContent = { Text(playlist.summary, maxLines = 1) },
                ) { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
    if (creating) {
        NameDialog(
            title = stringResource(R.string.playlist_new),
            initial = "",
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = {
                viewModel.create(it)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
}

/** Thumbnail-sized tile with an icon, for rows without a picture. */
@Composable
private fun IconTile(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(FolderThumbWidth, FolderThumbWidth * 9 / 16)
            .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

// ---- History -----------------------------------------------------------------------------------

@Composable
internal fun HistorySection(
    contentPadding: PaddingValues,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val current = rows
    when {
        current == null -> DelayedLoadingIndicator(Modifier.padding(contentPadding))
        current.isEmpty() -> RxEmptyState(
            icon = Icons.Rounded.History,
            title = stringResource(R.string.history_empty_title),
            body = stringResource(R.string.history_empty_body),
            modifier = Modifier.padding(contentPadding),
        )
        else -> {
            val count = current.size
            LazyColumn(
                state = rememberLazyListState(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header", contentType = "header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp),
                    ) {
                        Text(
                            stringResource(R.string.history_recent),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { heading() },
                        )
                        TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.history_clear)) }
                    }
                }
                itemsIndexed(current, key = { _, r -> r.video.key }, contentType = { _, _ -> "video" }) { index, row ->
                    val video = row.video
                    SegmentedListItem(
                        onClick = {
                            context.startActivity(RxIntents.player(context, listOf(MediaKey(video.key)), 0))
                        },
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
                        verticalAlignment = Alignment.CenterVertically,
                        leadingContent = {
                            VideoThumbnail(
                                contentUri = video.contentUri,
                                duration = video.duration,
                                resumeFraction = video.resumeFraction,
                                shape = MaterialTheme.shapes.medium,
                                requestWidth = ListThumbWidth,
                                dimmed = video.watched,
                                modifier = Modifier.size(ListThumbWidth, ListThumbWidth * 9 / 16),
                            )
                        },
                        supportingContent = { Text(row.summary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    ) {
                        Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.history_clear_title),
            text = stringResource(R.string.history_clear_body),
            confirmLabel = stringResource(R.string.history_clear),
            onConfirm = {
                viewModel.clear()
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
    }
}
