package com.dietpizza.rxplayer.feature.gallery

import com.dietpizza.rxplayer.core.designsystem.icon.RxIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.dietpizza.rxplayer.core.model.AlbumSort
import com.dietpizza.rxplayer.core.model.AlbumSortField
import com.dietpizza.rxplayer.core.model.AppearanceSettings
import com.dietpizza.rxplayer.core.model.ThemeMode
import com.dietpizza.rxplayer.core.model.VirtualAlbum
import com.dietpizza.rxplayer.core.ui.RxIntents
import com.dietpizza.rxplayer.core.ui.thumbnail.MediaThumbnail
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role

/**
 * Gallery tab root: the albums grid (plan §4 #2), with the pinned row on top.
 *
 * Insets: the navigation suite consumes the side it occupies (bottom bar / start rail), so the
 * Scaffold's default content insets only add what is still unconsumed (status bar, cutout).
 * [contentPadding] is extra padding the shell may add on top of that.
 */
@Composable
fun GalleryScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val viewModel: GalleryViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var newAlbum by rememberSaveable { mutableStateOf(false) }
    GalleryContent(
        uiState = uiState,
        sort = sort,
        onSortChange = viewModel::setSort,
        onOpenSettings = onOpenSettings,
        onOpenSearch = { context.startActivity(RxIntents.search(context)) },
        onOpenAlbum = { album ->
            val intent = when (album.kind) {
                AlbumKind.FAVOURITES -> AlbumActivity.favouritesIntent(context, album.name)
                AlbumKind.CUSTOM -> AlbumActivity.customAlbumIntent(context, album.customAlbumId, album.name)
                AlbumKind.FOLDER, AlbumKind.VIRTUAL -> AlbumActivity.intent(context, album.path, album.name, album.virtual)
            }
            context.startActivity(intent)
        },
        onNewAlbum = { newAlbum = true },
        modifier = modifier,
        contentPadding = contentPadding,
    )
    if (newAlbum) {
        NameDialog(
            title = stringResource(R.string.gallery_new_album),
            initial = "",
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = {
                viewModel.createAlbum(it)
                newAlbum = false
            },
            onDismiss = { newAlbum = false },
        )
    }
}

private val AlbumSortOptions = listOf(
    SortOption(AlbumSortField.DATE_MODIFIED, R.string.gallery_sort_date_modified, RxIcons.CalendarToday, R.string.sort_dir_oldest, R.string.sort_dir_newest),
    SortOption(AlbumSortField.NAME, R.string.gallery_sort_name, RxIcons.SortByAlpha, R.string.sort_dir_az, R.string.sort_dir_za),
    SortOption(AlbumSortField.COUNT, R.string.gallery_sort_count, RxIcons.FormatListNumbered, R.string.sort_dir_fewest, R.string.sort_dir_most),
    SortOption(AlbumSortField.SIZE, R.string.gallery_sort_folder_size, RxIcons.Storage, R.string.sort_dir_smallest, R.string.sort_dir_largest),
)

@Composable
internal fun GalleryContent(
    uiState: GalleryUiState,
    sort: AlbumSort,
    onSortChange: (AlbumSort) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAlbum: (AlbumTile) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onOpenSearch: () -> Unit = {},
    onNewAlbum: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val background = MaterialTheme.colorScheme.surfaceContainer
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = background,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.gallery_title)) },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(RxIcons.Search, contentDescription = stringResource(R.string.gallery_search))
                    }
                    SortMenuButton(
                        options = AlbumSortOptions,
                        current = sort.field,
                        descending = sort.descending,
                        naturalDescending = { AlbumSort.defaultFor(it).descending },
                        onSortChange = { field, desc -> onSortChange(AlbumSort(field, desc)) },
                    )
                    IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("open_settings")) {
                        Icon(RxIcons.Settings, contentDescription = stringResource(R.string.gallery_settings))
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
            GalleryUiState.Loading -> DelayedLoadingIndicator(
                Modifier.padding(innerPadding).padding(contentPadding),
            )
            GalleryUiState.Empty -> RxEmptyState(
                icon = RxIcons.PhotoLibrary,
                title = stringResource(R.string.gallery_empty_title),
                body = stringResource(R.string.gallery_empty_body),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(contentPadding),
            )
            is GalleryUiState.Content -> AlbumsGrid(
                pinned = uiState.pinned,
                albums = uiState.albums,
                onOpenAlbum = onOpenAlbum,
                onNewAlbum = onNewAlbum,
                // Small outer margin; the cards' inner padding adds the rest (8 + 6 ≈ the old 16).
                contentPadding = gridPadding(innerPadding, contentPadding, gutter = 8.dp, top = 4.dp),
            )
        }
    }
}

@Composable
private fun AlbumsGrid(
    pinned: List<AlbumTile>,
    albums: List<AlbumTile>,
    onOpenAlbum: (AlbumTile) -> Unit,
    onNewAlbum: () -> Unit,
    contentPadding: PaddingValues,
) {
    // No arrangement gaps: each card's own inner padding (CardInset) makes the gap, so the tap
    // highlight has breathing room around the thumbnail; contentPadding keeps it off the edges.
    LazyVerticalGrid(
        columns = GridCells.Adaptive(AlbumMinCellWidth),
        contentPadding = contentPadding,
        modifier = Modifier
            .fillMaxSize()
            .testTag("gallery_albums"),
    ) {
        if (pinned.isNotEmpty()) {
            item(key = PINNED_ROW_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = PINNED_ROW_KEY) {
                PinnedRow(pinned, onOpenAlbum, onNewAlbum)
            }
        }
        items(albums, key = { it.id }, contentType = { "album" }) { album ->
            AlbumCard(album, onClick = { onOpenAlbum(album) })
        }
    }
}

private const val PINNED_ROW_KEY = "pinned"

/** Inner padding of every album card: half the visual gap between neighbouring thumbnails. */
private val CardInset = 6.dp
private val AlbumMinCellWidth = 152.dp

/** Adaptive cells are 152–304 dp wide; 240 dp buckets to a size that stays sharp across them. */
private val AlbumThumbRequestWidth = 240.dp
private val PinnedTileWidth = 112.dp

/**
 * The pinned row: Favourites, custom albums, All videos, Camera, then a "New album" tile. Scrolls
 * horizontally once the tiles overflow.
 */
@Composable
private fun PinnedRow(pinned: List<AlbumTile>, onOpenAlbum: (AlbumTile) -> Unit, onNewAlbum: () -> Unit) {
    LazyRow(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        items(pinned, key = { it.id }, contentType = { "pinned" }) { album ->
            Column(
                Modifier
                    .width(PinnedTileWidth + CardInset * 2)
                    .clip(MaterialTheme.shapes.large)
                    .clickable(role = Role.Button) { onOpenAlbum(album) }
                    .padding(CardInset),
            ) {
                Box {
                    MediaThumbnail(
                        contentUri = album.coverUri,
                        requestWidth = PinnedTileWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.extraLarge),
                    )
                    pinnedIcon(album)?.let { icon ->
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .background(BadgeScrim, CircleShape)
                                .padding(4.dp)
                                .size(16.dp),
                        )
                    }
                }
                AlbumLabels(album, Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 4.dp))
            }
        }
        item(key = NEW_ALBUM_KEY, contentType = NEW_ALBUM_KEY) {
            Column(
                Modifier
                    .width(PinnedTileWidth + CardInset * 2)
                    .clip(MaterialTheme.shapes.large)
                    .clickable(role = Role.Button, onClick = onNewAlbum)
                    .padding(CardInset),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Icon(
                        RxIcons.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.gallery_new_album),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
                )
            }
        }
    }
}

private const val NEW_ALBUM_KEY = "new_album"

private fun pinnedIcon(album: AlbumTile): ImageVector? = when (album.kind) {
    AlbumKind.FAVOURITES -> RxIcons.Favorite
    AlbumKind.CUSTOM -> RxIcons.PhotoAlbum
    AlbumKind.VIRTUAL, AlbumKind.FOLDER -> when (album.virtual) {
        VirtualAlbum.ALL_VIDEOS -> RxIcons.Videocam
        VirtualAlbum.CAMERA -> RxIcons.PhotoCamera
        null -> null
    }
}

private val BadgeScrim = Color.Black.copy(alpha = 0.5f)

@Composable
private fun AlbumCard(album: AlbumTile, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(MaterialTheme.shapes.largeIncreased)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(CardInset)
            .testTag("gallery_album"),
    ) {
        MediaThumbnail(
            contentUri = album.coverUri,
            requestWidth = AlbumThumbRequestWidth,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.largeIncreased),
        )
        AlbumLabels(album, Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp))
    }
}

@Composable
private fun AlbumLabels(album: AlbumTile, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = album.count,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

// ---- Previews ----------------------------------------------------------------------------------

private val previewState = GalleryUiState.Content(
    pinned = listOf(
        AlbumTile("favourites", "", "Favourites", "12 items", null, null, AlbumKind.FAVOURITES),
        AlbumTile("virtual:all_videos", "", "All videos", "214 items", null, VirtualAlbum.ALL_VIDEOS),
        AlbumTile("virtual:camera", "/s/DCIM/Camera", "Camera", "3,412 items", null, VirtualAlbum.CAMERA),
    ),
    albums = listOf(
        AlbumTile("/s/Pictures/Screenshots", "/s/Pictures/Screenshots", "Screenshots", "812 items", null, null),
        AlbumTile("/s/WhatsApp/Images", "/s/WhatsApp/Images", "WhatsApp Images", "2,031 items", null, null),
        AlbumTile("/s/Download", "/s/Download", "Download", "41 items", null, null),
        AlbumTile("/s/Pictures/Rx Player", "/s/Pictures/Rx Player", "Rx Player with a long album name", "1 item", null, null),
    ),
)

@Preview(name = "Gallery – light")
@Composable
private fun GalleryPreview() {
    RxTheme(AppearanceSettings(themeMode = ThemeMode.LIGHT, dynamicColor = false)) {
        GalleryContent(previewState, AlbumSort.DEFAULT, {}, {}, {})
    }
}

@Preview(name = "Gallery – dark")
@Composable
private fun GalleryDarkPreview() {
    RxTheme(AppearanceSettings(themeMode = ThemeMode.DARK, dynamicColor = false)) {
        GalleryContent(previewState, AlbumSort.DEFAULT, {}, {}, {})
    }
}

@Preview(name = "Gallery – empty")
@Composable
private fun GalleryEmptyPreview() {
    RxTheme(AppearanceSettings(themeMode = ThemeMode.LIGHT, dynamicColor = false)) {
        GalleryContent(GalleryUiState.Empty, AlbumSort.DEFAULT, {}, {}, {})
    }
}
