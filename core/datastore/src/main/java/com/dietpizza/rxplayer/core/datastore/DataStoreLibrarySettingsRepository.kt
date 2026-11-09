package com.dietpizza.rxplayer.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dietpizza.rxplayer.core.model.LibrarySettings
import com.dietpizza.rxplayer.core.model.VideoSort
import com.dietpizza.rxplayer.core.model.VideoSortField
import com.dietpizza.rxplayer.core.model.VideoViewMode
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** [LibrarySettingsRepository] backed by the app-wide Preferences DataStore (`library.*` keys). */
@Singleton
class DataStoreLibrarySettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : LibrarySettingsRepository {

    override val settings: Flow<LibrarySettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::read)
        .distinctUntilChanged()

    override suspend fun setVideoViewMode(mode: VideoViewMode) {
        dataStore.edit { it[Keys.VIDEO_VIEW_MODE] = mode.name }
    }

    override suspend fun setVideoSort(sort: VideoSort) {
        dataStore.edit {
            it[Keys.VIDEO_SORT_FIELD] = sort.field.name
            it[Keys.VIDEO_SORT_DESC] = sort.descending
        }
    }

    override suspend fun setShowHidden(show: Boolean) {
        dataStore.edit { it[Keys.SHOW_HIDDEN] = show }
    }

    private fun read(prefs: Preferences): LibrarySettings {
        val d = LibrarySettings()
        val field = prefs[Keys.VIDEO_SORT_FIELD]?.let { n -> VideoSortField.entries.firstOrNull { it.name == n } }
        return LibrarySettings(
            videoViewMode = prefs[Keys.VIDEO_VIEW_MODE]
                ?.let { n -> VideoViewMode.entries.firstOrNull { it.name == n } }
                ?: d.videoViewMode,
            videoSort = if (field == null) {
                d.videoSort
            } else {
                VideoSort(field, prefs[Keys.VIDEO_SORT_DESC] ?: VideoSort.defaultFor(field).descending)
            },
            showHidden = prefs[Keys.SHOW_HIDDEN] ?: d.showHidden,
        )
    }

    private object Keys {
        val VIDEO_VIEW_MODE = stringPreferencesKey("library.video_view_mode")
        val VIDEO_SORT_FIELD = stringPreferencesKey("library.video_sort_field")
        val VIDEO_SORT_DESC = booleanPreferencesKey("library.video_sort_descending")
        val SHOW_HIDDEN = booleanPreferencesKey("library.show_hidden")
    }
}
