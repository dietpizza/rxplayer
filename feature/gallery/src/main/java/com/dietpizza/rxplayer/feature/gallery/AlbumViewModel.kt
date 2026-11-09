package com.dietpizza.rxplayer.feature.gallery

import android.content.Context
import android.text.format.DateFormat
import android.os.Environment
import com.dietpizza.rxplayer.core.data.AlbumsRepository
import com.dietpizza.rxplayer.core.data.FavouritesRepository
import com.dietpizza.rxplayer.core.data.FileOpException
import com.dietpizza.rxplayer.core.data.MediaFileService
import com.dietpizza.rxplayer.core.data.sortItems
import com.dietpizza.rxplayer.core.datastore.LibrarySettingsRepository
import com.dietpizza.rxplayer.core.model.AlbumSort
import com.dietpizza.rxplayer.core.model.AlbumSortField
import com.dietpizza.rxplayer.core.model.FileOpError
import com.dietpizza.rxplayer.core.model.GalleryItem
import com.dietpizza.rxplayer.core.model.LibrarySettings
import com.dietpizza.rxplayer.core.model.MediaKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dietpizza.rxplayer.core.data.GalleryRepository
import com.dietpizza.rxplayer.core.model.MediaSort
import com.dietpizza.rxplayer.core.model.MediaSortField
import com.dietpizza.rxplayer.core.model.VirtualAlbum
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

@Immutable
sealed interface AlbumUiState {
    val title: String

    data class Loading(override val title: String) : AlbumUiState

    data class Content(
        override val title: String,
        /** "1,234 items" */
        val subtitle: String,
        val grid: AlbumGrid,
        val sort: MediaSort,
        /** Initial grid columns from Settings ([LibrarySettings.GRID_COLUMNS_AUTO] = by width). */
        val defaultColumns: Int = LibrarySettings.GRID_COLUMNS_AUTO,
    ) : AlbumUiState
}

@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repository: GalleryRepository,
    private val favourites: FavouritesRepository,
    private val albums: AlbumsRepository,
    private val files: MediaFileService,
    private val librarySettings: LibrarySettingsRepository,
) : ViewModel() {

    val source: AlbumSource = parseSource(savedStateHandle)
    private val initialTitle: String = savedStateHandle.get<String>(AlbumActivity.EXTRA_ALBUM_NAME)
        ?: when (source) {
            is AlbumSource.Folder -> source.path.trimEnd('/').substringAfterLast('/')
            is AlbumSource.Virtual -> ""
            AlbumSource.Favourites -> context.getString(R.string.gallery_favourites)
            is AlbumSource.Custom -> ""
        }

    /** Set on tap so the grid re-sorts this frame; DataStore catches up behind it. */
    private val sortOverride = MutableStateFlow<MediaSort?>(null)
    private val sortFlow: Flow<MediaSort> =
        combine(librarySettings.settings.map { it.mediaSort }, sortOverride) { saved, tapped -> tapped ?: saved }
            .distinctUntilChanged()
    val sort: StateFlow<MediaSort> = sortFlow.stateIn(viewModelScope, SharingStarted.Eagerly, MediaSort.DEFAULT)

    val selection = AlbumSelection()

    /** Live title: custom albums follow renames. */
    private val title: Flow<String> = when (source) {
        is AlbumSource.Custom -> albums.albums()
            .map { list -> list.firstOrNull { it.id == source.albumId }?.name ?: initialTitle }
            .distinctUntilChanged()
        else -> flowOf(initialTitle)
    }

    private val defaultColumns: Flow<Int> =
        librarySettings.settings.map { it.galleryGridColumns }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun items(sort: MediaSort): Flow<List<GalleryItem>> = when (source) {
        is AlbumSource.Folder -> repository.items(source.path, sort)
        is AlbumSource.Virtual -> repository.items(source.album, sort)
        AlbumSource.Favourites -> keyedItems(favourites.favourites, sort)
        is AlbumSource.Custom -> keyedItems(albums.items(source.albumId), sort)
    }

    private fun keyedItems(keys: Flow<Collection<MediaKey>>, sort: MediaSort): Flow<List<GalleryItem>> =
        combine(keys, repository.itemsByKey()) { k, byKey -> joinAlbumItems(k, byKey) }
            .distinctUntilChanged()
            .map { sortItems(it, sort) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<AlbumUiState> = sortFlow
        .flatMapLatest { sort ->
            combine(items(sort), title, defaultColumns) { list, t, columns ->
                AlbumUiState.Content(
                    title = t,
                    subtitle = itemCount(list.size),
                    grid = buildAlbumGrid(list, sort, dateFormatter()),
                    sort = sort,
                    defaultColumns = columns,
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .onEach { state ->
            // Drop selected keys that no longer exist (deleted / moved elsewhere).
            if (selection.isActive) {
                val present = HashSet<String>(state.grid.keys.size * 2)
                for (k in state.grid.keys) present += k.value
                selection.retainAll(present)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumUiState.Loading(initialTitle))

    fun setSort(sort: MediaSort) {
        sortOverride.value = sort
        viewModelScope.launch { librarySettings.setMediaSort(sort) }
    }

    // ---- Selection actions -------------------------------------------------------------------

    private val _messages = Channel<GalleryMessage>(Channel.BUFFERED)
    internal val messages: Flow<GalleryMessage> = _messages.receiveAsFlow()

    private val _progress = MutableStateFlow<FileOpProgress?>(null)
    val progress: StateFlow<FileOpProgress?> = _progress.asStateFlow()
    private var opJob: Job? = null

    /** Selected keys in display order. */
    private fun selectedKeys(): List<MediaKey> {
        val selected = selection.selected
        if (selected.isEmpty()) return emptyList()
        val content = uiState.value as? AlbumUiState.Content ?: return selected.map(::MediaKey)
        return content.grid.keys.filter { it.value in selected }
    }

    /** All selected are favourites → the action un-favourites, otherwise it favourites. */
    fun toggleFavouriteSelected() {
        val keys = selectedKeys()
        if (keys.isEmpty()) return
        viewModelScope.launch {
            val favs = favourites.favourites.first()
            val makeFavourite = !keys.all { it in favs }
            favourites.setFavourite(keys, makeFavourite)
            selection.clear()
            _messages.send(GalleryMessage.Favourited(keys.size, makeFavourite))
        }
    }

    /** Custom albums for the "Add to album" picker (not the one being viewed). Collected only while shown. */
    val pickerAlbums: StateFlow<List<AlbumChoice>> = albums.albums()
        .map { list ->
            list.filter { (source as? AlbumSource.Custom)?.albumId != it.id }
                .map { AlbumChoice(it.id, it.name, itemCount(it.itemCount)) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), emptyList())

    fun addSelectedToAlbum(albumId: Long, albumName: String) {
        val keys = selectedKeys()
        if (keys.isEmpty()) return
        viewModelScope.launch {
            albums.add(albumId, keys)
            selection.clear()
            _messages.send(GalleryMessage.AddedToAlbum(keys.size, albumName))
        }
    }

    fun addSelectedToNewAlbum(name: String) {
        val keys = selectedKeys()
        viewModelScope.launch {
            albums.create(name, keys)
            selection.clear()
            _messages.send(GalleryMessage.AddedToAlbum(keys.size, name))
        }
    }

    /** Favourites: un-favourites. Custom album: removes the rows. Never touches files. */
    fun removeSelectedFromAlbum() {
        val keys = selectedKeys()
        if (keys.isEmpty()) return
        viewModelScope.launch {
            when (source) {
                AlbumSource.Favourites -> favourites.setFavourite(keys, false)
                is AlbumSource.Custom -> albums.remove(source.albumId, keys)
                else -> return@launch
            }
            selection.clear()
            _messages.send(GalleryMessage.RemovedFromAlbum(keys.size))
        }
    }

    fun renameAlbum(name: String) {
        val id = (source as? AlbumSource.Custom)?.albumId ?: return
        viewModelScope.launch { albums.rename(id, name) }
    }

    fun deleteAlbum() {
        val id = (source as? AlbumSource.Custom)?.albumId ?: return
        viewModelScope.launch {
            albums.delete(id)
            _messages.send(GalleryMessage.Close)
        }
    }

    /** Folders for the move/copy picker: every gallery folder (Camera included) but this one. */
    val pickerFolders: StateFlow<List<FolderChoice>> =
        combine(repository.albums(AlbumSort.defaultFor(AlbumSortField.NAME)), repository.virtualAlbums()) { folders, virtual ->
            val choices = ArrayList<FolderChoice>(folders.size + 1)
            for (a in virtual) {
                if (a.virtual == VirtualAlbum.CAMERA && a.path.isNotEmpty()) {
                    choices += FolderChoice(a.path, a.path.substringAfterLast('/'))
                }
            }
            for (a in folders) choices += FolderChoice(a.path, a.name)
            folderChoices(choices, exclude = (source as? AlbumSource.Folder)?.path)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), emptyList())

    /** The single selected item's file name, for the rename dialog. */
    fun renameCandidate(): String? =
        selection.selected.singleOrNull()?.let { MediaKey(it).filePath?.substringAfterLast('/') }

    /** Returns null on success, else the error to show in the dialog. */
    suspend fun renameSelected(newName: String): FileOpError? {
        val key = selection.selected.singleOrNull()?.let(::MediaKey) ?: return FileOpError.NOT_FOUND
        val result = files.rename(key, newName)
        if (result.isSuccess) selection.clear()
        return result.error
    }

    /**
     * Where "New folder" is created: next to this folder, else in the selection's first folder's
     * parent, else Pictures.
     */
    private fun newFolderParent(): String {
        val base = (source as? AlbumSource.Folder)?.path
            ?: selectedKeys().firstOrNull()?.filePath?.substringBeforeLast('/')
        val parent = base?.trimEnd('/')?.substringBeforeLast('/', missingDelimiterValue = "")
        return if (!parent.isNullOrEmpty()) {
            parent
        } else {
            @Suppress("DEPRECATION")
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath
        }
    }

    fun transferSelected(targetDir: String, copy: Boolean) {
        val keys = selectedKeys()
        if (keys.isEmpty()) return
        runFileOp(if (copy) FileOp.COPY else FileOp.MOVE, cancellable = copy) {
            if (copy) {
                files.copy(keys, targetDir) { p -> _progress.value = FileOpProgress(FileOp.COPY, p.fraction, true) }
            } else {
                files.move(keys, targetDir) { p -> _progress.value = FileOpProgress(FileOp.MOVE, p.fraction, false) }
            }.let { results -> results.count { it.isSuccess } to results.count { !it.isSuccess } }
        }
    }

    /** Returns null on success (the transfer then starts), else the error for the name dialog. */
    suspend fun transferSelectedToNewFolder(name: String, copy: Boolean): FileOpError? {
        val created = files.createFolder(newFolderParent(), name)
        val dir = created.getOrElse { return (it as? FileOpException)?.error ?: FileOpError.IO }
        transferSelected(dir, copy)
        return null
    }

    fun deleteSelected() {
        val keys = selectedKeys()
        if (keys.isEmpty()) return
        runFileOp(FileOp.DELETE, cancellable = false) {
            val results = files.delete(keys)
            results.count { it.isSuccess } to results.count { !it.isSuccess }
        }
    }

    fun cancelFileOp() {
        if (_progress.value?.cancellable == true) opJob?.cancel()
    }

    private fun runFileOp(op: FileOp, cancellable: Boolean, block: suspend () -> Pair<Int, Int>) {
        if (opJob?.isActive == true) return
        _progress.value = FileOpProgress(op, 0f, cancellable)
        opJob = viewModelScope.launch {
            try {
                val (ok, failed) = block()
                selection.clear()
                _messages.send(GalleryMessage.FileOpDone(op, ok, failed))
            } finally {
                _progress.value = null
            }
        }
    }

    private fun itemCount(n: Int): String =
        context.resources.getQuantityString(R.plurals.gallery_item_count, n, GalleryFormat.count(n))

    private fun dateFormatter(): DateHeaderFormatter {
        val locale = Locale.getDefault()
        // ICU skeletons give the locale's own order ("Mon, 3 Nov" / "Mon, Nov 3"). ICU and
        // java.time patterns agree on these fields; fall back to a fixed pattern if one doesn't parse.
        fun pattern(skeleton: String, fallback: String): DateTimeFormatter = try {
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        } catch (_: IllegalArgumentException) {
            DateTimeFormatter.ofPattern(fallback, locale)
        }
        return DateHeaderFormatter(
            today = LocalDate.now(),
            zone = ZoneId.systemDefault(),
            todayLabel = context.getString(R.string.gallery_today),
            yesterdayLabel = context.getString(R.string.gallery_yesterday),
            weekdayFormat = pattern("EEEE", "EEEE"),
            sameYearFormat = pattern("EEEdMMM", "EEE, d MMM"),
            otherYearFormat = pattern("dMMMyyyy", "d MMM yyyy"),
        )
    }

    private companion object {

        fun parseSource(state: SavedStateHandle): AlbumSource {
            val customId = state.get<Long>(AlbumActivity.EXTRA_CUSTOM_ALBUM_ID)
            VirtualAlbum.fromId(state.get<String>(AlbumActivity.EXTRA_VIRTUAL_ALBUM))?.let { return AlbumSource.Virtual(it) }
            return when {
                state.get<Boolean>(AlbumActivity.EXTRA_FAVOURITES) == true -> AlbumSource.Favourites
                customId != null && customId > 0L -> AlbumSource.Custom(customId)
                else -> {
                    val path = state.get<String>(AlbumActivity.EXTRA_ALBUM_PATH).orEmpty()
                    require(path.isNotEmpty()) {
                        "AlbumActivity needs a folder path, virtual album, favourites or custom album id"
                    }
                    AlbumSource.Folder(path)
                }
            }
        }
    }
}
