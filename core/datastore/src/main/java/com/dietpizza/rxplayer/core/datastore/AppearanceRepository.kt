package com.dietpizza.rxplayer.core.datastore

import com.dietpizza.rxplayer.core.model.AppearanceSettings
import com.dietpizza.rxplayer.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/** Contract used by :app and :feature:settings. Implementation + Hilt binding owned by the designsystem agent. */
interface AppearanceRepository {
    val appearance: Flow<AppearanceSettings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setPureBlack(enabled: Boolean)
}
