package com.dietpizza.rxplayer.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.dietpizza.rxplayer.core.model.AlbumSort
import com.dietpizza.rxplayer.core.model.AlbumSortField
import com.dietpizza.rxplayer.core.model.FolderField
import com.dietpizza.rxplayer.core.model.VideoField
import com.dietpizza.rxplayer.core.model.LibrarySettings
import com.dietpizza.rxplayer.core.model.MediaSort
import com.dietpizza.rxplayer.core.model.MediaSortField
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

    override suspend fun setGalleryGridColumns(columns: Int) {
        val value = if (columns in LibrarySettings.MIN_GRID_COLUMNS..LibrarySettings.MAX_GRID_COLUMNS) {
            columns
        } else {
            LibrarySettings.GRID_COLUMNS_AUTO
        }
        dataStore.edit { it[Keys.GALLERY_GRID_COLUMNS] = value }
    }

    override suspend fun setAlbumSort(sort: AlbumSort) {
        dataStore.edit {
            it[Keys.ALBUM_SORT_FIELD] = sort.field.name
            it[Keys.ALBUM_SORT_DESC] = sort.descending
        }
    }

    override suspend fun setMediaSort(sort: MediaSort) {
        dataStore.edit {
            it[Keys.MEDIA_SORT_FIELD] = sort.field.name
            it[Keys.MEDIA_SORT_DESC] = sort.descending
        }
    }

    override suspend fun setVideoFolderSort(sort: AlbumSort) {
        dataStore.edit {
            it[Keys.FOLDER_SORT_FIELD] = sort.field.name
            it[Keys.FOLDER_SORT_DESC] = sort.descending
        }
    }

    override suspend fun setFolderViewMode(mode: VideoViewMode) {
        dataStore.edit { it[Keys.FOLDER_VIEW_MODE] = mode.name }
    }

    override suspend fun setFolderFields(fields: Set<FolderField>) {
        dataStore.edit { it[Keys.FOLDER_FIELDS] = fields.mapTo(HashSet()) { f -> f.name } }
    }

    override suspend fun setVideoFields(fields: Set<VideoField>) {
        dataStore.edit { it[Keys.VIDEO_FIELDS] = fields.mapTo(HashSet()) { f -> f.name } }
    }

    override suspend fun setAlbumPinned(path: String, pinned: Boolean) {
        dataStore.edit { it[Keys.PINNED_ALBUMS] = toggled(it[Keys.PINNED_ALBUMS], path, pinned) }
    }

    override suspend fun setVideoFolderPinned(path: String, pinned: Boolean) {
        dataStore.edit { it[Keys.PINNED_VIDEO_FOLDERS] = toggled(it[Keys.PINNED_VIDEO_FOLDERS], path, pinned) }
    }

    override suspend fun movePins(oldPath: String, newPath: String) {
        if (oldPath == newPath) return
        dataStore.edit { prefs ->
            for (key in listOf(Keys.PINNED_ALBUMS, Keys.PINNED_VIDEO_FOLDERS)) {
                val current = prefs[key] ?: continue
                prefs[key] = current.mapTo(HashSet()) { p ->
                    when {
                        p == oldPath -> newPath
                        p.startsWith("$oldPath/") -> newPath + p.substring(oldPath.length)
                        else -> p
                    }
                }
            }
        }
    }

    private fun toggled(current: Set<String>?, path: String, on: Boolean): Set<String> =
        (current ?: emptySet()).let { if (on) it + path else it - path }

    private inline fun <reified E : Enum<E>> enumSet(names: Set<String>?, fallback: Set<E>): Set<E> =
        names?.mapNotNullTo(LinkedHashSet()) { n -> enumValues<E>().firstOrNull { it.name == n } } ?: fallback

    private fun albumSort(prefs: Preferences, field: Preferences.Key<String>, desc: Preferences.Key<Boolean>, fallback: AlbumSort): AlbumSort {
        val f = prefs[field]?.let { n -> AlbumSortField.entries.firstOrNull { it.name == n } } ?: return fallback
        return AlbumSort(f, prefs[desc] ?: AlbumSort.defaultFor(f).descending)
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
            galleryGridColumns = prefs[Keys.GALLERY_GRID_COLUMNS] ?: d.galleryGridColumns,
            albumSort = albumSort(prefs, Keys.ALBUM_SORT_FIELD, Keys.ALBUM_SORT_DESC, d.albumSort),
            mediaSort = prefs[Keys.MEDIA_SORT_FIELD]
                ?.let { n -> MediaSortField.entries.firstOrNull { it.name == n } }
                ?.let { f -> MediaSort(f, prefs[Keys.MEDIA_SORT_DESC] ?: MediaSort.defaultFor(f).descending) }
                ?: d.mediaSort,
            videoFolderSort = albumSort(prefs, Keys.FOLDER_SORT_FIELD, Keys.FOLDER_SORT_DESC, d.videoFolderSort),
            folderViewMode = prefs[Keys.FOLDER_VIEW_MODE]
                ?.let { n -> VideoViewMode.entries.firstOrNull { it.name == n } }
                ?: d.folderViewMode,
            folderFields = enumSet(prefs[Keys.FOLDER_FIELDS], d.folderFields),
            videoFields = enumSet(prefs[Keys.VIDEO_FIELDS], d.videoFields),
            pinnedAlbums = prefs[Keys.PINNED_ALBUMS] ?: d.pinnedAlbums,
            pinnedVideoFolders = prefs[Keys.PINNED_VIDEO_FOLDERS] ?: d.pinnedVideoFolders,
        )
    }

    private object Keys {
        val VIDEO_VIEW_MODE = stringPreferencesKey("library.video_view_mode")
        val VIDEO_SORT_FIELD = stringPreferencesKey("library.video_sort_field")
        val VIDEO_SORT_DESC = booleanPreferencesKey("library.video_sort_descending")
        val SHOW_HIDDEN = booleanPreferencesKey("library.show_hidden")
        val GALLERY_GRID_COLUMNS = intPreferencesKey("library.gallery_grid_columns")
        val ALBUM_SORT_FIELD = stringPreferencesKey("library.album_sort_field")
        val ALBUM_SORT_DESC = booleanPreferencesKey("library.album_sort_descending")
        val MEDIA_SORT_FIELD = stringPreferencesKey("library.media_sort_field")
        val MEDIA_SORT_DESC = booleanPreferencesKey("library.media_sort_descending")
        val FOLDER_SORT_FIELD = stringPreferencesKey("library.video_folder_sort_field")
        val FOLDER_SORT_DESC = booleanPreferencesKey("library.video_folder_sort_descending")
        val FOLDER_VIEW_MODE = stringPreferencesKey("library.video_folder_view_mode")
        val FOLDER_FIELDS = stringSetPreferencesKey("library.video_folder_fields")
        val VIDEO_FIELDS = stringSetPreferencesKey("library.video_fields")
        val PINNED_ALBUMS = stringSetPreferencesKey("library.pinned_albums")
        val PINNED_VIDEO_FOLDERS = stringSetPreferencesKey("library.pinned_video_folders")
    }
}
