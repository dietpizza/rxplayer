package com.dietpizza.rxplayer.feature.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dietpizza.rxplayer.core.designsystem.component.RxEmptyState
import com.dietpizza.rxplayer.core.designsystem.theme.RxTheme
import com.dietpizza.rxplayer.core.model.AppearanceSettings
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.MediaSort
import com.dietpizza.rxplayer.core.model.MediaSortField
import com.dietpizza.rxplayer.core.model.ThemeMode
import com.dietpizza.rxplayer.core.ui.thumbnail.MediaThumbnail
import kotlin.math.roundToInt
import kotlinx.coroutines.isActive

@Composable
internal fun AlbumScreen(
    onBack: () -> Unit,
    onOpenItem: OpenItem,
    enabledSelectionActions: Set<SelectionAction>,
    onSelectionAction: (SelectionAction, List<MediaCell>) -> Boolean,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    AlbumContent(
        uiState = uiState,
        sort = sort,
        selection = viewModel.selection,
        onSortChange = viewModel::setSort,
        onBack = onBack,
        onOpenItem = onOpenItem,
        enabledSelectionActions = enabledSelectionActions,
        onSelectionAction = onSelectionAction,
    )
}

private val MediaSortOptions = listOf(
    SortOption(MediaSortField.DATE_TAKEN, R.string.gallery_sort_date_taken),
    SortOption(MediaSortField.NAME, R.string.gallery_sort_name),
    SortOption(MediaSortField.SIZE, R.string.gallery_sort_size),
)

@Composable
internal fun AlbumContent(
    uiState: AlbumUiState,
    sort: MediaSort,
    selection: AlbumSelection,
    onSortChange: (MediaSort) -> Unit,
    onBack: () -> Unit,
    onOpenItem: OpenItem,
    enabledSelectionActions: Set<SelectionAction>,
    onSelectionAction: (SelectionAction, List<MediaCell>) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.surfaceContainer
    val content = uiState as? AlbumUiState.Content
    BackHandler(enabled = selection.isActive) { selection.clear() }

    Scaffold(
        modifier = modifier,
        containerColor = background,
        topBar = {
            // Both bars are fixed-height small bars, so swapping them never re-lays out the grid.
            if (selection.isActive && content != null) {
                SelectionTopBar(
                    count = selection.selected.size,
                    enabled = enabledSelectionActions,
                    onClear = selection::clear,
                    onAction = { action ->
                        val chosen = selectedCells(content.grid, selection.selected)
                        if (onSelectionAction(action, chosen)) selection.clear()
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(uiState.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    // Always present (empty while loading) so the title doesn't shift when data lands.
                    subtitle = { Text(content?.subtitle.orEmpty(), maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.album_back))
                        }
                    },
                    actions = {
                        SortMenuButton(
                            options = MediaSortOptions,
                            current = sort.field,
                            descending = sort.descending,
                            onFieldSelected = { onSortChange(MediaSort.defaultFor(it)) },
                            onDirectionSelected = { onSortChange(sort.copy(descending = it)) },
                        )
                    },
                    expandedHeight = TopAppBarDefaults.TopAppBarExpandedHeight,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = background,
                        scrolledContainerColor = background,
                    ),
                )
            }
        },
    ) { innerPadding ->
        when {
            content == null -> DelayedLoadingIndicator(Modifier.padding(innerPadding))
            content.grid.mediaCount == 0 -> RxEmptyState(
                icon = Icons.Rounded.PhotoLibrary,
                title = stringResource(R.string.album_empty_title),
                body = stringResource(R.string.album_empty_body),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
            else -> MediaGrid(
                grid = content.grid,
                selection = selection,
                background = background,
                innerPadding = innerPadding,
                onOpen = { index -> onOpenItem(content.grid.keys, index) },
            )
        }
    }
}

/** Selected media in display order. */
private fun selectedCells(grid: AlbumGrid, selected: Set<String>): List<MediaCell> {
    val out = ArrayList<MediaCell>(selected.size)
    for (cell in grid.cells) if (cell is MediaCell && cell.key in selected) out += cell
    return out
}

// ---- Grid --------------------------------------------------------------------------------------

private val TileGap = 2.dp
private const val FAST_SCROLL_MIN_CELLS = 60

@Composable
private fun MediaGrid(
    grid: AlbumGrid,
    selection: AlbumSelection,
    background: Color,
    innerPadding: PaddingValues,
    onOpen: (index: Int) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val screenWidth = LocalConfiguration.current.screenWidthDp
    var columns by rememberSaveable { mutableIntStateOf((screenWidth / 110f).roundToInt().coerceIn(3, MAX_COLUMNS)) }
    // Two request sizes only (2–3 and 4–6 columns), so pinching doesn't refetch every thumbnail.
    val requestWidth = (screenWidth / if (columns <= 3) 2 else 4).dp
    val scroller = rememberFastScrollerState(gridState)
    val drag = remember { DragSelectState() }
    val haptics = LocalHapticFeedback.current
    val currentGrid by rememberUpdatedState(grid)

    // Auto-scroll while drag-selecting near an edge, extending the range under the finger.
    val autoScrolling by remember { derivedStateOf { drag.autoScrollSpeed != 0f } }
    LaunchedEffect(autoScrolling) {
        if (!autoScrolling) return@LaunchedEffect
        while (isActive) {
            withFrameNanos { }
            gridState.scrollBy(drag.autoScrollSpeed)
            val index = gridState.cellIndexAt(drag.pointerX, drag.pointerY)
            if (index >= 0) selection.dragTo(index, currentGrid.cells)
        }
    }

    val dir = LocalLayoutDirection.current
    val bottom = innerPadding.calculateBottomPadding()
    Box(
        Modifier
            .fillMaxSize()
            // Top inset outside the grid: sticky headers pin under the app bar, and item offsets
            // share the gesture handlers' coordinate space (see GridGestures.kt).
            .padding(
                top = innerPadding.calculateTopPadding(),
                start = innerPadding.calculateStartPadding(dir),
                end = innerPadding.calculateEndPadding(dir),
            )
            .fastScrollerInput(scroller),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = PaddingValues(bottom = bottom),
            horizontalArrangement = Arrangement.spacedBy(TileGap),
            verticalArrangement = Arrangement.spacedBy(TileGap),
            modifier = Modifier
                .fillMaxSize()
                .pinchToChangeColumns(gridState, columns = { columns }, onColumnsChange = { columns = it })
                .dragToSelect(
                    state = gridState,
                    drag = drag,
                    onStart = { index ->
                        val started = selection.beginDrag(index, currentGrid.cells)
                        if (started) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        started
                    },
                    onMove = { index -> selection.dragTo(index, currentGrid.cells) },
                    onEnd = selection::endDrag,
                ),
        ) {
            albumCells(grid, selection, background, requestWidth, onOpen)
        }
        if (grid.cells.size >= FAST_SCROLL_MIN_CELLS) {
            FastScroller(
                state = scroller,
                totalCells = grid.cells.size,
                labelAt = grid::sectionLabelAt,
                bottomPadding = bottom,
            )
        }
    }
}

/** Headers become sticky headers; each run of media between them is one `items` block. */
@OptIn(ExperimentalFoundationApi::class)
private fun LazyGridScope.albumCells(
    grid: AlbumGrid,
    selection: AlbumSelection,
    background: Color,
    requestWidth: Dp,
    onOpen: (Int) -> Unit,
) {
    val cells = grid.cells
    val headers = grid.headerIndices
    var start = 0
    var h = 0
    while (start < cells.size) {
        if (h < headers.size && headers[h] == start) {
            val header = cells[start] as HeaderCell
            stickyHeader(key = header.key, contentType = HEADER_CONTENT_TYPE) {
                DateHeader(header.label, background)
            }
            start++
            h++
            continue
        }
        val end = if (h < headers.size) headers[h] else cells.size
        val runStart = start
        items(
            count = end - runStart,
            key = { cells[runStart + it].key },
            contentType = { MEDIA_CONTENT_TYPE },
        ) { i ->
            val cell = cells[runStart + i] as MediaCell
            MediaTile(
                cell = cell,
                selected = cell.key in selection.selected,
                selectionMode = selection.isActive,
                requestWidth = requestWidth,
                onClick = { if (selection.isActive) selection.toggle(cell.key) else onOpen(cell.index) },
                onLongClick = { selection.toggle(cell.key) },
            )
        }
        start = end
    }
}

@Composable
private fun DateHeader(label: String, background: Color) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

private val SelectedShape = RoundedCornerShape(12.dp)
private val ChipShape = RoundedCornerShape(4.dp)
private val ChipScrim = Color.Black.copy(alpha = 0.55f)
private const val SELECTED_SCALE = 0.86f

@Composable
private fun MediaTile(
    cell: MediaCell,
    selected: Boolean,
    selectionMode: Boolean,
    requestWidth: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val scale by animateFloatAsState(if (selected) SELECTED_SCALE else 1f, label = "tileScale")
    val description = stringResource(if (cell.isVideo) R.string.album_video else R.string.album_photo)
    Box(
        Modifier
            .aspectRatio(1f)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .semantics {
                this.selected = selected
                onLongClick { onLongClick(); true }
            }
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    shape = if (selected) SelectedShape else RectangleShape
                    clip = selected
                },
        ) {
            MediaThumbnail(
                contentUri = cell.contentUri,
                requestWidth = requestWidth,
                contentDescription = description,
                modifier = Modifier.fillMaxSize(),
            )
            if (cell.duration != null) {
                Text(
                    text = cell.duration,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(ChipScrim, ChipShape)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
            if (cell.isGif) {
                Text(
                    text = stringResource(R.string.album_gif),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(ChipScrim, ChipShape)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        if (selectionMode) {
            SelectionMark(selected, Modifier.align(Alignment.TopStart).padding(6.dp))
        }
    }
}

@Composable
private fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    if (selected) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier
                .size(22.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
        )
    } else {
        Icon(
            Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = Color.White,
            modifier = modifier.size(22.dp),
        )
    }
}

// ---- Selection bar -----------------------------------------------------------------------------

@Composable
private fun SelectionTopBar(
    count: Int,
    enabled: Set<SelectionAction>,
    onClear: () -> Unit,
    onAction: (SelectionAction) -> Unit,
) {
    var overflow by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(pluralStringResource(R.plurals.selection_count, count, GalleryFormat.count(count))) },
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.selection_clear))
            }
        },
        actions = {
            SelectionIcon(SelectionAction.SHARE, Icons.Rounded.Share, R.string.selection_share, enabled, onAction)
            SelectionIcon(SelectionAction.FAVOURITE, Icons.Rounded.FavoriteBorder, R.string.selection_favourite, enabled, onAction)
            SelectionIcon(SelectionAction.ADD_TO_ALBUM, Icons.Rounded.LibraryAdd, R.string.selection_add_to_album, enabled, onAction)
            Box {
                IconButton(onClick = { overflow = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.selection_more))
                }
                DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                    OverflowItem(SelectionAction.MOVE, Icons.Rounded.DriveFileMove, R.string.selection_move, enabled, onAction) { overflow = false }
                    OverflowItem(SelectionAction.COPY, Icons.Rounded.ContentCopy, R.string.selection_copy, enabled, onAction) { overflow = false }
                    OverflowItem(
                        SelectionAction.RENAME, Icons.Rounded.DriveFileRenameOutline, R.string.selection_rename,
                        if (count == 1) enabled else emptySet(), onAction,
                    ) { overflow = false }
                    OverflowItem(SelectionAction.DELETE, Icons.Rounded.Delete, R.string.selection_delete, enabled, onAction) { overflow = false }
                }
            }
        },
        expandedHeight = TopAppBarDefaults.TopAppBarExpandedHeight,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    )
}

@Composable
private fun SelectionIcon(
    action: SelectionAction,
    icon: ImageVector,
    label: Int,
    enabled: Set<SelectionAction>,
    onAction: (SelectionAction) -> Unit,
) {
    IconButton(onClick = { onAction(action) }, enabled = action in enabled) {
        Icon(icon, contentDescription = stringResource(label))
    }
}

@Composable
private fun OverflowItem(
    action: SelectionAction,
    icon: ImageVector,
    label: Int,
    enabled: Set<SelectionAction>,
    onAction: (SelectionAction) -> Unit,
    dismiss: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = action in enabled,
        onClick = {
            dismiss()
            onAction(action)
        },
    )
}

// ---- Previews ----------------------------------------------------------------------------------

private fun previewGrid(): AlbumGrid {
    val cells = ArrayList<AlbumCell>()
    val keys = ArrayList<MediaKey>()
    cells += HeaderCell("h:1", "Today")
    repeat(7) { i ->
        keys += MediaKey("file:///p/$i.jpg")
        cells += MediaCell("file:///p/$i.jpg", i, "", "image/jpeg", isVideo = i == 2, duration = if (i == 2) "0:42" else null, isGif = i == 5)
    }
    cells += HeaderCell("h:0", "Yesterday")
    repeat(5) { j ->
        val i = j + 7
        keys += MediaKey("file:///p/$i.jpg")
        cells += MediaCell("file:///p/$i.jpg", i, "", "image/jpeg", isVideo = false, duration = null, isGif = false)
    }
    return AlbumGrid(cells, intArrayOf(0, 8), keys, IntArray(cells.size) { if (it < 8) 0 else 1 }, listOf("Today", "Yesterday"))
}

@Preview(name = "Album – light")
@Composable
private fun AlbumPreview() {
    RxTheme(AppearanceSettings(themeMode = ThemeMode.LIGHT, dynamicColor = false)) {
        AlbumContent(
            uiState = AlbumUiState.Content("Camera", "12 items", previewGrid(), MediaSort.DEFAULT),
            sort = MediaSort.DEFAULT,
            selection = AlbumSelection(),
            onSortChange = {},
            onBack = {},
            onOpenItem = { _, _ -> },
            enabledSelectionActions = setOf(SelectionAction.SHARE),
            onSelectionAction = { _, _ -> false },
        )
    }
}

@Preview(name = "Album – selecting, dark")
@Composable
private fun AlbumSelectingPreview() {
    val selection = AlbumSelection().apply {
        toggle("file:///p/1.jpg")
        toggle("file:///p/3.jpg")
    }
    RxTheme(AppearanceSettings(themeMode = ThemeMode.DARK, dynamicColor = false)) {
        AlbumContent(
            uiState = AlbumUiState.Content("Camera", "12 items", previewGrid(), MediaSort.DEFAULT),
            sort = MediaSort.DEFAULT,
            selection = selection,
            onSortChange = {},
            onBack = {},
            onOpenItem = { _, _ -> },
            enabledSelectionActions = setOf(SelectionAction.SHARE),
            onSelectionAction = { _, _ -> false },
        )
    }
}
