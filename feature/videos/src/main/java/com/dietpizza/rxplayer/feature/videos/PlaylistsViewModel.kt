package com.dietpizza.rxplayer.feature.videos

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dietpizza.rxplayer.core.data.LocalVideoIndex
import com.dietpizza.rxplayer.core.data.PlaylistsRepository
import com.dietpizza.rxplayer.core.model.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class PlaylistRow(
    val id: Long,
    val name: String,
    /** "12 videos" */
    val summary: String,
    val coverUri: String?,
)

internal fun Playlist.toRow(coverUri: String?) = PlaylistRow(
    id = id,
    name = name,
    summary = VideoFormat.videoCount(itemCount),
    coverUri = coverUri,
)

/** null = still loading. */
@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val playlists: PlaylistsRepository,
    index: LocalVideoIndex,
) : ViewModel() {

    val rows: StateFlow<List<PlaylistRow>?> =
        combine(playlists.playlists(), index.videosByKey()) { list, videos ->
            list.map { it.toRow(coverUri = it.coverKey?.let { k -> videos[k.value]?.contentUri }) }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun create(name: String) {
        viewModelScope.launch { playlists.create(name) }
    }
}
