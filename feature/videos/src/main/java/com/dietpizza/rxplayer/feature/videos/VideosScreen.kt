package com.dietpizza.rxplayer.feature.videos

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
                    RxSortOption(AlbumSortField.NAME, stringResource(R.string.sort_name), RxIcons.SortByAlpha),
                    RxSortOption(AlbumSortField.DATE_MODIFIED, stringResource(R.string.sort_date), RxIcons.CalendarToday),
                    RxSortOption(AlbumSortField.COUNT, stringResource(R.string.sort_count), RxIcons.FormatListNumbered),
                    RxSortOption(AlbumSortField.SIZE, stringResource(R.string.sort_size), RxIcons.Storage),
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
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
        Column(Modifier.fillMaxSize()) {
            SectionHeader(
                selected = section,
                onSelect = onSectionChange,
                modifier = Modifier.padding(
                    start = start + 16.dp,
                    end = end + 16.dp,
                    // 8 dp above, 12 dp below (4 here + the list's 8 dp top), collapsed or not.
                    top = innerPadding.calculateTopPadding() + contentPadding.calculateTopPadding() + 8.dp,
                    bottom = 4.dp,
                ),
            )
            Box(Modifier.weight(1f)) {
                content(
                    // Below the header: sides + bottom insets, no top inset.
                    listPadding(
                        PaddingValues(start = start, end = end, bottom = innerPadding.calculateBottomPadding()),
                        PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                        gutter = 0.dp,
                    ),
                )
            }
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

@Composable
private fun FolderList(
    folders: List<FolderRow>,
    onOpenFolder: (FolderRow) -> Unit,
    contentPadding: PaddingValues,
) {
    val count = folders.size
    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = contentPadding,
        modifier = Modifier
            .fillMaxSize()
            .testTag("videos_folders"),
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
    ListItem(
        onClick = onClick,
        modifier = Modifier.testTag("videos_folder"),
        leadingContent = {
            VideoThumbnail(
                contentUri = folder.coverUri,
                duration = null,
                resumeFraction = 0f,
                shape = MaterialTheme.shapes.extraSmall,
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

internal val FolderThumbWidth = 112.dp

private val previewFolders = listOf(
    FolderRow("/storage/emulated/0/DCIM/Camera", "Camera", null, "214 videos · 38 GB", "3 NEW"),
    FolderRow("/storage/emulated/0/Download", "Download", null, "12 videos · 4.2 GB", null),
    FolderRow("/storage/emulated/0/Movies/Series", "Series – Season 1 with a long folder name", null, "10 videos · 7.9 GB", "10 NEW"),
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
