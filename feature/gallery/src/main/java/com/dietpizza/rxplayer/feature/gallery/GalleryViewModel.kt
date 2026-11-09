package com.dietpizza.rxplayer.feature.gallery

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dietpizza.rxplayer.core.datastore.LibrarySettingsRepository
import com.dietpizza.rxplayer.core.data.AlbumsRepository
import com.dietpizza.rxplayer.core.data.FavouritesRepository
import com.dietpizza.rxplayer.core.data.GalleryRepository
import com.dietpizza.rxplayer.core.model.AlbumSort
import com.dietpizza.rxplayer.core.model.AlbumSortField
import com.dietpizza.rxplayer.core.model.GalleryAlbum
import com.dietpizza.rxplayer.core.model.VirtualAlbum
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One album card, with every string already formatted. */
@Immutable
data class AlbumTile(
    /** [GalleryAlbum.id]: lazy-grid key. */
    val id: String,
    val path: String,
    val name: String,
    /** "1,234 items" */
    val count: String,
    val coverUri: String?,
    val virtual: VirtualAlbum?,
    val kind: AlbumKind = if (virtual != null) AlbumKind.VIRTUAL else AlbumKind.FOLDER,
    /** Custom albums only. */
    val customAlbumId: Long = 0L,
)

enum class AlbumKind { FOLDER, VIRTUAL, FAVOURITES, CUSTOM }

@Immutable
sealed interface GalleryUiState {
    data object Loading : GalleryUiState
    data object Empty : GalleryUiState

    data class Content(
        /** The pinned row (plan §4 #2): Favourites, custom albums, All videos, Camera. */
        val pinned: List<AlbumTile>,
        val albums: List<AlbumTile>,
    ) : GalleryUiState
}

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    repository: GalleryRepository,
    favourites: FavouritesRepository,
    private val customAlbums: AlbumsRepository,
    private val librarySettings: LibrarySettingsRepository,
) : ViewModel() {

    /** Set on tap so the grid re-sorts this frame; DataStore catches up behind it. */
    private val sortOverride = MutableStateFlow<AlbumSort?>(null)
    private val sortFlow: Flow<AlbumSort> =
        combine(librarySettings.settings.map { it.albumSort }, sortOverride) { saved, tapped -> tapped ?: saved }
            .distinctUntilChanged()
    val sort: StateFlow<AlbumSort> = sortFlow.stateIn(viewModelScope, SharingStarted.Eagerly, AlbumSort.DEFAULT)

    /** Favourites + custom album tiles: DB rows joined with the live library for counts/covers. */
    private val keyedTiles: Flow<List<AlbumTile>> =
        combine(favourites.favourites, customAlbums.albums(), repository.itemsByKey()) { favs, custom, byKey ->
            val tiles = ArrayList<AlbumTile>(custom.size + 1)
            val fav = summarizeKeyed(favs, byKey)
            tiles += AlbumTile(
                id = FAVOURITES_ID,
                path = "",
                name = context.getString(R.string.gallery_favourites),
                count = countLabel(fav.count),
                coverUri = fav.cover?.contentUri,
                virtual = null,
                kind = AlbumKind.FAVOURITES,
            )
            for (album in custom) {
                tiles += AlbumTile(
                    id = "custom:" + album.id,
                    path = "",
                    name = album.name,
                    count = countLabel(album.itemCount),
                    coverUri = album.coverKey?.let { byKey[it.value]?.contentUri },
                    virtual = null,
                    kind = AlbumKind.CUSTOM,
                    customAlbumId = album.id,
                )
            }
            tiles as List<AlbumTile>
        }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<GalleryUiState> = sortFlow
        .flatMapLatest { sort ->
            combine(keyedTiles, repository.virtualAlbums(), repository.albums(sort)) { keyed, virtual, albums ->
                if (virtual.isEmpty() && albums.isEmpty()) {
                    GalleryUiState.Empty
                } else {
                    GalleryUiState.Content(
                        pinned = keyed + virtual.map { it.toTile() },
                        albums = albums.map { it.toTile() },
                    )
                }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUiState.Loading)

    /** Creates an empty custom album; it appears in the pinned row. */
    fun createAlbum(name: String) {
        viewModelScope.launch { customAlbums.create(name) }
    }

    private fun countLabel(n: Int): String =
        context.resources.getQuantityString(R.plurals.gallery_item_count, n, GalleryFormat.count(n))

    fun setSort(sort: AlbumSort) {
        sortOverride.value = sort
        viewModelScope.launch { librarySettings.setAlbumSort(sort) }
    }

    private fun GalleryAlbum.toTile() = AlbumTile(
        id = id,
        path = path,
        name = when (virtual) {
            VirtualAlbum.ALL_VIDEOS -> context.getString(R.string.gallery_all_videos)
            VirtualAlbum.CAMERA -> context.getString(R.string.gallery_camera)
            null -> name
        },
        count = countLabel(itemCount),
        coverUri = coverContentUri,
        virtual = virtual,
    )

    private companion object {
        const val FAVOURITES_ID = "favourites"
    }
}
