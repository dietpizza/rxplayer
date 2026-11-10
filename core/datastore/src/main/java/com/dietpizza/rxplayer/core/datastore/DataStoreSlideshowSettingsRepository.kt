package com.dietpizza.rxplayer.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.dietpizza.rxplayer.core.model.SlideshowOptions
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** [SlideshowSettingsRepository] backed by the app-wide Preferences DataStore (`slideshow.*` keys). */
@Singleton
class DataStoreSlideshowSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SlideshowSettingsRepository {

    override val options: Flow<SlideshowOptions> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::read)
        .distinctUntilChanged()

    override suspend fun setOptions(options: SlideshowOptions) {
        val o = options.sanitized()
        dataStore.edit {
            it[Keys.INCLUDE_PHOTOS] = o.includePhotos
            it[Keys.INCLUDE_VIDEOS] = o.includeVideos
            it[Keys.INCLUDE_GIFS] = o.includeGifs
            it[Keys.SHUFFLE] = o.shuffle
            it[Keys.LOOP] = o.loop
            it[Keys.SECONDS_PER_PHOTO] = o.secondsPerPhoto
        }
    }

    private fun read(prefs: Preferences): SlideshowOptions {
        val d = SlideshowOptions.DEFAULT
        return SlideshowOptions(
            includePhotos = prefs[Keys.INCLUDE_PHOTOS] ?: d.includePhotos,
            includeVideos = prefs[Keys.INCLUDE_VIDEOS] ?: d.includeVideos,
            includeGifs = prefs[Keys.INCLUDE_GIFS] ?: d.includeGifs,
            shuffle = prefs[Keys.SHUFFLE] ?: d.shuffle,
            loop = prefs[Keys.LOOP] ?: d.loop,
            secondsPerPhoto = prefs[Keys.SECONDS_PER_PHOTO] ?: d.secondsPerPhoto,
        ).sanitized()
    }

    private object Keys {
        val INCLUDE_PHOTOS = booleanPreferencesKey("slideshow.include_photos")
        val INCLUDE_VIDEOS = booleanPreferencesKey("slideshow.include_videos")
        val INCLUDE_GIFS = booleanPreferencesKey("slideshow.include_gifs")
        val SHUFFLE = booleanPreferencesKey("slideshow.shuffle")
        val LOOP = booleanPreferencesKey("slideshow.loop")
        val SECONDS_PER_PHOTO = intPreferencesKey("slideshow.seconds_per_photo")
    }
}
