package com.dietpizza.rxplayer.feature.videos

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dietpizza.rxplayer.core.data.VideoRepository
import com.dietpizza.rxplayer.core.model.VideoFolder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** One row of the Videos tab, with every string already formatted. */
@Immutable
data class FolderRow(
    val path: String,
    val name: String,
    val coverUri: String?,
    /** "12 videos · 4.2 GB" */
    val summary: String,
    /** "3 NEW", or null when nothing is unplayed. */
    val newBadge: String?,
)

@Immutable
sealed interface VideosUiState {
    data object Loading : VideosUiState
    data object Empty : VideosUiState
    data class Content(val folders: List<FolderRow>) : VideosUiState
}

@HiltViewModel
class VideosViewModel @Inject constructor(repository: VideoRepository) : ViewModel() {

    val uiState: StateFlow<VideosUiState> = repository.folders()
        .map { folders ->
            if (folders.isEmpty()) VideosUiState.Empty else VideosUiState.Content(folders.map { it.toRow() })
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VideosUiState.Loading)
}

internal fun VideoFolder.toRow() = FolderRow(
    path = path,
    name = name,
    coverUri = coverContentUri,
    summary = VideoFormat.videoCount(videoCount) + " · " + VideoFormat.bytes(totalSizeBytes),
    newBadge = if (newCount > 0) "$newCount NEW" else null,
)
