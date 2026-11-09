package com.dietpizza.rxplayer.feature.videos

import android.text.format.DateUtils
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dietpizza.rxplayer.core.data.HistoryRepository
import com.dietpizza.rxplayer.core.model.HistoryEntry
import com.dietpizza.rxplayer.core.model.MediaKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class HistoryRow(
    val video: VideoRow,
    /** "Camera · 2 hours ago" */
    val summary: String,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val history: HistoryRepository,
) : ViewModel() {

    /** null = still loading. */
    val rows: StateFlow<List<HistoryRow>?> = history.history()
        .map { entries ->
            val now = System.currentTimeMillis()
            entries.map { it.toRow(now) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun clear() {
        viewModelScope.launch { history.clear() }
    }

    fun remove(key: String) {
        viewModelScope.launch { history.remove(listOf(MediaKey(key))) }
    }
}

private fun HistoryEntry.toRow(now: Long): HistoryRow {
    val folder = video.folderPath.substringAfterLast('/')
    val ago = DateUtils.getRelativeTimeSpanString(lastPlayedAt, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
    return HistoryRow(video = video.toRow(), summary = "$folder · $ago")
}
