package com.dietpizza.rxplayer.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dietpizza.rxplayer.core.datastore.AppearanceRepository
import com.dietpizza.rxplayer.core.model.AppearanceSettings
import com.dietpizza.rxplayer.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AppInfo(val appName: String, val versionName: String)

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Ready(val appearance: AppearanceSettings, val appInfo: AppInfo) : SettingsUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appearanceRepository: AppearanceRepository,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val appInfo: AppInfo = context.readAppInfo()

    /**
     * What the user just picked. The controls render from this immediately (so the switch / toggle
     * animates right away), while the real theme change is written [APPLY_DELAY_MS] later, after
     * the control's own animation has finished. Without that, the whole screen restyles mid-animation.
     */
    private val pending = MutableStateFlow<AppearanceSettings?>(null)

    val uiState: StateFlow<SettingsUiState> =
        combine(appearanceRepository.appearance, pending) { saved, picked ->
            SettingsUiState.Ready(picked ?: saved, appInfo)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

    fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }
    fun setDynamicColor(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }
    fun setPureBlack(enabled: Boolean) = update { it.copy(pureBlack = enabled) }

    private fun update(change: (AppearanceSettings) -> AppearanceSettings) {
        val current = (uiState.value as? SettingsUiState.Ready)?.appearance ?: return
        val next = change(current)
        pending.value = next
        viewModelScope.launch {
            try {
                delay(APPLY_DELAY_MS)
            } finally {
                // Also runs if the screen closes before the delay is up, so the choice is never lost.
                withContext(NonCancellable) { appearanceRepository.update(pending.value ?: next) }
            }
        }
    }

    private companion object {
        const val APPLY_DELAY_MS = 200L
    }
}

private fun Context.readAppInfo(): AppInfo {
    val versionName = runCatching {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName
    }.getOrNull() ?: "Unknown"
    return AppInfo(
        appName = applicationInfo.loadLabel(packageManager).toString(),
        versionName = versionName,
    )
}
