package com.dietpizza.rxplayer.feature.gallery

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dietpizza.rxplayer.core.data.GalleryRepository
import com.dietpizza.rxplayer.core.model.MediaSort
import com.dietpizza.rxplayer.core.model.MediaSortField
import com.dietpizza.rxplayer.core.model.VirtualAlbum
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

@Immutable
sealed interface AlbumUiState {
    val title: String

    data class Loading(override val title: String) : AlbumUiState

    data class Content(
        override val title: String,
        /** "1,234 items" */
        val subtitle: String,
        val grid: AlbumGrid,
        val sort: MediaSort,
    ) : AlbumUiState
}

@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    repository: GalleryRepository,
) : ViewModel() {

    private val virtual: VirtualAlbum? =
        VirtualAlbum.fromId(savedStateHandle.get<String>(AlbumActivity.EXTRA_VIRTUAL_ALBUM))
    private val albumPath: String = savedStateHandle.get<String>(AlbumActivity.EXTRA_ALBUM_PATH).orEmpty()
    private val title: String = savedStateHandle.get<String>(AlbumActivity.EXTRA_ALBUM_NAME)
        ?: albumPath.trimEnd('/').substringAfterLast('/')

    init {
        require(virtual != null || albumPath.isNotEmpty()) {
            "AlbumActivity needs ${AlbumActivity.EXTRA_ALBUM_PATH} or ${AlbumActivity.EXTRA_VIRTUAL_ALBUM}"
        }
    }

    private val _sort = MutableStateFlow(
        MediaSort(
            field = MediaSortField.entries.getOrNull(savedStateHandle[KEY_SORT_FIELD] ?: -1) ?: MediaSortField.DATE_TAKEN,
            descending = savedStateHandle[KEY_SORT_DESC] ?: true,
        ),
    )
    val sort: StateFlow<MediaSort> = _sort.asStateFlow()

    val selection = AlbumSelection()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<AlbumUiState> = _sort
        .flatMapLatest { sort ->
            val items = if (virtual != null) repository.items(virtual, sort) else repository.items(albumPath, sort)
            items.map { list ->
                AlbumUiState.Content(
                    title = title,
                    subtitle = itemCount(list.size),
                    grid = buildAlbumGrid(list, sort, dateFormatter()),
                    sort = sort,
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .onEach { state ->
            // Drop selected keys that no longer exist (deleted / moved elsewhere).
            if (selection.isActive) {
                val present = HashSet<String>(state.grid.keys.size * 2)
                for (k in state.grid.keys) present += k.value
                selection.retainAll(present)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumUiState.Loading(title))

    fun setSort(sort: MediaSort) {
        savedStateHandle[KEY_SORT_FIELD] = sort.field.ordinal
        savedStateHandle[KEY_SORT_DESC] = sort.descending
        _sort.value = sort
    }

    private fun itemCount(n: Int): String =
        context.resources.getQuantityString(R.plurals.gallery_item_count, n, GalleryFormat.count(n))

    private fun dateFormatter(): DateHeaderFormatter {
        val locale = Locale.getDefault()
        // ICU skeletons give the locale's own order ("Mon, 3 Nov" / "Mon, Nov 3"). ICU and
        // java.time patterns agree on these fields; fall back to a fixed pattern if one doesn't parse.
        fun pattern(skeleton: String, fallback: String): DateTimeFormatter = try {
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        } catch (_: IllegalArgumentException) {
            DateTimeFormatter.ofPattern(fallback, locale)
        }
        return DateHeaderFormatter(
            today = LocalDate.now(),
            zone = ZoneId.systemDefault(),
            todayLabel = context.getString(R.string.gallery_today),
            yesterdayLabel = context.getString(R.string.gallery_yesterday),
            weekdayFormat = pattern("EEEE", "EEEE"),
            sameYearFormat = pattern("EEEdMMM", "EEE, d MMM"),
            otherYearFormat = pattern("dMMMyyyy", "d MMM yyyy"),
        )
    }

    private companion object {
        const val KEY_SORT_FIELD = "album.sort.field"
        const val KEY_SORT_DESC = "album.sort.desc"
    }
}
