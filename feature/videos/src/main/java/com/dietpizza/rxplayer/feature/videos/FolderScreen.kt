package com.dietpizza.rxplayer.feature.videos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
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
internal fun FolderScreen(
    onBack: () -> Unit,
    onPlay: (playlist: List<MediaKey>, index: Int) -> Unit,
    viewModel: FolderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FolderContent(
        uiState = uiState,
        onBack = onBack,
        onPlay = { index -> (uiState as? FolderUiState.Content)?.let { onPlay(it.playlist, index) } },
    )
}

@Composable
internal fun FolderContent(
    uiState: FolderUiState,
    onBack: () -> Unit,
    onPlay: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    initialGrid: Boolean = false,
) {
    // Session-only (survives rotation / process death via saved state, not app restarts).
    var grid by rememberSaveable { mutableStateOf(initialGrid) }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val background = MaterialTheme.colorScheme.surfaceContainer

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = background,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(uiState.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                // Always present (empty while loading) so the bar's height doesn't jump when data lands.
                subtitle = { Text((uiState as? FolderUiState.Content)?.summary.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.folder_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            // Keep the same video at the top across the switch; applied on the
                            // next measure, so there's no frame at the old position.
                            if (grid) {
                                listState.requestScrollToItem(gridState.firstVisibleItemIndex)
                            } else {
                                gridState.requestScrollToItem(listState.firstVisibleItemIndex)
                            }
                            grid = !grid
                        },
                    ) {
                        if (grid) {
                            Icon(Icons.AutoMirrored.Rounded.ViewList, contentDescription = stringResource(R.string.folder_list_view))
                        } else {
                            Icon(Icons.Rounded.GridView, contentDescription = stringResource(R.string.folder_grid_view))
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
            uiState is FolderUiState.Loading -> DelayedLoadingIndicator(Modifier.padding(innerPadding))
            uiState is FolderUiState.Content && uiState.videos.isEmpty() -> RxEmptyState(
                icon = Icons.Rounded.VideoLibrary,
                title = stringResource(R.string.folder_empty_title),
                body = stringResource(R.string.folder_empty_body),
                modifier = Modifier.padding(innerPadding),
            )
            uiState is FolderUiState.Content -> {
                val padding = listPadding(innerPadding, PaddingValues())
                if (grid) {
                    VideoGrid(uiState.videos, gridState, padding, onPlay)
                } else {
                    VideoList(uiState.videos, listState, padding, onPlay)
                }
            }
        }
    }
}

// ---- List --------------------------------------------------------------------------------------

@Composable
private fun VideoList(
    videos: List<VideoRow>,
    state: androidx.compose.foundation.lazy.LazyListState,
    contentPadding: PaddingValues,
    onPlay: (Int) -> Unit,
) {
    val count = videos.size
    LazyColumn(
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(videos, key = { _, v -> v.key }, contentType = { _, _ -> "video" }) { index, video ->
            VideoListItem(video = video, index = index, count = count, onClick = { onPlay(index) })
        }
    }
}

@Composable
private fun VideoListItem(video: VideoRow, index: Int, count: Int, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick,
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
        supportingContent = { VideoMeta(video) },
    ) {
        Text(
            text = video.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (video.watched) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val ListThumbWidth = 128.dp

/** NEW dot · size · resolution tag. */
@Composable
private fun VideoMeta(video: VideoRow, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (video.isNew) NewDot()
        Text(
            text = video.size,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        if (video.resolution != null) ResolutionTag(video.resolution)
    }
}

// ---- Grid --------------------------------------------------------------------------------------

@Composable
private fun VideoGrid(
    videos: List<VideoRow>,
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    contentPadding: PaddingValues,
    onPlay: (Int) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(GridMinCellWidth),
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(videos, key = { _, v -> v.key }, contentType = { _, _ -> "video" }) { index, video ->
            VideoGridItem(video = video, onClick = { onPlay(index) })
        }
    }
}

@Composable
private fun VideoGridItem(video: VideoRow, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            VideoThumbnail(
                contentUri = video.contentUri,
                duration = video.duration,
                resumeFraction = video.resumeFraction,
                shape = RectangleShape, // the card's shape clips the top corners
                requestWidth = GridThumbRequestWidth,
                dimmed = video.watched,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (video.watched) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                VideoMeta(video, Modifier.padding(top = 4.dp))
            }
        }
    }
}

private val GridMinCellWidth = 160.dp

/** Adaptive cells are 160–320 dp wide; 240 dp buckets to a size that stays sharp across them. */
private val GridThumbRequestWidth = 240.dp

// ---- Previews ----------------------------------------------------------------------------------

private val previewVideos = listOf(
    VideoRow("file:///a/1", "", "Episode 1 – Pilot", "42:10", "1.4 GB", "1080p", isNew = false, resumeFraction = 0f, watched = true),
    VideoRow("file:///a/2", "", "Episode 2 – A much longer title that wraps onto a second line", "44:02", "1.5 GB", "1080p", isNew = false, resumeFraction = 0.37f, watched = false),
    VideoRow("file:///a/3", "", "Episode 3", "41:55", "1.3 GB", "4K", isNew = true, resumeFraction = 0f, watched = false),
    VideoRow("file:///a/4", "", "VID_20260101_123456", "0:12", "24 MB", "720p", isNew = true, resumeFraction = 0f, watched = false),
)

private val previewState = FolderUiState.Content(
    title = "Series – Season 1",
    summary = "4 videos · 4.2 GB",
    videos = previewVideos,
    playlist = previewVideos.map { MediaKey(it.key) },
)

@Preview(name = "Folder – list")
@Composable
private fun FolderListPreview() {
    RxTheme(AppearanceSettings(themeMode = ThemeMode.LIGHT, dynamicColor = false)) {
        FolderContent(previewState, onBack = {}, onPlay = {})
    }
}

@Preview(name = "Folder – grid, dark")
@Composable
private fun FolderGridPreview() {
    RxTheme(AppearanceSettings(themeMode = ThemeMode.DARK, dynamicColor = false)) {
        FolderContent(previewState, onBack = {}, onPlay = {}, initialGrid = true)
    }
}
