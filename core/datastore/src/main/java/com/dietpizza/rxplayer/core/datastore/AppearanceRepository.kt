package com.dietpizza.rxplayer.core.datastore

import com.dietpizza.rxplayer.core.model.AppearanceSettings
import com.dietpizza.rxplayer.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/** Appearance settings (theme, dynamic colour, pure black). Bound to a Preferences DataStore impl in [com.dietpizza.rxplayer.core.datastore.di.DataStoreModule]. */
interface AppearanceRepository {
    val appearance: Flow<AppearanceSettings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setPureBlack(enabled: Boolean)
}
