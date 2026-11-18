package com.dietpizza.rxplayer.feature.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dietpizza.rxplayer.core.data.GalleryRepository
import com.dietpizza.rxplayer.core.model.GalleryItem
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.ui.RxIntents
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

/** One folder result, pre-formatted. */
@Immutable
data class FolderTile(
    val path: String,
    val name: String,
    val count: Int,
    /** Newest item's thumbnail. */
    val coverUri: String,
)

/** Which tab the search belongs to: what it searches and where results open. */
enum class SearchScope {
    /** Photos and videos; items open in the viewer, folders as Gallery albums. */
    GALLERY,

    /** Videos only; items open in the player, folders in the Videos tab's folder screen. */
    VIDEOS;

    companion object {
        fun of(extra: String?): SearchScope =
            if (extra == RxIntents.SEARCH_SCOPE_VIDEOS) VIDEOS else GALLERY
    }
}

@Immutable
sealed interface SearchUiState {
    /** Nothing typed and no filters: show the hint. */
    data object Idle : SearchUiState

    data object Loading : SearchUiState

    data class Results(
        /** Folders whose name matches the query (none for a filters-only search). */
        val folders: List<FolderTile>,
        val tiles: List<ResultTile>,
        /** Same order as [tiles]: the viewer / player playlist. */
        val keys: List<MediaKey>,
    ) : SearchUiState {
        val isEmpty: Boolean get() = folders.isEmpty() && tiles.isEmpty()
    }
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    repository: GalleryRepository,
) : ViewModel() {

    val scope: SearchScope = SearchScope.of(savedStateHandle.get<String>(RxIntents.EXTRA_SEARCH_SCOPE))

    val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

    private val _filters = MutableStateFlow(SearchFilters())
    val filters: StateFlow<SearchFilters> = _filters.asStateFlow()

    /** Rebuilt only when the library changes. */
    private val index = repository.allItems()
        .map { items -> SearchIndex(if (scope == SearchScope.VIDEOS) items.filter { it.isVideo } else items) }
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
                        folders = searchFolders(i, q).map { FolderTile(it.path, it.name, it.count, it.coverUri) },
                        tiles = results.map { it.toTile() },
                        keys = results.map { it.key },
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
