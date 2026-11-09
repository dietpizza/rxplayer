package com.dietpizza.rxplayer.feature.gallery

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

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
)

@Immutable
sealed interface GalleryUiState {
    data object Loading : GalleryUiState
    data object Empty : GalleryUiState

    data class Content(
        /**
         * The pinned row (plan §4 #2). Today: All videos, Camera. Favourites and custom albums
         * (M5) get prepended here by the ViewModel; the UI already renders any number of tiles.
         */
        val pinned: List<AlbumTile>,
        val albums: List<AlbumTile>,
    ) : GalleryUiState
}

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    repository: GalleryRepository,
) : ViewModel() {

    private val _sort = MutableStateFlow(
        AlbumSort(
            field = AlbumSortField.entries.getOrNull(savedStateHandle[KEY_SORT_FIELD] ?: -1) ?: AlbumSortField.DATE_MODIFIED,
            descending = savedStateHandle[KEY_SORT_DESC] ?: true,
        ),
    )
    val sort: StateFlow<AlbumSort> = _sort.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<GalleryUiState> = _sort
        .flatMapLatest { sort ->
            combine(repository.virtualAlbums(), repository.albums(sort)) { virtual, albums ->
                if (virtual.isEmpty() && albums.isEmpty()) {
                    GalleryUiState.Empty
                } else {
                    GalleryUiState.Content(
                        pinned = virtual.map { it.toTile() },
                        albums = albums.map { it.toTile() },
                    )
                }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUiState.Loading)

    fun setSort(sort: AlbumSort) {
        savedStateHandle[KEY_SORT_FIELD] = sort.field.ordinal
        savedStateHandle[KEY_SORT_DESC] = sort.descending
        _sort.value = sort
    }

    private fun GalleryAlbum.toTile() = AlbumTile(
        id = id,
        path = path,
        name = when (virtual) {
            VirtualAlbum.ALL_VIDEOS -> context.getString(R.string.gallery_all_videos)
            VirtualAlbum.CAMERA -> context.getString(R.string.gallery_camera)
            null -> name
        },
        count = context.resources.getQuantityString(R.plurals.gallery_item_count, itemCount, GalleryFormat.count(itemCount)),
        coverUri = coverContentUri,
        virtual = virtual,
    )

    private companion object {
        const val KEY_SORT_FIELD = "gallery.sort.field"
        const val KEY_SORT_DESC = "gallery.sort.desc"
    }
}
