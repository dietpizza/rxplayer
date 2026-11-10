package com.dietpizza.rxplayer.feature.videos

import com.dietpizza.rxplayer.core.designsystem.component.RxMenu
import com.dietpizza.rxplayer.core.designsystem.component.RxFieldToggle
import com.dietpizza.rxplayer.core.designsystem.component.RxFieldsChoice
import com.dietpizza.rxplayer.core.designsystem.component.RxSortButton
import com.dietpizza.rxplayer.core.designsystem.component.RxSortOption
import com.dietpizza.rxplayer.core.designsystem.component.RxMenuItem
import com.dietpizza.rxplayer.core.designsystem.icon.RxIcons
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dietpizza.rxplayer.core.designsystem.component.RxEmptyState
import com.dietpizza.rxplayer.core.designsystem.theme.RxTheme
import com.dietpizza.rxplayer.core.model.AppearanceSettings
import com.dietpizza.rxplayer.core.model.FileOpError
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.ThemeMode
import com.dietpizza.rxplayer.core.model.VideoField
import com.dietpizza.rxplayer.core.model.VideoSort
import com.dietpizza.rxplayer.core.model.VideoSortField
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role

@Composable
internal fun FolderScreen(
    onBack: () -> Unit,
    onPlay: (playlist: List<MediaKey>, index: Int) -> Unit,
    viewModel: FolderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    val scope = rememberCoroutineScope()
    var dialog by rememberDialogState()
    var nameError by remember { mutableStateOf<FileOpError?>(null) }
    fun open(d: LibraryDialog) {
        nameError = null
        dialog = d
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it.resolve(resources)) }
    }

    val actions = remember(viewModel) {
        FolderActions(
            onToggleGrid = { grid -> viewModel.setGrid(grid) },
            onFieldsChange = viewModel::setFields,
            onSort = viewModel::setSort,
            onToggleSelected = viewModel::toggleSelected,
            onClearSelection = viewModel::clearSelection,
            onSelectAll = viewModel::selectAll,
            onFavourite = viewModel::toggleFavouriteSelected,
            onRename = { open(LibraryDialog.RENAME) },
            onMove = { open(LibraryDialog.MOVE) },
            onCopy = { open(LibraryDialog.COPY) },
            onDelete = { open(LibraryDialog.DELETE) },
        )
    }

    FolderContent(
        uiState = uiState,
        selection = selection,
        actions = actions,
        snackbarHostState = snackbar,
        onBack = onBack,
        onPlay = { index -> (uiState as? FolderUiState.Content)?.let { onPlay(it.playlist, index) } },
    )

    val close = { dialog = LibraryDialog.NONE }
    when (dialog) {
        LibraryDialog.NONE -> Unit
        LibraryDialog.RENAME -> {
            val name = remember { viewModel.renameCandidate() }
            if (name == null) {
                LaunchedEffect(Unit) { close() }
            } else {
                NameDialog(
                    title = stringResource(R.string.action_rename),
                    initial = name,
                    confirmLabel = stringResource(R.string.action_rename),
                    selectBaseName = true,
                    error = nameError?.let { stringResource(it.nameErrorRes()) },
                    onConfirm = { newName ->
                        scope.launch {
                            val error = viewModel.renameSelected(newName)
                            if (error == null) close() else nameError = error
                        }
                    },
                    onDismiss = close,
                )
            }
        }
        LibraryDialog.DELETE -> {
            val count = selection.size
            ConfirmDialog(
                title = pluralStringResource(R.plurals.delete_title, count, count),
                text = pluralStringResource(R.plurals.delete_body, count),
                confirmLabel = stringResource(R.string.action_delete),
                destructive = true,
                onConfirm = {
                    viewModel.deleteSelected()
                    close()
                },
                onDismiss = close,
            )
        }
        LibraryDialog.MOVE, LibraryDialog.COPY -> {
            val copy = dialog == LibraryDialog.COPY
            val folders by viewModel.pickerFolders.collectAsStateWithLifecycle()
            FolderPickerDialog(
                title = stringResource(if (copy) R.string.action_copy_to else R.string.action_move_to),
                folders = folders,
                onPick = {
                    viewModel.transferSelected(it.path, copy)
                    close()
                },
                onNewFolder = { open(if (copy) LibraryDialog.NEW_FOLDER_COPY else LibraryDialog.NEW_FOLDER_MOVE) },
                onDismiss = close,
            )
        }
        LibraryDialog.NEW_FOLDER_MOVE, LibraryDialog.NEW_FOLDER_COPY -> {
            val copy = dialog == LibraryDialog.NEW_FOLDER_COPY
            NameDialog(
                title = stringResource(R.string.folder_new),
                initial = "",
                confirmLabel = stringResource(if (copy) R.string.action_copy else R.string.action_move),
                error = nameError?.let { stringResource(it.nameErrorRes()) },
                onConfirm = { name ->
                    scope.launch {
                        val error = viewModel.transferSelectedToNewFolder(name, copy)
                        if (error == null) close() else nameError = error
                    }
                },
                onDismiss = close,
            )
        }
    }

    progress?.let { p ->
        // Fast operations (a same-volume move, a small delete) finish before this ever shows.
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(PROGRESS_GRACE_MS)
            visible = true
        }
        if (visible) {
            ProgressDialog(
                title = stringResource(
                    when (p.op) {
                        FileOp.MOVE -> R.string.progress_moving
                        FileOp.COPY -> R.string.progress_copying
                        FileOp.DELETE -> R.string.progress_deleting
                    },
                ),
                fraction = p.fraction,
                onCancel = if (p.cancellable) viewModel::cancelFileOp else null,
            )
        }
    }
}

private const val PROGRESS_GRACE_MS = 400L

@Immutable
internal class FolderActions(
    val onToggleGrid: (grid: Boolean) -> Unit,
    val onFieldsChange: (Set<VideoField>) -> Unit,
    val onSort: (VideoSort) -> Unit,
    val onToggleSelected: (key: String) -> Unit,
    val onClearSelection: () -> Unit,
    val onSelectAll: () -> Unit,
    val onFavourite: () -> Unit,
    val onRename: () -> Unit,
    val onMove: () -> Unit,
    val onCopy: () -> Unit,
    val onDelete: () -> Unit,
) {
    companion object {
        val None = FolderActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

@Composable
internal fun FolderContent(
    uiState: FolderUiState,
    selection: Set<String>,
    actions: FolderActions,
    onBack: () -> Unit,
    onPlay: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val content = uiState as? FolderUiState.Content
    val grid = content?.grid == true
    val selecting = selection.isNotEmpty()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val background = MaterialTheme.colorScheme.surfaceContainer

    BackHandler(enabled = selecting, onBack = actions.onClearSelection)

    Scaffold(
        modifier = modifier,
        containerColor = background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Selection mode swaps the bar's contents, not the bar, so its height never jumps.
            TopAppBar(
                title = {
                    Text(
                        if (selecting) pluralStringResource(R.plurals.selected_count, selection.size, selection.size) else uiState.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                // Always present (empty while loading) so the bar's height doesn't jump when data lands.
                subtitle = { Text(content?.summary.orEmpty(), maxLines = 1) },
                expandedHeight = TopAppBarDefaults.TopAppBarExpandedHeight,
                navigationIcon = {
                    if (selecting) {
                        IconButton(onClick = actions.onClearSelection) {
                            Icon(RxIcons.Close, contentDescription = stringResource(R.string.selection_clear))
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(RxIcons.ArrowBack, contentDescription = stringResource(R.string.folder_back))
                        }
                    }
                },
                actions = {
                    if (selecting) {
                        val allFavourite = content != null &&
                            content.videos.filter { it.key in selection }.let { s -> s.isNotEmpty() && s.all { it.isFavourite } }
                        SelectionActions(selection.size, allFavourite, actions)
                    } else if (content != null) {
                        SortMenu(
                            sort = content.sort,
                            onSort = actions.onSort,
                            grid = grid,
                            onGridChange = { toGrid ->
                                // Keep the same video at the top across the switch; applied on the
                                // next measure, so there's no frame at the old position.
                                if (toGrid) {
                                    gridState.requestScrollToItem(listState.firstVisibleItemIndex)
                                } else {
                                    listState.requestScrollToItem(gridState.firstVisibleItemIndex)
                                }
                                actions.onToggleGrid(toGrid)
                            },
                            fields = content.fields,
                            onFieldsChange = actions.onFieldsChange,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    scrolledContainerColor = background,
                ),
            )
        },
    ) { innerPadding ->
        when {
            content == null -> DelayedLoadingIndicator(Modifier.padding(innerPadding))
            content.videos.isEmpty() -> RxEmptyState(
                icon = RxIcons.VideoLibrary,
                title = stringResource(R.string.folder_empty_title),
                body = stringResource(R.string.folder_empty_body),
                modifier = Modifier.padding(innerPadding),
            )
            else -> {
                val padding = listPadding(innerPadding, PaddingValues(), gutter = if (grid) 16.dp else 0.dp)
                val onClick: (Int, VideoRow) -> Unit = { index, video ->
                    if (selecting) actions.onToggleSelected(video.key) else onPlay(index)
                }
                val onLongClick: (VideoRow) -> Unit = { actions.onToggleSelected(it.key) }
                if (grid) {
                    VideoGrid(content.videos, content.fields, selection, gridState, padding, onClick, onLongClick)
                } else {
                    VideoList(content.videos, content.fields, selection, listState, padding, onClick, onLongClick)
                }
            }
        }
    }
}

@Composable
private fun SelectionActions(count: Int, allFavourite: Boolean, actions: FolderActions) {
    IconButton(onClick = actions.onFavourite) {
        if (allFavourite) {
            Icon(RxIcons.Favorite, contentDescription = stringResource(R.string.action_unfavourite))
        } else {
            Icon(RxIcons.FavoriteBorder, contentDescription = stringResource(R.string.action_favourite))
        }
    }
    IconButton(onClick = actions.onDelete) {
        Icon(RxIcons.Delete, contentDescription = stringResource(R.string.action_delete))
    }
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }) {
            Icon(RxIcons.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        RxMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            groups = listOf(
                listOf(
                    RxMenuItem(stringResource(R.string.action_rename), actions.onRename, RxIcons.DriveFileRenameOutline, enabled = count == 1),
                    RxMenuItem(stringResource(R.string.action_move_to), actions.onMove, RxIcons.DriveFileMove),
                    RxMenuItem(stringResource(R.string.action_copy_to), actions.onCopy, RxIcons.ContentCopy),
                ),
                listOf(RxMenuItem(stringResource(R.string.action_select_all), actions.onSelectAll, RxIcons.CheckCircle)),
            ),
        )
    }
}

@Composable
private fun SortMenu(
    sort: VideoSort,
    onSort: (VideoSort) -> Unit,
    grid: Boolean,
    onGridChange: (Boolean) -> Unit,
    fields: Set<VideoField>,
    onFieldsChange: (Set<VideoField>) -> Unit,
) {
    RxSortButton(
        options = VideoSortField.entries.map {
            val (asc, desc) = it.directions()
            RxSortOption(it, stringResource(it.label()), it.icon(), stringResource(asc), stringResource(desc))
        },
        current = sort.field,
        descending = sort.descending,
        labels = sortLabels(),
        naturalDescending = { VideoSort.defaultFor(it).descending },
        onSortChange = { field, desc -> onSort(VideoSort(field, desc)) },
        layout = layoutChoice(grid, onGridChange),
        fields = RxFieldsChoice(
            title = stringResource(R.string.view_fields),
            fields = VideoField.entries.map { f ->
                RxFieldToggle(stringResource(f.label()), f in fields) { shown ->
                    onFieldsChange(if (shown) fields + f else fields - f)
                }
            },
        ),
    )
}

private fun VideoField.label(): Int = when (this) {
    VideoField.DURATION -> R.string.field_duration
    VideoField.SIZE -> R.string.field_size
    VideoField.RESOLUTION -> R.string.field_resolution
    VideoField.DATE -> R.string.field_date
}

private fun VideoSortField.directions(): Pair<Int, Int> = when (this) {
    VideoSortField.NAME -> R.string.sort_dir_az to R.string.sort_dir_za
    VideoSortField.DATE_MODIFIED -> R.string.sort_dir_oldest to R.string.sort_dir_newest
    VideoSortField.SIZE -> R.string.sort_dir_smallest to R.string.sort_dir_largest
    VideoSortField.DURATION -> R.string.sort_dir_shortest to R.string.sort_dir_longest
}

private fun VideoSortField.icon() = when (this) {
    VideoSortField.NAME -> RxIcons.TextFields
    VideoSortField.DATE_MODIFIED -> RxIcons.CalendarToday
    VideoSortField.SIZE -> RxIcons.Database
    VideoSortField.DURATION -> RxIcons.Schedule
}

private fun VideoSortField.label(): Int = when (this) {
    VideoSortField.NAME -> R.string.sort_name
    VideoSortField.DATE_MODIFIED -> R.string.sort_date
    VideoSortField.SIZE -> R.string.sort_size
    VideoSortField.DURATION -> R.string.sort_duration
}

// ---- List --------------------------------------------------------------------------------------

@Composable
private fun VideoList(
    videos: List<VideoRow>,
    fields: Set<VideoField>,
    selection: Set<String>,
    state: LazyListState,
    contentPadding: PaddingValues,
    onClick: (Int, VideoRow) -> Unit,
    onLongClick: (VideoRow) -> Unit,
) {
    val count = videos.size
    LazyColumn(
        state = state,
        contentPadding = contentPadding,
        modifier = Modifier
            .fillMaxSize()
            .testTag("folder_list"),
    ) {
        itemsIndexed(videos, key = { _, v -> v.key }, contentType = { _, _ -> "video" }) { index, video ->
            VideoListItem(
                video = video,
                fields = fields,
                index = index,
                count = count,
                selected = video.key in selection,
                onClick = { onClick(index, video) },
                onLongClick = { onLongClick(video) },
            )
        }
    }
}

@Composable
private fun VideoListItem(
    video: VideoRow,
    fields: Set<VideoField>,
    index: Int,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        selected = selected,
        onClick = onClick,
        onLongClick = onLongClick,
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = {
            Box {
                VideoThumbnail(
                    contentUri = video.contentUri,
                    duration = video.duration.takeIf { VideoField.DURATION in fields },
                    resumeFraction = video.resumeFraction,
                    shape = MaterialTheme.shapes.extraSmall,
                    requestWidth = ListThumbWidth,
                    dimmed = video.watched,
                    modifier = Modifier.size(ListThumbWidth, ListThumbWidth * 9 / 16),
                )
                if (selected) SelectedMark(Modifier.align(Alignment.TopStart).padding(4.dp))
            }
        },
        supportingContent = { VideoMeta(video, fields) },
    ) {
        TitleWithNewDot(
            text = video.title,
            isNew = video.isNew,
            color = if (video.watched) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
        )
    }
}

internal val ListThumbWidth = 128.dp

@Composable
private fun SelectedMark(modifier: Modifier = Modifier) {
    Icon(
        RxIcons.CheckCircle,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .size(22.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape),
    )
}

/** Favourite · then a chip for each switched-on field (duration sits on the thumbnail). */
@Composable
private fun VideoMeta(video: VideoRow, fields: Set<VideoField>, modifier: Modifier = Modifier) {
    val chips = listOfNotNull(
        video.size.takeIf { VideoField.SIZE in fields },
        video.resolution.takeIf { VideoField.RESOLUTION in fields },
        video.date.takeIf { VideoField.DATE in fields && it.isNotEmpty() },
    )
    FieldChips(chips, modifier) {
        if (video.isFavourite) {
            Icon(
                RxIcons.Favorite,
                contentDescription = stringResource(R.string.favourite),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

// ---- Grid --------------------------------------------------------------------------------------

@Composable
private fun VideoGrid(
    videos: List<VideoRow>,
    fields: Set<VideoField>,
    selection: Set<String>,
    state: LazyGridState,
    contentPadding: PaddingValues,
    onClick: (Int, VideoRow) -> Unit,
    onLongClick: (VideoRow) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(GridMinCellWidth),
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("folder_grid"),
    ) {
        itemsIndexed(videos, key = { _, v -> v.key }, contentType = { _, _ -> "video" }) { index, video ->
            VideoGridItem(
                video = video,
                fields = fields,
                selected = video.key in selection,
                onClick = { onClick(index, video) },
                onLongClick = { onLongClick(video) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoGridItem(video: VideoRow, fields: Set<VideoField>, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            Modifier
                .semantics { this.selected = selected }
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onLongClickLabel = stringResource(R.string.action_select),
                    role = Role.Button,
                ),
        ) {
            Box {
                VideoThumbnail(
                    contentUri = video.contentUri,
                    duration = video.duration.takeIf { VideoField.DURATION in fields },
                    resumeFraction = video.resumeFraction,
                    shape = RectangleShape, // the card's shape clips the top corners
                    requestWidth = GridThumbRequestWidth,
                    dimmed = video.watched,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                )
                if (selected) SelectedMark(Modifier.align(Alignment.TopStart).padding(8.dp))
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                TitleWithNewDot(
                    text = video.title,
                    isNew = video.isNew,
                    color = if (video.watched) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    minLines = 2,
                    maxLines = 2,
                )
                VideoMeta(video, fields, Modifier.padding(top = 4.dp))
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
    VideoRow("file:///a/2", "", "Episode 2 – A much longer title that wraps onto a second line", "44:02", "1.5 GB", "1080p", isNew = false, resumeFraction = 0.37f, watched = false, isFavourite = true),
    VideoRow("file:///a/3", "", "Episode 3", "41:55", "1.3 GB", "4K", isNew = true, resumeFraction = 0f, watched = false),
    VideoRow("file:///a/4", "", "VID_20260101_123456", "0:12", "24 MB", "720p", isNew = true, resumeFraction = 0f, watched = false),
)

private fun previewState(grid: Boolean) = FolderUiState.Content(
    title = "Series – Season 1",
    summary = "4 videos · 4.2 GB",
    videos = previewVideos,
    playlist = previewVideos.map { MediaKey(it.key) },
    grid = grid,
)

@Preview(name = "Folder – list")
@Composable
private fun FolderListPreview() {
    RxTheme(AppearanceSettings(themeMode = ThemeMode.LIGHT, dynamicColor = false)) {
        FolderContent(previewState(grid = false), emptySet(), FolderActions.None, onBack = {}, onPlay = {})
    }
}

@Preview(name = "Folder – list, selecting")
@Composable
private fun FolderSelectingPreview() {
    RxTheme(AppearanceSettings(themeMode = ThemeMode.LIGHT, dynamicColor = false)) {
        FolderContent(previewState(grid = false), setOf("file:///a/2", "file:///a/3"), FolderActions.None, onBack = {}, onPlay = {})
    }
}

@Preview(name = "Folder – grid, dark")
@Composable
private fun FolderGridPreview() {
    RxTheme(AppearanceSettings(themeMode = ThemeMode.DARK, dynamicColor = false)) {
        FolderContent(previewState(grid = true), setOf("file:///a/3"), FolderActions.None, onBack = {}, onPlay = {})
    }
}
