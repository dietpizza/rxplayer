package com.dietpizza.rxplayer.feature.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dietpizza.rxplayer.core.data.GalleryRepository
import com.dietpizza.rxplayer.core.model.GalleryItem
import com.dietpizza.rxplayer.core.model.MediaKey
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/** One result tile, pre-formatted. */
@Immutable
data class ResultTile(
    /** MediaKey string; lazy-grid key. */
    val key: String,
    val contentUri: String,
    val name: String,
    val isVideo: Boolean,
    /** "0:42" for videos with a known duration. */
    val duration: String?,
)

@Immutable
sealed interface SearchUiState {
    /** Nothing typed and no filters: show the hint. */
    data object Idle : SearchUiState

    data object Loading : SearchUiState

    data class Results(
        val tiles: List<ResultTile>,
        /** Same order as [tiles]: the viewer / player playlist. */
        val keys: List<MediaKey>,
        /** Every result is a video: tapping opens the full player instead of the viewer. */
        val allVideos: Boolean,
    ) : SearchUiState
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    repository: GalleryRepository,
) : ViewModel() {

    val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

    private val _filters = MutableStateFlow(SearchFilters())
    val filters: StateFlow<SearchFilters> = _filters.asStateFlow()

    /** Rebuilt only when the library changes. */
    private val index = repository.allItems()
        .map { SearchIndex(it) }
        .flowOn(Dispatchers.Default)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SearchUiState> =
        combine(
            // Typing settles first; clearing the field is instant.
            query.debounce { if (it.isBlank()) 0L else DEBOUNCE_MS }.distinctUntilChanged(),
            _filters,
            index,
        ) { q, f, i -> Triple(q, f, i) }
            .mapLatest { (q, f, i) ->
                if (q.isBlank() && !f.isActive) {
                    SearchUiState.Idle
                } else {
                    val results = search(i, q, f, System.currentTimeMillis(), ZoneId.systemDefault())
                    SearchUiState.Results(
                        tiles = results.map { it.toTile() },
                        keys = results.map { it.key },
                        allVideos = results.isNotEmpty() && results.all { it.isVideo },
                    )
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState.Loading)

    fun setQuery(text: String) {
        savedStateHandle[KEY_QUERY] = text
    }

    fun setFilters(filters: SearchFilters) {
        _filters.value = filters
    }

    private fun GalleryItem.toTile() = ResultTile(
        key = key.value,
        contentUri = contentUri,
        name = displayName,
        isVideo = isVideo,
        duration = if (isVideo) formatDuration(durationMs) else null,
    )

    private companion object {
        const val KEY_QUERY = "search.query"
        const val DEBOUNCE_MS = 200L
    }
}

internal fun formatDuration(ms: Long): String? {
    if (ms <= 0) return null
    val total = ms / 1_000
    val h = total / 3_600
    val m = (total % 3_600) / 60
    val s = total % 60
    return if (h > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.ROOT, "%d:%02d", m, s)
    }
}
