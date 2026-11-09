package com.dietpizza.rxplayer.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dietpizza.rxplayer.core.model.PlayerSettings
import com.dietpizza.rxplayer.core.model.ResumeMode
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** [PlayerSettingsRepository] backed by the app-wide Preferences DataStore (`player.*` keys). */
@Singleton
class DataStorePlayerSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PlayerSettingsRepository {

    override val settings: Flow<PlayerSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::read)
        .distinctUntilChanged()

    override suspend fun setMuted(muted: Boolean) {
        dataStore.edit { it[Keys.MUTED] = muted }
    }

    override suspend fun update(settings: PlayerSettings) {
        dataStore.edit { write(it, settings) }
    }

    override suspend fun edit(transform: (PlayerSettings) -> PlayerSettings) {
        dataStore.edit { write(it, transform(read(it))) }
    }

    private fun read(prefs: Preferences): PlayerSettings {
        val d = PlayerSettings()
        return PlayerSettings(
            muted = prefs[Keys.MUTED] ?: d.muted,
            seekStepSeconds = prefs[Keys.SEEK_STEP_SECONDS] ?: d.seekStepSeconds,
            swipeSeekSensitivity = prefs[Keys.SWIPE_SEEK_SENSITIVITY] ?: d.swipeSeekSensitivity,
            doubleTapPlayPause = prefs[Keys.DOUBLE_TAP_PLAY_PAUSE] ?: d.doubleTapPlayPause,
            longPressSpeedEnabled = prefs[Keys.LONG_PRESS_SPEED_ENABLED] ?: d.longPressSpeedEnabled,
            longPressSpeed = prefs[Keys.LONG_PRESS_SPEED] ?: d.longPressSpeed,
            pinchToZoom = prefs[Keys.PINCH_TO_ZOOM] ?: d.pinchToZoom,
            swipeDownToClose = prefs[Keys.SWIPE_DOWN_TO_CLOSE] ?: d.swipeDownToClose,
            seekBarThumbnails = prefs[Keys.SEEK_BAR_THUMBNAILS] ?: d.seekBarThumbnails,
            resumeMode = prefs[Keys.RESUME_MODE]
                ?.let { name -> ResumeMode.entries.firstOrNull { it.name == name } }
                ?: d.resumeMode,
            autoPlayNext = prefs[Keys.AUTO_PLAY_NEXT] ?: d.autoPlayNext,
            shuffle = prefs[Keys.SHUFFLE] ?: d.shuffle,
            autoPip = prefs[Keys.AUTO_PIP] ?: d.autoPip,
            pauseOnHeadphonesUnplugged = prefs[Keys.PAUSE_ON_UNPLUG] ?: d.pauseOnHeadphonesUnplugged,
            pauseDuringCalls = prefs[Keys.PAUSE_DURING_CALLS] ?: d.pauseDuringCalls,
            resumeAfterCall = prefs[Keys.RESUME_AFTER_CALL] ?: d.resumeAfterCall,
        )
    }

    private fun write(prefs: MutablePreferences, s: PlayerSettings) {
        prefs[Keys.MUTED] = s.muted
        prefs[Keys.SEEK_STEP_SECONDS] = s.seekStepSeconds
        prefs[Keys.SWIPE_SEEK_SENSITIVITY] = s.swipeSeekSensitivity
        prefs[Keys.DOUBLE_TAP_PLAY_PAUSE] = s.doubleTapPlayPause
        prefs[Keys.LONG_PRESS_SPEED_ENABLED] = s.longPressSpeedEnabled
        prefs[Keys.LONG_PRESS_SPEED] = s.longPressSpeed
        prefs[Keys.PINCH_TO_ZOOM] = s.pinchToZoom
        prefs[Keys.SWIPE_DOWN_TO_CLOSE] = s.swipeDownToClose
        prefs[Keys.SEEK_BAR_THUMBNAILS] = s.seekBarThumbnails
        prefs[Keys.RESUME_MODE] = s.resumeMode.name
        prefs[Keys.AUTO_PLAY_NEXT] = s.autoPlayNext
        prefs[Keys.SHUFFLE] = s.shuffle
        prefs[Keys.AUTO_PIP] = s.autoPip
        prefs[Keys.PAUSE_ON_UNPLUG] = s.pauseOnHeadphonesUnplugged
        prefs[Keys.PAUSE_DURING_CALLS] = s.pauseDuringCalls
        prefs[Keys.RESUME_AFTER_CALL] = s.resumeAfterCall
    }

    private object Keys {
        val MUTED = booleanPreferencesKey("player.muted")
        val SEEK_STEP_SECONDS = intPreferencesKey("player.seek_step_seconds")
        val SWIPE_SEEK_SENSITIVITY = floatPreferencesKey("player.swipe_seek_sensitivity")
        val DOUBLE_TAP_PLAY_PAUSE = booleanPreferencesKey("player.double_tap_play_pause")
        val LONG_PRESS_SPEED_ENABLED = booleanPreferencesKey("player.long_press_speed_enabled")
        val LONG_PRESS_SPEED = floatPreferencesKey("player.long_press_speed")
        val PINCH_TO_ZOOM = booleanPreferencesKey("player.pinch_to_zoom")
        val SWIPE_DOWN_TO_CLOSE = booleanPreferencesKey("player.swipe_down_to_close")
        val SEEK_BAR_THUMBNAILS = booleanPreferencesKey("player.seek_bar_thumbnails")
        val RESUME_MODE = stringPreferencesKey("player.resume_mode")
        val AUTO_PLAY_NEXT = booleanPreferencesKey("player.auto_play_next")
        val SHUFFLE = booleanPreferencesKey("player.shuffle")
        val AUTO_PIP = booleanPreferencesKey("player.auto_pip")
        val PAUSE_ON_UNPLUG = booleanPreferencesKey("player.pause_on_headphones_unplugged")
        val PAUSE_DURING_CALLS = booleanPreferencesKey("player.pause_during_calls")
        val RESUME_AFTER_CALL = booleanPreferencesKey("player.resume_after_call")
    }
}
