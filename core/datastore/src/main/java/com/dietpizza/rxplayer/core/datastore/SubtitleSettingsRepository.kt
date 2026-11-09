package com.dietpizza.rxplayer.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Subtitle preferences (plan §8 "Subtitles", decision 9). */
data class SubtitleSettings(
    /** Subtitles on/off. Picking "Off" in the player's subtitle sheet turns this off (sticky). */
    val enabled: Boolean = true,
    /** Auto-load same-name `.srt/.ass/.ssa/.vtt` next to local files. */
    val autoLoadSidecar: Boolean = true,
    /** Text size multiplier, 1f = default (about 5.3% of the video height). */
    val textScale: Float = 1f,
    /** Draw a translucent box behind the text (otherwise an outline/shadow only). */
    val background: Boolean = false,
) {
    companion object {
        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 2.5f
    }
}

interface SubtitleSettingsRepository {
    val settings: Flow<SubtitleSettings>

    suspend fun edit(transform: (SubtitleSettings) -> SubtitleSettings)
}

/** Stored in the app-wide Preferences DataStore under `subtitle.*` keys. */
@Singleton
class DataStoreSubtitleSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SubtitleSettingsRepository {

    override val settings: Flow<SubtitleSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::read)
        .distinctUntilChanged()

    override suspend fun edit(transform: (SubtitleSettings) -> SubtitleSettings) {
        dataStore.edit { prefs ->
            val s = transform(read(prefs))
            prefs[ENABLED] = s.enabled
            prefs[AUTO_LOAD_SIDECAR] = s.autoLoadSidecar
            prefs[TEXT_SCALE] = s.textScale.coerceIn(SubtitleSettings.MIN_SCALE, SubtitleSettings.MAX_SCALE)
            prefs[BACKGROUND] = s.background
        }
    }

    private fun read(prefs: Preferences): SubtitleSettings {
        val d = SubtitleSettings()
        return SubtitleSettings(
            enabled = prefs[ENABLED] ?: d.enabled,
            autoLoadSidecar = prefs[AUTO_LOAD_SIDECAR] ?: d.autoLoadSidecar,
            textScale = prefs[TEXT_SCALE] ?: d.textScale,
            background = prefs[BACKGROUND] ?: d.background,
        )
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("subtitle.enabled")
        val AUTO_LOAD_SIDECAR = booleanPreferencesKey("subtitle.auto_load_sidecar")
        val TEXT_SCALE = floatPreferencesKey("subtitle.text_scale")
        val BACKGROUND = booleanPreferencesKey("subtitle.background")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SubtitleSettingsModule {
    @Binds
    @Singleton
    abstract fun bindSubtitleSettingsRepository(
        impl: DataStoreSubtitleSettingsRepository,
    ): SubtitleSettingsRepository
}
