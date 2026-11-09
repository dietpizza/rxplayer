package com.dietpizza.rxplayer.feature.videos

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dietpizza.rxplayer.core.data.NaturalOrderComparator
import com.dietpizza.rxplayer.core.data.VideoRepository
import com.dietpizza.rxplayer.core.datastore.LibrarySettingsRepository
import com.dietpizza.rxplayer.core.model.AlbumSort
import com.dietpizza.rxplayer.core.model.AlbumSortField
import com.dietpizza.rxplayer.core.model.LibrarySettings
import com.dietpizza.rxplayer.core.model.VideoFolder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
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
    /** "12 videos · 4.2 GB" */
    val summary: String,
    /** Unplayed count ("3"), or null when nothing is new. */
    val newBadge: String?,
)

@Immutable
sealed interface VideosUiState {
    data object Loading : VideosUiState
    data object Empty : VideosUiState
    data class Content(val folders: List<FolderRow>) : VideosUiState
}

@HiltViewModel
class VideosViewModel @Inject constructor(
    repository: VideoRepository,
    private val librarySettings: LibrarySettingsRepository,
) : ViewModel() {

    /** Set on tap so the list re-sorts this frame; DataStore catches up behind it. */
    private val sortOverride = MutableStateFlow<AlbumSort?>(null)
    private val sortFlow: Flow<AlbumSort> =
        combine(librarySettings.settings.map { it.videoFolderSort }, sortOverride) { saved, tapped -> tapped ?: saved }
            .distinctUntilChanged()
    val sort: StateFlow<AlbumSort> = sortFlow.stateIn(viewModelScope, SharingStarted.Eagerly, LibrarySettings().videoFolderSort)

    val uiState: StateFlow<VideosUiState> = combine(repository.folders(), sortFlow) { folders, sort ->
        if (folders.isEmpty()) {
            VideosUiState.Empty
        } else {
            VideosUiState.Content(folders.sortedWith(folderComparator(sort)).map { it.toRow() })
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VideosUiState.Loading)

    fun setSort(sort: AlbumSort) {
        sortOverride.value = sort
        viewModelScope.launch { librarySettings.setVideoFolderSort(sort) }
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

internal fun VideoFolder.toRow() = FolderRow(
    path = path,
    name = name,
    coverUri = coverContentUri,
    summary = VideoFormat.videoCount(videoCount) + " · " + VideoFormat.bytes(totalSizeBytes),
    newBadge = if (newCount > 0) "$newCount" else null,
)
