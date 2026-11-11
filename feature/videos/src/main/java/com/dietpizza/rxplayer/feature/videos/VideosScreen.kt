package com.dietpizza.rxplayer.feature.videos

import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.dietpizza.rxplayer.core.designsystem.component.RxSectionHeader
import com.dietpizza.rxplayer.core.model.FileOpError
import com.dietpizza.rxplayer.core.designsystem.component.RxBars
import com.dietpizza.rxplayer.core.designsystem.component.rxHeader
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
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
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<FolderRow?>(null) }
    var renameError by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<List<FolderRow>>(emptyList()) }
    val tileActions = FolderTileActions(
        onToggle = viewModel::toggleSelected,
        onClear = viewModel::clearSelection,
        onPin = viewModel::pinSelected,
        onRename = {
            renameError = null
            renaming = viewModel.selectedFolders().singleOrNull()
        },
        onDelete = { deleting = viewModel.selectedFolders() },
    )
    BackHandler(enabled = selection.isNotEmpty(), onBack = viewModel::clearSelection)
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    VideosScaffold(
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        contentPadding = contentPadding,
        selectionBar = if (selection.isEmpty()) {
            null
        } else {
            {
                val content = uiState as? VideosUiState.Content
                val allPinned = content != null &&
                    (content.pinned + content.folders).filter { it.path in selection }.let { s -> s.isNotEmpty() && s.all { it.pinned } }
                FolderSelectionBar(selection.size, allPinned, tileActions)
            }
        },
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
            selection = selection,
            actions = tileActions,
        )
    }
    renaming?.let { folder ->
        NameDialog(
            title = stringResource(R.string.videos_rename_folder),
            initial = folder.name,
            confirmLabel = stringResource(R.string.action_rename),
            error = renameError,
            onConfirm = { name ->
                viewModel.renameFolder(folder, name) { error ->
                    if (error == null) renaming = null else renameError = context.getString(error.folderNameErrorRes())
                }
            },
            onDismiss = { renaming = null },
        )
    }
    if (deleting.isNotEmpty()) {
        val single = deleting.singleOrNull()
        ConfirmDialog(
            title = if (single != null) {
                stringResource(R.string.videos_delete_folder_title, single.name)
            } else {
                pluralStringResource(R.plurals.videos_delete_folders_title, deleting.size, deleting.size)
            },
            text = if (single != null) {
                stringResource(R.string.videos_delete_folder_body, single.countLabel)
            } else {
                stringResource(R.string.videos_delete_folders_body)
            },
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                viewModel.deleteSelected()
                deleting = emptyList()
            },
            onDismiss = { deleting = emptyList() },
        )
    }
}

/** Selection of folders: long-press starts it, then taps toggle; the bar acts on it. */
@Immutable
internal class FolderTileActions(
    val onToggle: (FolderRow) -> Unit = {},
    val onClear: () -> Unit = {},
    val onPin: () -> Unit = {},
    val onRename: () -> Unit = {},
    val onDelete: () -> Unit = {},
)

/** Rename errors worded for a folder rather than a file. */
private fun FileOpError.folderNameErrorRes(): Int = when (this) {
    FileOpError.TARGET_EXISTS -> R.string.error_folder_exists
    FileOpError.INVALID_NAME -> R.string.error_name_invalid
    FileOpError.NOT_FOUND -> R.string.error_folder_not_found
    else -> R.string.error_generic
}

/** Selection-mode top bar: Pin/Unpin, Rename (one folder only) and Delete, as plain icons. */
@Composable
private fun FolderSelectionBar(count: Int, allPinned: Boolean, actions: FolderTileActions) {
    TopAppBar(
        title = { Text(pluralStringResource(R.plurals.selected_count, count, count)) },
        navigationIcon = {
            IconButton(onClick = actions.onClear) {
                Icon(RxIcons.Close, contentDescription = stringResource(R.string.selection_clear))
            }
        },
        actions = {
            IconButton(onClick = actions.onPin) {
                Icon(RxIcons.Keep, contentDescription = stringResource(if (allPinned) R.string.action_unpin else R.string.action_pin))
            }
            if (count == 1) {
                IconButton(onClick = actions.onRename) {
                    Icon(RxIcons.Edit, contentDescription = stringResource(R.string.action_rename))
                }
            }
            IconButton(onClick = actions.onDelete) {
                Icon(RxIcons.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        },
        colors = RxBars.headerColors(MaterialTheme.colorScheme.surfaceContainerHighest),
        modifier = Modifier.rxHeader(),
    )
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
    selectionBar: (@Composable () -> Unit)? = null,
    sortAction: @Composable () -> Unit = {},
    content: @Composable (listPadding: PaddingValues) -> Unit,
) {
    val background = RxBars.pageColor
    Scaffold(
        modifier = modifier,
        containerColor = background,
        topBar = {
            // Both bars are the same fixed-height small bar, so swapping them never moves the list.
            if (selectionBar != null) selectionBar() else TopAppBar(
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
                colors = RxBars.headerColors(),
                modifier = Modifier.rxHeader(),
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
    selection: Set<String> = emptySet(),
    actions: FolderTileActions = FolderTileActions(),
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
            FolderGrid(uiState.pinned, uiState.folders, onOpenFolder, selection, actions, contentPadding)
        } else {
            FolderList(uiState.pinned, uiState.folders, onOpenFolder, selection, actions, contentPadding)
        }
    }
}

/** Folders as a card grid, styled exactly like the Gallery tab's album grid. */
@Composable
private fun FolderGrid(
    pinned: List<FolderRow>,
    folders: List<FolderRow>,
    onOpenFolder: (FolderRow) -> Unit,
    selection: Set<String>,
    actions: FolderTileActions,
    contentPadding: PaddingValues,
) {
    val selecting = selection.isNotEmpty()
    val onClick: (FolderRow) -> Unit = { if (selecting) actions.onToggle(it) else onOpenFolder(it) }
    val dir = LocalLayoutDirection.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(FolderMinCellWidth),
        // A small gap so selected cards (tint + border) never touch their neighbours.
        horizontalArrangement = Arrangement.spacedBy(CardGap),
        verticalArrangement = Arrangement.spacedBy(CardGap),
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
        // The user's pinned folders get their own section, sorted like the rest.
        if (pinned.isNotEmpty()) {
            item(key = PINNED_HEADER_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                RxSectionHeader(stringResource(R.string.videos_section_pinned), GridHeaderModifier)
            }
            items(pinned, key = { it.path }, contentType = { "folder" }) { folder ->
                FolderCard(folder, folder.path in selection, selecting, onClick = { onClick(folder) }, onLongClick = { actions.onToggle(folder) })
            }
            if (folders.isNotEmpty()) {
                item(key = FOLDERS_HEADER_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                    RxSectionHeader(stringResource(R.string.videos_section_folders), GridHeaderModifier)
                }
            }
        }
        items(folders, key = { it.path }, contentType = { "folder" }) { folder ->
            FolderCard(folder, folder.path in selection, selecting, onClick = { onClick(folder) }, onLongClick = { actions.onToggle(folder) })
        }
    }
}

@Composable
private fun FolderCard(folder: FolderRow, selected: Boolean, selecting: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) SELECTED_SCALE else 1f, label = "folderScale")
    Column(
        Modifier
            .clip(SelectedCardShape)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, SelectedCardShape)
            .semantics { this.selected = selected }
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = stringResource(R.string.action_select),
            )
            .padding(CardInset)
            .testTag("videos_folder"),
    ) {
        Box {
            // Thumbnail and badge scale together, so the badge stays on the corner when selected.
            Box(
                Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            ) {
                MediaThumbnail(
                    contentUri = folder.coverUri,
                    requestWidth = FolderThumbRequestWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.largeIncreased),
                )
                if (folder.newBadge != null) {
                    NewBadge(
                        cornerRadius = CardThumbCorner,
                        ring = if (selected) MaterialTheme.colorScheme.secondaryContainer else RxBars.pageColor,
                    )
                }
            }
            if (selecting) SelectionCheck(selected, Modifier.align(Alignment.TopStart).padding(8.dp))
        }
        Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)) {
            ItemTitle(folder.name)
            FolderDetails(folder, Modifier.padding(top = 4.dp), singleLine = true)
        }
    }
}

private const val SELECTED_SCALE = 0.9f

/** Card shape for the tap/selected highlight: tighter than the thumbnail's own rounding. */
private val SelectedCardShape = RoundedCornerShape(12.dp)
private val CardGap = 6.dp

/** Corner radii of the folder thumbnails (shapes.largeIncreased / shapes.extraSmall), for [NewBadge]. */
private val CardThumbCorner = 20.dp
private val RowThumbCorner = 4.dp

/** Filled check when selected, an empty ring otherwise (selection mode only). */
@Composable
private fun SelectionCheck(selected: Boolean, modifier: Modifier = Modifier) {
    if (selected) {
        Icon(
            RxIcons.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier
                .size(22.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
        )
    } else {
        Icon(RxIcons.RadioButtonUnchecked, contentDescription = null, tint = Color.White, modifier = modifier.size(22.dp))
    }
}

/** Folders as plain flat rows: no cards, no gaps, no side gutter. */
@Composable
private fun FolderList(
    pinned: List<FolderRow>,
    folders: List<FolderRow>,
    onOpenFolder: (FolderRow) -> Unit,
    selection: Set<String>,
    actions: FolderTileActions,
    contentPadding: PaddingValues,
) {
    val selecting = selection.isNotEmpty()
    val onClick: (FolderRow) -> Unit = { if (selecting) actions.onToggle(it) else onOpenFolder(it) }
    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = contentPadding,
        modifier = Modifier
            .fillMaxSize()
            .testTag("videos_folders"),
    ) {
        if (pinned.isNotEmpty()) {
            item(key = PINNED_HEADER_KEY, contentType = "header") {
                RxSectionHeader(stringResource(R.string.videos_section_pinned), ListHeaderModifier)
            }
            items(pinned, key = { it.path }, contentType = { "folder" }) { folder ->
                FolderListRow(folder, folder.path in selection, selecting, onClick = { onClick(folder) }, onLongClick = { actions.onToggle(folder) })
            }
            if (folders.isNotEmpty()) {
                item(key = FOLDERS_HEADER_KEY, contentType = "header") {
                    RxSectionHeader(stringResource(R.string.videos_section_folders), ListHeaderModifier)
                }
            }
        }
        items(folders, key = { it.path }, contentType = { "folder" }) { folder ->
            FolderListRow(folder, folder.path in selection, selecting, onClick = { onClick(folder) }, onLongClick = { actions.onToggle(folder) })
        }
    }
}

@Composable
private fun FolderListRow(folder: FolderRow, selected: Boolean, selecting: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    ListItem(
        colors = RxBars.flatListItemColors(),
        selected = selected,
        onClick = onClick,
        onLongClick = onLongClick,
        verticalAlignment = Alignment.Top,
        leadingContent = {
            Box {
                MediaThumbnail(
                    contentUri = folder.coverUri,
                    requestWidth = FolderThumbWidth,
                    modifier = Modifier
                        .size(FolderThumbWidth, FolderThumbWidth * 9 / 16)
                        .clip(MaterialTheme.shapes.extraSmall),
                )
                if (folder.newBadge != null) NewBadge(cornerRadius = RowThumbCorner, ring = RxBars.pageColor)
                if (selecting) SelectionCheck(selected, Modifier.align(Alignment.TopStart).padding(4.dp))
            }
        },
        supportingContent = { FolderDetails(folder, Modifier.padding(top = 2.dp)) },
        modifier = Modifier.testTag("videos_folder"),
    ) {
        ItemTitle(folder.name)
    }
}

private const val PINNED_HEADER_KEY = "header:pinned"
private const val FOLDERS_HEADER_KEY = "header:folders"
private val GridHeaderModifier = Modifier.padding(start = 10.dp, top = 12.dp, bottom = 4.dp)
private val ListHeaderModifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)

/** A chip for each switched-on field. */
@Composable
private fun FolderDetails(folder: FolderRow, modifier: Modifier = Modifier, singleLine: Boolean = false) {
    if (folder.details.isNotEmpty()) FieldChips(folder.details, modifier, singleLine = singleLine)
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
