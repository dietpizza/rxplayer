package com.dietpizza.rxplayer.feature.videos

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dietpizza.rxplayer.core.data.FavouritesRepository
import com.dietpizza.rxplayer.core.data.FileOpException
import com.dietpizza.rxplayer.core.data.MediaFileService
import com.dietpizza.rxplayer.core.data.NaturalOrderComparator
import com.dietpizza.rxplayer.core.data.PlaylistsRepository
import com.dietpizza.rxplayer.core.data.VideoRepository
import com.dietpizza.rxplayer.core.datastore.LibrarySettingsRepository
import com.dietpizza.rxplayer.core.model.FileOpError
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.VideoItem
import com.dietpizza.rxplayer.core.model.VideoSort
import com.dietpizza.rxplayer.core.model.VideoSortField
import com.dietpizza.rxplayer.core.model.VideoViewMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One video in a folder, with every string already formatted. */
@Immutable
data class VideoRow(
    /** MediaKey string; also the lazy-list key. */
    val key: String,
    val contentUri: String,
    val title: String,
    /** "12:31", or null when unknown. */
    val duration: String?,
    /** "1.4 GB" */
    val size: String,
    /** "1080p", or null when unknown. */
    val resolution: String?,
    /** Never played: NEW dot. */
    val isNew: Boolean,
    /** Resume bar fill, 0f = no bar (not started, or finished). */
    val resumeFraction: Float,
    /** Played to the end: de-emphasised. */
    val watched: Boolean,
    val isFavourite: Boolean = false,
)

@Immutable
sealed interface FolderUiState {
    val title: String

    data class Loading(override val title: String) : FolderUiState

    data class Content(
        override val title: String,
        /** "12 videos · 4.2 GB" */
        val summary: String,
        val videos: List<VideoRow>,
        /** Same order as [videos]; handed to the player so next/prev follow the folder. */
        val playlist: List<MediaKey>,
        val grid: Boolean = false,
        val sort: VideoSort = VideoSort.DEFAULT,
    ) : FolderUiState
}

/** A running move/copy/delete, for the progress dialog. */
@Immutable
data class FileOpProgress(val op: FileOp, val fraction: Float, val cancellable: Boolean)

@HiltViewModel
class FolderViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: VideoRepository,
    private val settings: LibrarySettingsRepository,
    private val favourites: FavouritesRepository,
    private val playlists: PlaylistsRepository,
    private val files: MediaFileService,
) : ViewModel() {

    private val folderPath: String = checkNotNull(savedStateHandle.get<String>(FolderActivity.EXTRA_FOLDER_PATH)) {
        "FolderActivity needs ${FolderActivity.EXTRA_FOLDER_PATH}"
    }.trimEnd('/')
    private val title: String = savedStateHandle.get<String>(FolderActivity.EXTRA_FOLDER_NAME)
        ?: folderPath.substringAfterLast('/')

    /** Applied immediately on toggle so the switch never waits for a DataStore round trip. */
    private val viewModeOverride = MutableStateFlow<VideoViewMode?>(null)
    private val sortOverride = MutableStateFlow<VideoSort?>(null)

    private val librarySettings = combine(settings.settings, viewModeOverride, sortOverride) { s, mode, sort ->
        (mode ?: s.videoViewMode) to (sort ?: s.videoSort)
    }

    val uiState: StateFlow<FolderUiState> =
        combine(repository.videosIn(folderPath), librarySettings, favourites.favourites) { videos, (mode, sort), favs ->
            val sorted = videos.sortedWith(comparatorFor(sort))
            FolderUiState.Content(
                title = title,
                summary = VideoFormat.videoCount(videos.size) + " · " + VideoFormat.bytes(videos.sumOf { it.sizeBytes }),
                videos = sorted.map { it.toRow(isFavourite = it.key in favs) },
                playlist = sorted.map { it.key },
                grid = mode == VideoViewMode.GRID,
                sort = sort,
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FolderUiState.Loading(title))

    // ---- Selection (survives rotation / process death) -----------------------------------------

    val selection: StateFlow<Set<String>> = savedStateHandle.getStateFlow(KEY_SELECTION, ArrayList<String>())
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private fun setSelection(keys: Set<String>) {
        savedStateHandle[KEY_SELECTION] = ArrayList(keys)
    }

    fun toggleSelected(key: String) {
        val current = selection.value
        setSelection(if (key in current) current - key else current + key)
    }

    fun selectAll() {
        (uiState.value as? FolderUiState.Content)?.let { setSelection(it.videos.mapTo(LinkedHashSet()) { v -> v.key }) }
    }

    fun clearSelection() = setSelection(emptySet())

    /** Selected keys in list order (so move/copy/playlist adds keep the folder order). */
    private fun selectedKeys(): List<MediaKey> {
        val selected = selection.value
        val content = uiState.value as? FolderUiState.Content ?: return selected.map(::MediaKey)
        return content.playlist.filter { it.value in selected }
    }

    // ---- View settings ---------------------------------------------------------------------------

    fun setGrid(grid: Boolean) {
        val mode = if (grid) VideoViewMode.GRID else VideoViewMode.LIST
        viewModeOverride.value = mode
        viewModelScope.launch { settings.setVideoViewMode(mode) }
    }

    fun setSort(sort: VideoSort) {
        sortOverride.value = sort
        viewModelScope.launch { settings.setVideoSort(sort) }
    }

    // ---- Actions ---------------------------------------------------------------------------------

    private val _messages = Channel<LibraryMessage>(Channel.BUFFERED)
    internal val messages: Flow<LibraryMessage> = _messages.receiveAsFlow()

    private val _progress = MutableStateFlow<FileOpProgress?>(null)
    val progress: StateFlow<FileOpProgress?> = _progress.asStateFlow()
    private var opJob: Job? = null

    /** All selected are favourites → the action un-favourites, otherwise it favourites. */
    fun toggleFavouriteSelected() {
        val keys = selectedKeys()
        if (keys.isEmpty()) return
        viewModelScope.launch {
            val favs = favourites.favourites.first()
            val makeFavourite = !keys.all { it in favs }
            favourites.setFavourite(keys, makeFavourite)
            clearSelection()
            _messages.send(LibraryMessage.Favourited(keys.size, makeFavourite))
        }
    }

    /** Video folders for the move/copy picker (other than this one). Collected only while shown. */
    val pickerFolders: StateFlow<List<FolderRow>> = repository.folders()
        .map { folders -> folders.filter { it.path != folderPath }.map { it.toRow() } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), emptyList())

    val pickerPlaylists: StateFlow<List<PlaylistRow>> = playlists.playlists()
        .map { list -> list.map { it.toRow(coverUri = null) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), emptyList())

    fun addSelectedToPlaylist(playlistId: Long, playlistName: String) {
        val keys = selectedKeys()
        viewModelScope.launch {
            val added = playlists.add(playlistId, keys)
            clearSelection()
            _messages.send(LibraryMessage.AddedToPlaylist(added, playlistName))
        }
    }

    fun addSelectedToNewPlaylist(name: String) {
        val keys = selectedKeys()
        viewModelScope.launch {
            playlists.create(name, keys)
            clearSelection()
            _messages.send(LibraryMessage.AddedToPlaylist(keys.size, name))
        }
    }

    /** The single selected video's file name, for the rename dialog. */
    fun renameCandidate(): String? =
        selection.value.singleOrNull()?.let { MediaKey(it).filePath?.substringAfterLast('/') }

    /** Returns null on success, else the error to show in the dialog. */
    suspend fun renameSelected(newName: String): FileOpError? {
        val key = selection.value.singleOrNull()?.let(::MediaKey) ?: return FileOpError.NOT_FOUND
        val result = files.rename(key, newName)
        if (result.isSuccess) clearSelection()
        return result.error
    }

    /** Parent of this folder: where "New folder" is created. */
    val newFolderParent: String = folderPath.substringBeforeLast('/', missingDelimiterValue = folderPath)

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
        val created = files.createFolder(newFolderParent, name)
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
                clearSelection()
                _messages.send(LibraryMessage.FileOpDone(op, ok, failed))
            } finally {
                _progress.value = null
            }
        }
    }

    private companion object {
        const val KEY_SELECTION = "selection"
    }
}

internal fun comparatorFor(sort: VideoSort): Comparator<VideoItem> {
    val byName = Comparator<VideoItem> { a, b -> NaturalOrderComparator.compare(a.title, b.title) }
    val primary: Comparator<VideoItem> = when (sort.field) {
        VideoSortField.NAME -> byName
        VideoSortField.DATE_MODIFIED -> compareBy { it.dateModifiedMs }
        VideoSortField.SIZE -> compareBy { it.sizeBytes }
        VideoSortField.DURATION -> compareBy { it.durationMs }
    }
    val directed = if (sort.descending) primary.reversed() else primary
    // Ties always A→Z, then by path, so the order is total and stable.
    return directed.then(byName).thenBy { it.path }
}

internal fun VideoItem.toRow(isFavourite: Boolean = false): VideoRow {
    val playback = playback
    val watched = playback?.completed == true
    return VideoRow(
        key = key.value,
        contentUri = contentUri,
        title = title,
        duration = VideoFormat.duration(durationMs),
        size = VideoFormat.bytes(sizeBytes),
        resolution = VideoFormat.resolution(width, height),
        isNew = isNew,
        resumeFraction = if (playback == null || watched) 0f else playback.fraction,
        watched = watched,
        isFavourite = isFavourite,
    )
}
