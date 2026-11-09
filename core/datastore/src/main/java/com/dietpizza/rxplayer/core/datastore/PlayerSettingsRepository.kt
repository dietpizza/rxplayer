package com.dietpizza.rxplayer.core.datastore

import com.dietpizza.rxplayer.core.model.PlayerSettings
import kotlinx.coroutines.flow.Flow

/**
 * Player preferences (plan §8), including the global sticky mute (plan §3.3). Bound to a
 * Preferences DataStore impl in [com.dietpizza.rxplayer.core.datastore.di.PlayerSettingsModule].
 */
interface PlayerSettingsRepository {
    val settings: Flow<PlayerSettings>

    suspend fun setMuted(muted: Boolean)

    /** Writes every player field in one DataStore transaction, so observers see one change. */
    suspend fun update(settings: PlayerSettings)

    /** Read-modify-write in one transaction (e.g. `edit { it.copy(autoPlayNext = false) }`). */
    suspend fun edit(transform: (PlayerSettings) -> PlayerSettings)
}
