package com.dietpizza.rxplayer.feature.videos

import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import com.dietpizza.rxplayer.core.ui.thumbnail.MediaThumbnail
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import com.dietpizza.rxplayer.core.designsystem.component.RxSortButton
import com.dietpizza.rxplayer.core.designsystem.component.RxSortOption
import com.dietpizza.rxplayer.core.designsystem.icon.RxIcons
import com.dietpizza.rxplayer.core.model.AlbumSort
import com.dietpizza.rxplayer.core.model.AlbumSortField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dietpizza.rxplayer.core.ui.RxIntents
import com.dietpizza.rxplayer.core.designsystem.component.RxEmptyState
import com.dietpizza.rxplayer.core.designsystem.theme.RxTheme
import com.dietpizza.rxplayer.core.model.AppearanceSettings
import com.dietpizza.rxplayer.core.model.ThemeMode
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.selection.selectableGroup

/** The Videos tab's segmented header (plan §4 #6). */
internal enum class VideosSection(val label: Int) {
    FOLDERS(R.string.videos_section_folders),
    PLAYLISTS(R.string.videos_section_playlists),
    HISTORY(R.string.videos_section_history),
}

/**
 * Videos tab root: segmented **Folders · Playlists · History** (plan §4 #6, #8).
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
    val context = LocalContext.current
    var section by rememberSaveable { mutableStateOf(VideosSection.FOLDERS) }
    // Each section keeps its own scroll position across switches (like the tabs do).
    val sectionStates = rememberSaveableStateHolder()
    val foldersModel: VideosViewModel = hiltViewModel()
    val folderSort by foldersModel.sort.collectAsStateWithLifecycle()
    VideosScaffold(
        section = section,
        onSectionChange = { section = it },
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        contentPadding = contentPadding,
        sortAction = {
            RxSortButton(
                options = listOf(
                    RxSortOption(AlbumSortField.NAME, stringResource(R.string.sort_name), RxIcons.SortByAlpha, stringResource(R.string.sort_dir_az), stringResource(R.string.sort_dir_za)),
                    RxSortOption(AlbumSortField.DATE_MODIFIED, stringResource(R.string.sort_date), RxIcons.CalendarToday, stringResource(R.string.sort_dir_oldest), stringResource(R.string.sort_dir_newest)),
                    RxSortOption(AlbumSortField.COUNT, stringResource(R.string.sort_count), RxIcons.FormatListNumbered, stringResource(R.string.sort_dir_fewest), stringResource(R.string.sort_dir_most)),
                    RxSortOption(AlbumSortField.SIZE, stringResource(R.string.sort_size), RxIcons.Storage, stringResource(R.string.sort_dir_smallest), stringResource(R.string.sort_dir_largest)),
                ),
                current = folderSort.field,
                descending = folderSort.descending,
                labels = sortLabels(),
                naturalDescending = { AlbumSort.defaultFor(it).descending },
                onSortChange = { field, desc -> foldersModel.setSort(AlbumSort(field, desc)) },
            )
        },
    ) { listPadding ->
        sectionStates.SaveableStateProvider(section) {
            when (section) {
                VideosSection.FOLDERS -> {
                    val uiState by foldersModel.uiState.collectAsStateWithLifecycle()
                    FoldersSection(
                        uiState = uiState,
                        onOpenFolder = { folder ->
                            context.startActivity(FolderActivity.intent(context, folder.path, folder.name))
                        },
                        contentPadding = listPadding,
                    )
                }
                VideosSection.PLAYLISTS -> PlaylistsSection(contentPadding = listPadding)
                VideosSection.HISTORY -> HistorySection(contentPadding = listPadding)
            }
        }
    }
}

@Composable
private fun VideosScaffold(
    section: VideosSection,
    onSectionChange: (VideosSection) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    sortAction: @Composable () -> Unit = {},
    content: @Composable (listPadding: PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val background = MaterialTheme.colorScheme.surfaceContainer
    // Only the Folders · Playlists · History bar quick-returns; the title bar collapses as usual.
    val sectionBar = rememberQuickReturnState()
    // Show the bar when the user switches section, not when the tab merely comes back.
    var barSection by rememberSaveable { mutableStateOf(section) }
    if (barSection != section) {
        barSection = section
        sectionBar.show()
    }
    var barHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Scaffold(
        modifier = modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .nestedScroll(sectionBar.connection),
        containerColor = background,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.videos_title)) },
                actions = {
                    val context = LocalContext.current
                    IconButton(onClick = { context.startActivity(RxIntents.search(context)) }) {
                        Icon(RxIcons.Search, contentDescription = stringResource(R.string.videos_search))
                    }
                    if (section == VideosSection.FOLDERS) sortAction()
                    IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("open_settings")) {
                        Icon(RxIcons.Settings, contentDescription = stringResource(R.string.videos_settings))
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
        val dir = LocalLayoutDirection.current
        val start = innerPadding.calculateStartPadding(dir) + contentPadding.calculateStartPadding(dir)
        val end = innerPadding.calculateEndPadding(dir) + contentPadding.calculateEndPadding(dir)
        val barHeight = with(density) { barHeightPx.toDp() }
        // Below the title bar; clipped so the section bar slides out under it, not over it.
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding() + contentPadding.calculateTopPadding())
                .clipToBounds(),
        ) {
            content(
                // The list starts below the overlaid section bar and passes under it once hidden.
                listPadding(
                    PaddingValues(start = start, end = end, top = barHeight, bottom = innerPadding.calculateBottomPadding()),
                    PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                    gutter = 0.dp,
                ),
            )
            SectionHeader(
                selected = section,
                onSelect = onSectionChange,
                modifier = Modifier
                    .onSizeChanged {
                        barHeightPx = it.height
                        sectionBar.rangePx = it.height.toFloat()
                    }
                    .graphicsLayer { translationY = sectionBar.offsetPx }
                    .background(background)
                    // 8 dp above, 4 dp below (+ the list's own 8 dp = 12), inside the bar's background.
                    .padding(start = start + 16.dp, end = end + 16.dp, top = 8.dp, bottom = 4.dp),
            )
        }
    }
}

/** Connected button group (M3 Expressive). Switching is instant: no content transition. */
@Composable
private fun SectionHeader(
    selected: VideosSection,
    onSelect: (VideosSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sections = VideosSection.entries
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        sections.forEachIndexed { index, s ->
            ToggleButton(
                checked = s == selected,
                onCheckedChange = { onSelect(s) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    sections.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
            ) {
                Text(stringResource(s.label), maxLines = 1)
            }
        }
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
        is VideosUiState.Content -> FolderList(uiState.folders, onOpenFolder, contentPadding)
    }
}

/** Folders as a card grid, styled exactly like the Gallery tab's album grid. */
@Composable
private fun FolderList(
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
            // Something unplayed from the last 7 days: a dot on the cover's top-right corner,
            // ringed in the surface colour so it reads on any image.
            if (folder.newBadge != null) {
                NewDot(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .padding(2.dp),
                )
            }
        }
        Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = folder.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Same as the Gallery album grid. */
private val FolderMinCellWidth = 152.dp
private val FolderThumbRequestWidth = 240.dp
private val CardInset = 6.dp

internal val FolderThumbWidth = 112.dp

private val previewFolders = listOf(
    FolderRow("/storage/emulated/0/DCIM/Camera", "Camera", null, "214 videos · 38 GB", "3"),
    FolderRow("/storage/emulated/0/Download", "Download", null, "12 videos · 4.2 GB", null),
    FolderRow("/storage/emulated/0/Movies/Series", "Series – Season 1 with a long folder name", null, "10 videos · 7.9 GB", "10"),
    FolderRow("/storage/emulated/0/WhatsApp/Video", "WhatsApp Video", null, "1 video · 4.1 MB", null),
)

@Composable
private fun FoldersPreview(state: VideosUiState, dark: Boolean) {
    RxTheme(AppearanceSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, dynamicColor = false)) {
        VideosScaffold(VideosSection.FOLDERS, onSectionChange = {}, onOpenSettings = {}) { padding ->
            FoldersSection(state, onOpenFolder = {}, contentPadding = padding)
        }
    }
}

@Preview(name = "Videos – light")
@Composable
private fun VideosContentPreview() = FoldersPreview(VideosUiState.Content(previewFolders), dark = false)

@Preview(name = "Videos – dark")
@Composable
private fun VideosContentDarkPreview() = FoldersPreview(VideosUiState.Content(previewFolders), dark = true)

@Preview(name = "Videos – empty")
@Composable
private fun VideosEmptyPreview() = FoldersPreview(VideosUiState.Empty, dark = false)
