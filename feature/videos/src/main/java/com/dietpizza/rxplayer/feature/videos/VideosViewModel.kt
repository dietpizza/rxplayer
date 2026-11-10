package com.dietpizza.rxplayer.feature.videos

import android.content.Context
import androidx.compose.runtime.Immutable
import com.dietpizza.rxplayer.core.data.FileOpException
import com.dietpizza.rxplayer.core.data.MediaFileService
import com.dietpizza.rxplayer.core.model.FileOpError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dietpizza.rxplayer.core.data.NaturalOrderComparator
import com.dietpizza.rxplayer.core.data.VideoRepository
import com.dietpizza.rxplayer.core.datastore.LibrarySettingsRepository
import com.dietpizza.rxplayer.core.model.AlbumSort
import com.dietpizza.rxplayer.core.model.AlbumSortField
import com.dietpizza.rxplayer.core.model.FolderField
import com.dietpizza.rxplayer.core.model.LibrarySettings
import com.dietpizza.rxplayer.core.model.VideoFolder
import com.dietpizza.rxplayer.core.model.VideoViewMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row of the Videos tab, with every string already formatted. */
@Immutable
data class FolderRow(
    val path: String,
    val name: String,
    val coverUri: String?,
    /** The switched-on details, one chip each: "12 videos", "4.2 GB"…; the path comes last. */
    val details: List<String>,
    /** Unplayed count ("3"), or null when nothing is new. */
    val newBadge: String?,
    /** "12 videos", for the delete confirmation. */
    val countLabel: String = "",
    /** The user pinned it: it sits in the Pinned section. */
    val pinned: Boolean = false,
)

@Immutable
sealed interface VideosUiState {
    data object Loading : VideosUiState
    data object Empty : VideosUiState
    data class Content(
        val folders: List<FolderRow>,
        val grid: Boolean = true,
        /** Pinned folders, in the same sort as [folders] (which leaves them out). */
        val pinned: List<FolderRow> = emptyList(),
    ) : VideosUiState
}

@HiltViewModel
class VideosViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: VideoRepository,
    private val librarySettings: LibrarySettingsRepository,
    private val files: MediaFileService,
) : ViewModel() {

    /** One-off results ("Deleted 12 videos") for a toast. */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Set on tap so the pin shows this frame; DataStore catches up behind it. */
    private val pinOverride = MutableStateFlow<Set<String>?>(null)
    private val pinnedPaths: Flow<Set<String>> =
        combine(librarySettings.settings.map { it.pinnedVideoFolders }, pinOverride) { saved, tapped -> tapped ?: saved }
            .distinctUntilChanged()

    /** Set on tap so the list re-sorts this frame; DataStore catches up behind it. */
    private val sortOverride = MutableStateFlow<AlbumSort?>(null)
    private val sortFlow: Flow<AlbumSort> =
        combine(librarySettings.settings.map { it.videoFolderSort }, sortOverride) { saved, tapped -> tapped ?: saved }
            .distinctUntilChanged()
    val sort: StateFlow<AlbumSort> = sortFlow.stateIn(viewModelScope, SharingStarted.Eagerly, LibrarySettings().videoFolderSort)

    // Same instant-apply pattern for the layout and the shown fields.
    private val gridOverride = MutableStateFlow<Boolean?>(null)
    private val fieldsOverride = MutableStateFlow<Set<FolderField>?>(null)
    val grid: StateFlow<Boolean> =
        combine(librarySettings.settings.map { it.folderViewMode == VideoViewMode.GRID }, gridOverride) { saved, tapped -> tapped ?: saved }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, LibrarySettings().folderViewMode == VideoViewMode.GRID)
    val fields: StateFlow<Set<FolderField>> =
        combine(librarySettings.settings.map { it.folderFields }, fieldsOverride) { saved, tapped -> tapped ?: saved }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, LibrarySettings().folderFields)

    val uiState: StateFlow<VideosUiState> = combine(repository.folders(), sortFlow, grid, fields, pinnedPaths) { folders, sort, grid, fields, pins ->
        if (folders.isEmpty()) {
            VideosUiState.Empty
        } else {
            // Same sort for both sections: just split the sorted list.
            val (pinned, rest) = folders.sortedWith(folderComparator(sort))
                .map { it.toRow(fields, pinned = it.path in pins) }
                .partition { it.pinned }
            VideosUiState.Content(rest, grid, pinned)
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VideosUiState.Loading)

    fun setSort(sort: AlbumSort) {
        sortOverride.value = sort
        viewModelScope.launch { librarySettings.setVideoFolderSort(sort) }
    }

    /** Selected folders (paths). Non-empty = selection mode. */
    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    fun toggleSelected(folder: FolderRow) {
        _selection.value = _selection.value.let { if (folder.path in it) it - folder.path else it + folder.path }
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    /** The selected folders still on screen, in display order. */
    fun selectedFolders(): List<FolderRow> {
        val content = uiState.value as? VideosUiState.Content ?: return emptyList()
        val selected = _selection.value
        return (content.pinned + content.folders).filter { it.path in selected }
    }

    /** Pins the selection, or unpins it when every selected folder is already pinned. */
    fun pinSelected() {
        val folders = selectedFolders()
        if (folders.isEmpty()) return
        val pin = !folders.all { it.pinned }
        val paths = folders.map { it.path }
        val current = pinOverride.value
            ?: (uiState.value as? VideosUiState.Content)?.pinned?.mapTo(HashSet()) { it.path }
            ?: emptySet()
        pinOverride.value = if (pin) current + paths else current - paths.toSet()
        clearSelection()
        viewModelScope.launch { librarySettings.setVideoFoldersPinned(paths, pin) }
    }

    /** Renames the folder on disk. [onDone] gets null on success, else why it failed. */
    fun renameFolder(folder: FolderRow, name: String, onDone: (FileOpError?) -> Unit) {
        viewModelScope.launch {
            val result = files.renameFolder(folder.path, name)
            result.onSuccess { newPath ->
                clearSelection()
                if (newPath != folder.path) {
                    pinOverride.value = null
                    librarySettings.movePins(folder.path, newPath)
                }
                _messages.tryEmit(context.getString(R.string.msg_folder_renamed))
            }
            onDone(result.exceptionOrNull()?.let { (it as? FileOpException)?.error ?: FileOpError.IO })
        }
    }

    /** Permanently deletes the selected folders' videos (nothing else), then each folder left empty. */
    fun deleteSelected() {
        val folders = selectedFolders()
        if (folders.isEmpty()) return
        clearSelection()
        viewModelScope.launch {
            var failed = 0
            var total = 0
            val unpin = ArrayList<String>()
            for (folder in folders) {
                val keys = repository.videosIn(folder.path).first().map { it.key }
                val results = files.deleteFolder(folder.path, keys)
                val folderFailed = results.count { !it.isSuccess }
                failed += folderFailed
                total += results.size
                if (folder.pinned && folderFailed == 0) unpin += folder.path
            }
            val ok = total - failed
            if (unpin.isNotEmpty()) {
                pinOverride.value = null
                librarySettings.setVideoFoldersPinned(unpin, false)
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

    fun setGrid(grid: Boolean) {
        gridOverride.value = grid
        viewModelScope.launch { librarySettings.setFolderViewMode(if (grid) VideoViewMode.GRID else VideoViewMode.LIST) }
    }

    fun setFields(fields: Set<FolderField>) {
        fieldsOverride.value = fields
        viewModelScope.launch { librarySettings.setFolderFields(fields) }
    }
}

/** Folder order for the Videos tab. Ties fall back to name A→Z, then path. */
internal fun folderComparator(sort: AlbumSort): Comparator<VideoFolder> {
    val byName = Comparator<VideoFolder> { a, b -> NaturalOrderComparator.compare(a.name, b.name) }
    val primary: Comparator<VideoFolder> = when (sort.field) {
        AlbumSortField.NAME -> byName
        AlbumSortField.DATE_MODIFIED -> compareBy { it.lastModifiedMs }
        AlbumSortField.COUNT -> compareBy { it.videoCount }
        AlbumSortField.SIZE -> compareBy { it.totalSizeBytes }
    }
    val directed = if (sort.descending) primary.reversed() else primary
    return directed.then(byName).thenBy { it.path }
}

internal fun VideoFolder.toRow(fields: Set<FolderField> = LibrarySettings.DEFAULT_FOLDER_FIELDS, pinned: Boolean = false) = FolderRow(
    path = path,
    name = name,
    coverUri = coverContentUri,
    details = listOfNotNull(
        VideoFormat.videoCount(videoCount).takeIf { FolderField.COUNT in fields },
        VideoFormat.bytes(totalSizeBytes).takeIf { FolderField.SIZE in fields },
        VideoFormat.date(lastModifiedMs).takeIf { FolderField.DATE in fields && it.isNotEmpty() },
        path.takeIf { FolderField.PATH in fields },
    ),
    newBadge = if (newCount > 0) "$newCount" else null,
    countLabel = VideoFormat.videoCount(videoCount),
    pinned = pinned,
)
