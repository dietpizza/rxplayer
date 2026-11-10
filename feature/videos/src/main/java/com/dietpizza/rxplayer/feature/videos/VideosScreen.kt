package com.dietpizza.rxplayer.feature.videos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dietpizza.rxplayer.core.designsystem.component.RxEmptyState
import com.dietpizza.rxplayer.core.designsystem.component.RxFieldToggle
import com.dietpizza.rxplayer.core.designsystem.component.RxFieldsChoice
import com.dietpizza.rxplayer.core.designsystem.component.RxSortButton
import com.dietpizza.rxplayer.core.designsystem.component.RxSortOption
import com.dietpizza.rxplayer.core.designsystem.icon.RxIcons
import com.dietpizza.rxplayer.core.designsystem.theme.RxTheme
import com.dietpizza.rxplayer.core.model.AlbumSort
import com.dietpizza.rxplayer.core.model.AlbumSortField
import com.dietpizza.rxplayer.core.model.AppearanceSettings
import com.dietpizza.rxplayer.core.model.FolderField
import com.dietpizza.rxplayer.core.model.ThemeMode
import com.dietpizza.rxplayer.core.ui.RxIntents
import com.dietpizza.rxplayer.core.ui.thumbnail.MediaThumbnail

/**
 * Videos tab root: the video folders, as a card grid or a flat list (plan §4 #6, #8).
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
    viewModel: VideosViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val grid by viewModel.grid.collectAsStateWithLifecycle()
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    VideosScaffold(
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        contentPadding = contentPadding,
        sortAction = {
            RxSortButton(
                options = listOf(
                    RxSortOption(AlbumSortField.NAME, stringResource(R.string.sort_name), RxIcons.TextFields, stringResource(R.string.sort_dir_az), stringResource(R.string.sort_dir_za)),
                    RxSortOption(AlbumSortField.DATE_MODIFIED, stringResource(R.string.sort_date), RxIcons.CalendarToday, stringResource(R.string.sort_dir_oldest), stringResource(R.string.sort_dir_newest)),
                    RxSortOption(AlbumSortField.COUNT, stringResource(R.string.sort_count), RxIcons.Tag, stringResource(R.string.sort_dir_fewest), stringResource(R.string.sort_dir_most)),
                    RxSortOption(AlbumSortField.SIZE, stringResource(R.string.sort_size), RxIcons.Database, stringResource(R.string.sort_dir_smallest), stringResource(R.string.sort_dir_largest)),
                ),
                current = sort.field,
                descending = sort.descending,
                labels = sortLabels(),
                naturalDescending = { AlbumSort.defaultFor(it).descending },
                onSortChange = { field, desc -> viewModel.setSort(AlbumSort(field, desc)) },
                layout = layoutChoice(grid, viewModel::setGrid),
                fields = RxFieldsChoice(
                    title = stringResource(R.string.view_fields),
                    fields = FolderField.entries.map { f ->
                        RxFieldToggle(stringResource(f.label()), f in fields) { shown ->
                            viewModel.setFields(if (shown) fields + f else fields - f)
                        }
                    },
                ),
            )
        },
    ) { listPadding ->
        FoldersSection(
            uiState = uiState,
            onOpenFolder = { folder ->
                context.startActivity(FolderActivity.intent(context, folder.path, folder.name))
            },
            contentPadding = listPadding,
        )
    }
}

private fun FolderField.label(): Int = when (this) {
    FolderField.COUNT -> R.string.field_count
    FolderField.SIZE -> R.string.field_size
    FolderField.DATE -> R.string.field_date
    FolderField.PATH -> R.string.field_path
}

@Composable
private fun VideosScaffold(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    sortAction: @Composable () -> Unit = {},
    content: @Composable (listPadding: PaddingValues) -> Unit,
) {
    val background = MaterialTheme.colorScheme.surfaceContainer
    Scaffold(
        modifier = modifier,
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.videos_title)) },
                actions = {
                    val context = LocalContext.current
                    IconButton(onClick = { context.startActivity(RxIntents.search(context)) }) {
                        Icon(RxIcons.Search, contentDescription = stringResource(R.string.videos_search))
                    }
                    sortAction()
                    IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("open_settings")) {
                        Icon(RxIcons.Settings, contentDescription = stringResource(R.string.videos_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    scrolledContainerColor = background,
                ),
            )
        },
    ) { innerPadding ->
        content(listPadding(innerPadding, contentPadding, gutter = 0.dp))
    }
}

@Composable
internal fun FoldersSection(
    uiState: VideosUiState,
    onOpenFolder: (FolderRow) -> Unit,
    contentPadding: PaddingValues,
) {
    when (uiState) {
        VideosUiState.Loading -> DelayedLoadingIndicator(Modifier.padding(contentPadding))
        VideosUiState.Empty -> RxEmptyState(
            icon = RxIcons.VideoLibrary,
            title = stringResource(R.string.videos_empty_title),
            body = stringResource(R.string.videos_empty_body),
            modifier = Modifier.padding(contentPadding),
        )
        is VideosUiState.Content -> if (uiState.grid) {
            FolderGrid(uiState.folders, onOpenFolder, contentPadding)
        } else {
            FolderList(uiState.folders, onOpenFolder, contentPadding)
        }
    }
}

/** Folders as a card grid, styled exactly like the Gallery tab's album grid. */
@Composable
private fun FolderGrid(
    folders: List<FolderRow>,
    onOpenFolder: (FolderRow) -> Unit,
    contentPadding: PaddingValues,
) {
    val dir = LocalLayoutDirection.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(FolderMinCellWidth),
        state = rememberLazyGridState(),
        // Small outer margin; each card's inner padding (CardInset) makes the gaps.
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(dir) + 8.dp,
            end = contentPadding.calculateEndPadding(dir) + 8.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
        ),
        modifier = Modifier
            .fillMaxSize()
            .testTag("videos_folders"),
    ) {
        items(folders, key = { it.path }, contentType = { "folder" }) { folder ->
            FolderCard(folder, onClick = { onOpenFolder(folder) })
        }
    }
}

@Composable
private fun FolderCard(folder: FolderRow, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(MaterialTheme.shapes.largeIncreased)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(CardInset)
            .testTag("videos_folder"),
    ) {
        Box {
            MediaThumbnail(
                contentUri = folder.coverUri,
                requestWidth = FolderThumbRequestWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.largeIncreased),
            )
        }
        Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)) {
            TitleWithNewDot(folder.name, isNew = folder.newBadge != null)
            FolderDetails(folder, Modifier.padding(top = 4.dp))
        }
    }
}

/** Folders as plain flat rows: no cards, no gaps, no side gutter. */
@Composable
private fun FolderList(
    folders: List<FolderRow>,
    onOpenFolder: (FolderRow) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = contentPadding,
        modifier = Modifier
            .fillMaxSize()
            .testTag("videos_folders"),
    ) {
        items(folders, key = { it.path }, contentType = { "folder" }) { folder ->
            ListItem(
                onClick = { onOpenFolder(folder) },
                verticalAlignment = Alignment.CenterVertically,
                leadingContent = {
                    Box {
                        MediaThumbnail(
                            contentUri = folder.coverUri,
                            requestWidth = FolderThumbWidth,
                            modifier = Modifier
                                .size(FolderThumbWidth, FolderThumbWidth * 9 / 16)
                                .clip(MaterialTheme.shapes.extraSmall),
                        )
                    }
                },
                supportingContent = { FolderDetails(folder, Modifier.padding(top = 2.dp)) },
                modifier = Modifier.testTag("videos_folder"),
            ) {
                TitleWithNewDot(folder.name, isNew = folder.newBadge != null)
            }
        }
    }
}

/** A chip for each switched-on field. */
@Composable
private fun FolderDetails(folder: FolderRow, modifier: Modifier = Modifier) {
    if (folder.details.isNotEmpty()) FieldChips(folder.details, modifier)
}

/** Same as the Gallery album grid. */
private val FolderMinCellWidth = 152.dp
private val FolderThumbRequestWidth = 240.dp
private val CardInset = 6.dp

internal val FolderThumbWidth = 112.dp

private val previewFolders = listOf(
    FolderRow("/storage/emulated/0/DCIM/Camera", "Camera", null, listOf("214 videos", "38 GB"), "3"),
    FolderRow("/storage/emulated/0/Download", "Download", null, listOf("12 videos", "4.2 GB"), null),
    FolderRow("/storage/emulated/0/Movies/Series", "Series – Season 1 with a long folder name", null, listOf("10 videos", "7.9 GB", "/storage/emulated/0/Movies/Series"), "10"),
    FolderRow("/storage/emulated/0/WhatsApp/Video", "WhatsApp Video", null, listOf("1 video", "4.1 MB"), null),
)

@Composable
private fun FoldersPreview(state: VideosUiState, dark: Boolean) {
    RxTheme(AppearanceSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, dynamicColor = false)) {
        VideosScaffold(onOpenSettings = {}) { padding ->
            FoldersSection(state, onOpenFolder = {}, contentPadding = padding)
        }
    }
}

@Preview(name = "Videos – grid")
@Composable
private fun VideosGridPreview() = FoldersPreview(VideosUiState.Content(previewFolders, grid = true), dark = false)

@Preview(name = "Videos – list, dark")
@Composable
private fun VideosListDarkPreview() = FoldersPreview(VideosUiState.Content(previewFolders, grid = false), dark = true)

@Preview(name = "Videos – empty")
@Composable
private fun VideosEmptyPreview() = FoldersPreview(VideosUiState.Empty, dark = false)
