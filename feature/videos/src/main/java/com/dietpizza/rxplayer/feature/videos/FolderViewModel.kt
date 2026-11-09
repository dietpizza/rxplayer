package com.dietpizza.rxplayer.feature.videos

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dietpizza.rxplayer.core.data.VideoRepository
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.VideoItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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
    ) : FolderUiState
}

@HiltViewModel
class FolderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: VideoRepository,
) : ViewModel() {

    private val folderPath: String = checkNotNull(savedStateHandle[FolderActivity.EXTRA_FOLDER_PATH]) {
        "FolderActivity needs ${FolderActivity.EXTRA_FOLDER_PATH}"
    }
    private val title: String = savedStateHandle.get<String>(FolderActivity.EXTRA_FOLDER_NAME)
        ?: folderPath.trimEnd('/').substringAfterLast('/')

    val uiState: StateFlow<FolderUiState> = repository.videosIn(folderPath)
        .map { videos ->
            FolderUiState.Content(
                title = title,
                summary = VideoFormat.videoCount(videos.size) + " · " + VideoFormat.bytes(videos.sumOf { it.sizeBytes }),
                videos = videos.map { it.toRow() },
                playlist = videos.map { it.key },
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FolderUiState.Loading(title))
}

internal fun VideoItem.toRow(): VideoRow {
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
    )
}
