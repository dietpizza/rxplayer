package com.dietpizza.rxplayer.feature.videos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Badge
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dietpizza.rxplayer.core.designsystem.component.RxEmptyState
import com.dietpizza.rxplayer.core.designsystem.theme.RxTheme
import com.dietpizza.rxplayer.core.model.AppearanceSettings
import com.dietpizza.rxplayer.core.model.ThemeMode

/**
 * Videos tab root: MX-style folder list (plan §4 #6).
 *
 * Insets: the navigation suite consumes the side it occupies (bottom bar / start rail), so the
 * Scaffold's default content insets only add what is still unconsumed (status bar, cutout).
 * [contentPadding] is extra padding the shell may add on top of that.
 */
@Composable
fun VideosScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val viewModel: VideosViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    VideosContent(
        uiState = uiState,
        onOpenSettings = onOpenSettings,
        onOpenFolder = { folder ->
            context.startActivity(FolderActivity.intent(context, folder.path, folder.name))
        },
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

@Composable
internal fun VideosContent(
    uiState: VideosUiState,
    onOpenSettings: () -> Unit,
    onOpenFolder: (FolderRow) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val background = MaterialTheme.colorScheme.surfaceContainer
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = background,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.videos_title)) },
                actions = {
                    // Search lands in M5.
                    IconButton(onClick = {}, enabled = false) {
                        Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.videos_search))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.videos_settings))
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
        when (uiState) {
            VideosUiState.Loading -> DelayedLoadingIndicator(
                Modifier.padding(innerPadding).padding(contentPadding),
            )
            VideosUiState.Empty -> RxEmptyState(
                icon = Icons.Rounded.VideoLibrary,
                title = stringResource(R.string.videos_empty_title),
                body = stringResource(R.string.videos_empty_body),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(contentPadding),
            )
            is VideosUiState.Content -> FolderList(
                folders = uiState.folders,
                onOpenFolder = onOpenFolder,
                contentPadding = listPadding(innerPadding, contentPadding),
            )
        }
    }
}

@Composable
private fun FolderList(
    folders: List<FolderRow>,
    onOpenFolder: (FolderRow) -> Unit,
    contentPadding: PaddingValues,
) {
    val count = folders.size
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(folders, key = { _, f -> f.path }, contentType = { _, _ -> "folder" }) { index, folder ->
            FolderListItem(
                folder = folder,
                index = index,
                count = count,
                onClick = { onOpenFolder(folder) },
            )
        }
    }
}

@Composable
private fun FolderListItem(folder: FolderRow, index: Int, count: Int, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        leadingContent = {
            VideoThumbnail(
                contentUri = folder.coverUri,
                duration = null,
                resumeFraction = 0f,
                shape = MaterialTheme.shapes.medium,
                requestWidth = FolderThumbWidth,
                modifier = Modifier.size(FolderThumbWidth, FolderThumbWidth * 9 / 16),
            )
        },
        supportingContent = { Text(folder.summary, maxLines = 1) },
        trailingContent = folder.newBadge?.let { badge ->
            {
                // Primary, not the default error red: "new" is good news, not a warning.
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Text(badge) }
            }
        },
    ) {
        Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val FolderThumbWidth = 112.dp

private val previewFolders = listOf(
    FolderRow("/storage/emulated/0/DCIM/Camera", "Camera", null, "214 videos · 38 GB", "3 NEW"),
    FolderRow("/storage/emulated/0/Download", "Download", null, "12 videos · 4.2 GB", null),
    FolderRow("/storage/emulated/0/Movies/Series", "Series – Season 1 with a long folder name", null, "10 videos · 7.9 GB", "10 NEW"),
    FolderRow("/storage/emulated/0/WhatsApp/Video", "WhatsApp Video", null, "1 video · 4.1 MB", null),
)

@Preview(name = "Videos – light")
@Composable
private fun VideosContentPreview() {
    RxTheme(AppearanceSettings(themeMode = ThemeMode.LIGHT, dynamicColor = false)) {
        VideosContent(VideosUiState.Content(previewFolders), onOpenSettings = {}, onOpenFolder = {})
    }
}

@Preview(name = "Videos – dark")
@Composable
private fun VideosContentDarkPreview() {
    RxTheme(AppearanceSettings(themeMode = ThemeMode.DARK, dynamicColor = false)) {
        VideosContent(VideosUiState.Content(previewFolders), onOpenSettings = {}, onOpenFolder = {})
    }
}

@Preview(name = "Videos – empty")
@Composable
private fun VideosEmptyPreview() {
    RxTheme(AppearanceSettings(themeMode = ThemeMode.LIGHT, dynamicColor = false)) {
        VideosContent(VideosUiState.Empty, onOpenSettings = {}, onOpenFolder = {})
    }
}
