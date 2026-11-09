package com.dietpizza.rxplayer.feature.videos

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dietpizza.rxplayer.core.data.PlaylistsRepository
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.PlaylistEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One playlist slot. [video] is null once the file is gone (shown as unavailable). */
@Immutable
data class PlaylistItemRow(
    /** MediaKey string; unique within a playlist, so also the lazy-list key. */
    val key: String,
    val video: VideoRow?,
    /** Fallback title for a missing file: its name from the key. */
    val fallbackTitle: String,
)

@Immutable
sealed interface PlaylistUiState {
    val title: String

    data class Loading(override val title: String) : PlaylistUiState

    /** The playlist was deleted (e.g. from another screen). */
    data class Gone(override val title: String) : PlaylistUiState

    data class Content(
        override val title: String,
        val summary: String,
        val items: List<PlaylistItemRow>,
    ) : PlaylistUiState {
        /** Playable keys in order; the player's playlist. */
        val playable: List<MediaKey> = items.mapNotNull { if (it.video != null) MediaKey(it.key) else null }

        /** Index into [playable] for the item at [itemIndex], or -1 if it isn't playable. */
        fun playableIndex(itemIndex: Int): Int {
            if (items.getOrNull(itemIndex)?.video == null) return -1
            return items.subList(0, itemIndex).count { it.video != null }
        }
    }
}

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlists: PlaylistsRepository,
) : ViewModel() {

    private val playlistId: Long = checkNotNull(savedStateHandle.get<Long>(PlaylistActivity.EXTRA_PLAYLIST_ID)) {
        "PlaylistActivity needs ${PlaylistActivity.EXTRA_PLAYLIST_ID}"
    }
    private val initialTitle: String = savedStateHandle.get<String>(PlaylistActivity.EXTRA_PLAYLIST_NAME).orEmpty()

    val uiState: StateFlow<PlaylistUiState> =
        combine(playlists.playlist(playlistId), playlists.entries(playlistId)) { playlist, entries ->
            if (playlist == null) {
                PlaylistUiState.Gone(initialTitle)
            } else {
                PlaylistUiState.Content(
                    title = playlist.name,
                    summary = VideoFormat.videoCount(entries.size),
                    items = entries.map { it.toRow() },
                )
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistUiState.Loading(initialTitle))

    fun setOrder(keys: List<String>) {
        viewModelScope.launch { playlists.setOrder(playlistId, keys.map(::MediaKey)) }
    }

    fun remove(key: String) {
        viewModelScope.launch { playlists.remove(playlistId, listOf(MediaKey(key))) }
    }

    fun rename(name: String) {
        viewModelScope.launch { playlists.rename(playlistId, name) }
    }

    fun delete() {
        viewModelScope.launch { playlists.delete(playlistId) }
    }
}

private fun PlaylistEntry.toRow() = PlaylistItemRow(
    key = key.value,
    video = video?.toRow(),
    fallbackTitle = key.value.substringAfterLast('/'),
)
