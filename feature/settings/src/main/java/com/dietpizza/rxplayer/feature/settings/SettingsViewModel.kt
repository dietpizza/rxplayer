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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    val uiState: StateFlow<SettingsUiState> = appearanceRepository.appearance
        .map<AppearanceSettings, SettingsUiState> { SettingsUiState.Ready(it, appInfo) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { appearanceRepository.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { appearanceRepository.setDynamicColor(enabled) }
    fun setPureBlack(enabled: Boolean) = viewModelScope.launch { appearanceRepository.setPureBlack(enabled) }
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
