package com.dietpizza.rxplayer.feature.player.viewer

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dietpizza.rxplayer.core.data.FavouritesRepository
import com.dietpizza.rxplayer.core.data.GalleryRepository
import com.dietpizza.rxplayer.core.data.MediaFileService
import com.dietpizza.rxplayer.core.model.MediaKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the viewer was opened on (intent extras / ACTION_VIEW). */
@Immutable
internal data class ViewerRequest(
    val keys: List<MediaKey>,
    val startIndex: Int,
    /** ACTION_VIEW from another app: no favourite/delete, info shows only what's known. */
    val external: Boolean = false,
    /** MIME type from the intent, for single external items. */
    val mimeHint: String? = null,
)

/** Items of the viewer pager plus the actions on them (favourite, delete). */
@HiltViewModel
internal class ViewerViewModel @Inject constructor(
    private val gallery: GalleryRepository,
    private val favouritesRepository: FavouritesRepository,
    private val files: MediaFileService,
    val images: ViewerImages,
) : ViewModel() {

    private val _items = MutableStateFlow<List<ViewerItem>>(emptyList())
    val items: StateFlow<List<ViewerItem>> = _items.asStateFlow()

    val favourites: StateFlow<Set<MediaKey>> = favouritesRepository.favourites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private var request: ViewerRequest? = null
    private var enrichJob: Job? = null

    /**
     * Shows [r]. Pages are built from the keys right away (kind from the extension) so the first
     * frame never waits; MediaStore metadata (share URI, dimensions, dates) is joined in after.
     */
    fun open(r: ViewerRequest) {
        if (r == request) return
        request = r
        _items.value = r.keys.map { PageKinds.item(it, null, r.external, r.mimeHint) }
        enrichJob?.cancel()
        enrichJob = viewModelScope.launch {
            val local = r.keys.filter { it.isLocalFile }
            if (local.isEmpty()) return@launch
            // The shared library is usually warm (the album grid is behind us).
            val byKey = withTimeoutOrNull(ENRICH_TIMEOUT_MS) { gallery.itemsByKey().first() }.orEmpty()
            val missing = local.filter { it.value !in byKey }
            // Items outside the visible library (hidden folders, ACTION_VIEW files): ask one by one,
            // but only a few, so a huge hidden album doesn't stall.
            val extra = HashMap<String, com.dietpizza.rxplayer.core.model.GalleryItem>()
            for (k in missing.take(MAX_SINGLE_LOOKUPS)) runCatching { gallery.item(k) }.getOrNull()?.let { extra[k.value] = it }
            _items.value = _items.value.map { item ->
                val g = byKey[item.key.value] ?: extra[item.key.value] ?: return@map item
                PageKinds.item(item.key, g, r.external, r.mimeHint)
            }
        }
    }

    fun toggleFavourite(key: MediaKey) {
        viewModelScope.launch { runCatching { favouritesRepository.toggle(key) } }
    }

    /** Permanently deletes [key] (the caller confirmed). Returns true when it's gone. */
    suspend fun delete(key: MediaKey): Boolean {
        val ok = runCatching { files.delete(listOf(key)).firstOrNull()?.isSuccess == true }.getOrDefault(false)
        if (ok) _items.value = _items.value.filterNot { it.key == key }
        return ok
    }

    private companion object {
        const val ENRICH_TIMEOUT_MS = 5_000L
        const val MAX_SINGLE_LOOKUPS = 32
    }
}
