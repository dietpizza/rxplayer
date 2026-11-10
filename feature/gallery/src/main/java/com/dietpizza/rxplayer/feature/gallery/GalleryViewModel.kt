package com.dietpizza.rxplayer.feature.gallery

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dietpizza.rxplayer.core.datastore.LibrarySettingsRepository
import com.dietpizza.rxplayer.core.datastore.SlideshowSettingsRepository
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.SlideshowCounts
import com.dietpizza.rxplayer.core.model.SlideshowOptions
import com.dietpizza.rxplayer.core.model.filterForSlideshow
import com.dietpizza.rxplayer.core.model.slideshowType
import com.dietpizza.rxplayer.core.data.AlbumsRepository
import com.dietpizza.rxplayer.core.data.FavouritesRepository
import com.dietpizza.rxplayer.core.data.FileOpException
import com.dietpizza.rxplayer.core.data.GalleryRepository
import com.dietpizza.rxplayer.core.data.MediaFileService
import com.dietpizza.rxplayer.core.model.FileOpError
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
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
    /** Folder albums only: the user pinned it (it sits in the Pinned section). */
    val pinned: Boolean = false,
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
        /** Folder albums the user pinned, in the same sort as [albums] (which leaves them out). */
        val pinnedAlbums: List<AlbumTile> = emptyList(),
    ) : GalleryUiState
}

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    favourites: FavouritesRepository,
    private val customAlbums: AlbumsRepository,
    private val librarySettings: LibrarySettingsRepository,
    private val repository: GalleryRepository,
    private val files: MediaFileService,
    private val slideshowSettings: SlideshowSettingsRepository,
) : ViewModel() {

    /** One-off results ("Deleted 12 items") for a toast. */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Set on tap so the pin shows this frame; DataStore catches up behind it. */
    private val pinOverride = MutableStateFlow<Set<String>?>(null)
    private val pinnedPaths: Flow<Set<String>> =
        combine(librarySettings.settings.map { it.pinnedAlbums }, pinOverride) { saved, tapped -> tapped ?: saved }
            .distinctUntilChanged()

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
            combine(keyedTiles, repository.virtualAlbums(), repository.albums(sort), pinnedPaths) { keyed, virtual, albums, pins ->
                if (virtual.isEmpty() && albums.isEmpty()) {
                    GalleryUiState.Empty
                } else {
                    // Same sort for both sections: just split the sorted list.
                    val (pinnedAlbums, rest) = albums.map { it.toTile(it.path in pins) }.partition { it.pinned }
                    GalleryUiState.Content(
                        pinned = keyed + virtual.map { it.toTile() },
                        albums = rest,
                        pinnedAlbums = pinnedAlbums,
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

    /** Selected folder albums (paths). Non-empty = selection mode. */
    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    fun toggleSelected(album: AlbumTile) {
        if (album.kind != AlbumKind.FOLDER) return
        _selection.value = _selection.value.let { if (album.path in it) it - album.path else it + album.path }
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    /** The selected albums still on screen, in display order. */
    fun selectedAlbums(): List<AlbumTile> {
        val content = uiState.value as? GalleryUiState.Content ?: return emptyList()
        val selected = _selection.value
        return (content.pinnedAlbums + content.albums).filter { it.path in selected }
    }

    /** Pins the selection, or unpins it when every selected album is already pinned. */
    fun pinSelected() {
        val albums = selectedAlbums()
        if (albums.isEmpty()) return
        val pin = !albums.all { it.pinned }
        val paths = albums.map { it.path }
        val current = pinOverride.value ?: currentPins()
        pinOverride.value = if (pin) current + paths else current - paths.toSet()
        clearSelection()
        viewModelScope.launch { librarySettings.setAlbumsPinned(paths, pin) }
    }

    private fun currentPins(): Set<String> =
        (uiState.value as? GalleryUiState.Content)?.pinnedAlbums?.mapTo(HashSet()) { it.path } ?: emptySet()

    /** Renames the album's folder on disk. [onDone] gets null on success, else why it failed. */
    fun renameAlbum(album: AlbumTile, name: String, onDone: (FileOpError?) -> Unit) {
        viewModelScope.launch {
            val result = files.renameFolder(album.path, name)
            result.onSuccess { newPath ->
                clearSelection()
                if (newPath != album.path) {
                    pinOverride.value = null
                    librarySettings.movePins(album.path, newPath)
                }
                _messages.tryEmit(context.getString(R.string.msg_renamed))
            }
            onDone(result.exceptionOrNull()?.let { (it as? FileOpException)?.error ?: FileOpError.IO })
        }
    }

    /**
     * Permanently deletes every photo and video in the selected albums' folders (nothing else),
     * then each folder that's left empty.
     */
    fun deleteSelected() {
        val albums = selectedAlbums()
        if (albums.isEmpty()) return
        clearSelection()
        viewModelScope.launch {
            var failed = 0
            var total = 0
            val unpin = ArrayList<String>()
            for (album in albums) {
                val keys = repository.items(album.path).first().map { it.key }
                val results = files.deleteFolder(album.path, keys)
                val albumFailed = results.count { !it.isSuccess }
                failed += albumFailed
                total += results.size
                if (album.pinned && albumFailed == 0) unpin += album.path
            }
            val ok = total - failed
            if (unpin.isNotEmpty()) {
                pinOverride.value = null
                librarySettings.setAlbumsPinned(unpin, false)
            }
            _messages.tryEmit(
                if (failed == 0) {
                    context.resources.getQuantityString(R.plurals.msg_deleted, ok, ok)
                } else {
                    context.resources.getQuantityString(R.plurals.msg_failed, total, failed, total)
                },
            )
        }
    }

    /** The Gallery tab's slideshow: every gallery item, newest first. */
    internal val slideshow: SlideshowSource = object : SlideshowSource {
        override val options: StateFlow<SlideshowOptions> = slideshowSettings.options
            .stateIn(viewModelScope, SharingStarted.Eagerly, SlideshowOptions.DEFAULT)

        override val counts: StateFlow<SlideshowCounts?> = repository.allItems()
            .map { items -> SlideshowCounts.of(items) { it.slideshowType } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), null)

        override suspend fun prepare(options: SlideshowOptions): List<MediaKey> {
            viewModelScope.launch { slideshowSettings.setOptions(options) }
            return repository.allItems().first().filterForSlideshow(options) { it.slideshowType }.map { it.key }
        }
    }

    private fun countLabel(n: Int): String =
        context.resources.getQuantityString(R.plurals.gallery_item_count, n, GalleryFormat.count(n))

    fun setSort(sort: AlbumSort) {
        sortOverride.value = sort
        viewModelScope.launch { librarySettings.setAlbumSort(sort) }
    }

    private fun GalleryAlbum.toTile(pinned: Boolean = false) = AlbumTile(
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
        pinned = pinned,
    )

    private companion object {
        const val FAVOURITES_ID = "favourites"
    }
}
