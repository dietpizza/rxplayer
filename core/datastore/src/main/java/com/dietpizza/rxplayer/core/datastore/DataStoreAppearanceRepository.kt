package com.dietpizza.rxplayer.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dietpizza.rxplayer.core.model.AppearanceSettings
import com.dietpizza.rxplayer.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** [AppearanceRepository] backed by Preferences DataStore. */
@Singleton
class DataStoreAppearanceRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AppearanceRepository {

    override val appearance: Flow<AppearanceSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            val defaults = AppearanceSettings()
            AppearanceSettings(
                themeMode = prefs[Keys.THEME_MODE]
                    ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                    ?: defaults.themeMode,
                dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: defaults.dynamicColor,
                pureBlack = prefs[Keys.PURE_BLACK] ?: defaults.pureBlack,
            )
        }
        .distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setPureBlack(enabled: Boolean) {
        dataStore.edit { it[Keys.PURE_BLACK] = enabled }
    }

    override suspend fun update(settings: AppearanceSettings) {
        dataStore.edit {
            it[Keys.THEME_MODE] = settings.themeMode.name
            it[Keys.DYNAMIC_COLOR] = settings.dynamicColor
            it[Keys.PURE_BLACK] = settings.pureBlack
        }
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("appearance.theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("appearance.dynamic_color")
        val PURE_BLACK = booleanPreferencesKey("appearance.pure_black")
    }
}
