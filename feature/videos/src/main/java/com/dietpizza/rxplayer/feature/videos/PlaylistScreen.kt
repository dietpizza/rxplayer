package com.dietpizza.rxplayer.feature.videos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dietpizza.rxplayer.core.designsystem.component.RxEmptyState
import com.dietpizza.rxplayer.core.designsystem.theme.RxTheme
import com.dietpizza.rxplayer.core.model.AppearanceSettings
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.ThemeMode

@Composable
internal fun PlaylistScreen(
    onBack: () -> Unit,
    onPlay: (playlist: List<MediaKey>, index: Int) -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    if (uiState is PlaylistUiState.Gone) {
        LaunchedEffect(Unit) { onBack() }
    }

    PlaylistContent(
        uiState = uiState,
        onBack = onBack,
        onPlay = onPlay,
        onRename = { renaming = true },
        onDelete = { confirmDelete = true },
        onRemove = viewModel::remove,
        onCommitOrder = viewModel::setOrder,
    )

    if (renaming) {
        NameDialog(
            title = stringResource(R.string.playlist_rename),
            initial = uiState.title,
            confirmLabel = stringResource(R.string.action_rename),
            onConfirm = {
                viewModel.rename(it)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.playlist_delete_title),
            text = stringResource(R.string.playlist_delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
                onBack()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
internal fun PlaylistContent(
    uiState: PlaylistUiState,
    onBack: () -> Unit,
    onPlay: (playlist: List<MediaKey>, index: Int) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onRemove: (key: String) -> Unit,
    onCommitOrder: (keys: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = uiState as? PlaylistUiState.Content
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val background = MaterialTheme.colorScheme.surfaceContainer
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = background,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(uiState.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = { Text(content?.summary.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.folder_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { content?.let { onPlay(it.playable, 0) } },
                        enabled = content != null && content.playable.isNotEmpty(),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.playlist_play_all))
                    }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }, enabled = content != null) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.playlist_rename)) },
                                onClick = {
                                    menu = false
                                    onRename()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.playlist_delete)) },
                                onClick = {
                                    menu = false
                                    onDelete()
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    scrolledContainerColor = background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        when {
            content == null -> DelayedLoadingIndicator(Modifier.padding(innerPadding))
            content.items.isEmpty() -> RxEmptyState(
                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                title = stringResource(R.string.playlist_empty_title),
                body = stringResource(R.string.playlist_empty_body),
                modifier = Modifier.padding(innerPadding),
            )
            else -> ReorderableItems(
                content = content,
                contentPadding = listPadding(innerPadding, PaddingValues()),
                onPlay = { index ->
                    val playable = content.playableIndex(index)
                    if (playable >= 0) onPlay(content.playable, playable)
                },
                onRemove = onRemove,
                onCommitOrder = onCommitOrder,
            )
        }
    }
}

@Composable
private fun ReorderableItems(
    content: PlaylistUiState.Content,
    contentPadding: PaddingValues,
    onPlay: (index: Int) -> Unit,
    onRemove: (key: String) -> Unit,
    onCommitOrder: (keys: List<String>) -> Unit,
) {
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState)
    // Order shown while dragging and until the saved order comes back from the database, so the
    // list never flashes the old order after a drop.
    var pending by remember { mutableStateOf<List<PlaylistItemRow>?>(null) }
    val items = pending ?: content.items
    val dragging = reorder.draggingKey != null
    LaunchedEffect(content.items, dragging) {
        val p = pending
        if (!dragging && p != null && p.map { it.key } == content.items.map { it.key }) pending = null
    }
    reorder.onMove = { from, to ->
        val base = pending ?: content.items
        pending = base.toMutableList().apply { add(to, removeAt(from)) }
    }
    reorder.onDragEnd = {
        val p = pending
        if (p != null) {
            val keys = p.map { it.key }
            if (keys == content.items.map { it.key }) pending = null else onCommitOrder(keys)
        }
    }

    val count = items.size
    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(items, key = { _, r -> r.key }, contentType = { _, _ -> "video" }) { index, row ->
            PlaylistItem(
                row = row,
                index = index,
                count = count,
                onClick = { onPlay(index) },
                onRemove = {
                    pending = null
                    onRemove(row.key)
                },
                handle = Modifier.reorderHandle(reorder, row.key),
                modifier = reorderableItem(reorder, row.key),
            )
        }
    }
}

@Composable
private fun PlaylistItem(
    row: PlaylistItemRow,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    handle: Modifier,
    modifier: Modifier = Modifier,
) {
    val video = row.video
    SegmentedListItem(
        onClick = onClick,
        enabled = true,
        modifier = modifier,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = {
            VideoThumbnail(
                contentUri = video?.contentUri,
                duration = video?.duration,
                resumeFraction = video?.resumeFraction ?: 0f,
                shape = MaterialTheme.shapes.medium,
                requestWidth = ListThumbWidth,
                dimmed = video?.watched == true,
                modifier = Modifier.size(PlaylistThumbWidth, PlaylistThumbWidth * 9 / 16),
            )
        },
        supportingContent = {
            Text(
                if (video == null) stringResource(R.string.playlist_unavailable) else video.size,
                maxLines = 1,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.playlist_remove))
                }
                Box(handle.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.DragHandle, contentDescription = stringResource(R.string.playlist_reorder))
                }
            }
        },
    ) {
        Text(
            text = video?.title ?: row.fallbackTitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (video == null || video.watched) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val PlaylistThumbWidth = 104.dp

@Preview(name = "Playlist")
@Composable
private fun PlaylistPreview() {
    val rows = listOf(
        PlaylistItemRow("file:///a/1.mp4", VideoRow("file:///a/1.mp4", "", "Episode 1", "42:10", "1.4 GB", "1080p", false, 0.4f, false), "1.mp4"),
        PlaylistItemRow("file:///a/2.mp4", null, "2.mp4"),
        PlaylistItemRow("file:///a/3.mp4", VideoRow("file:///a/3.mp4", "", "Episode 3", "41:55", "1.3 GB", "4K", true, 0f, false), "3.mp4"),
    )
    RxTheme(AppearanceSettings(themeMode = ThemeMode.LIGHT, dynamicColor = false)) {
        PlaylistContent(
            uiState = PlaylistUiState.Content("Watch later", "3 videos", rows),
            onBack = {},
            onPlay = { _, _ -> },
            onRename = {},
            onDelete = {},
            onRemove = {},
            onCommitOrder = {},
        )
    }
}
