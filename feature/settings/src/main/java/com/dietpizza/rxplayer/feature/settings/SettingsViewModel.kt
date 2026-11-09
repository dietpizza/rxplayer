package com.dietpizza.rxplayer.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import com.dietpizza.rxplayer.core.data.HistoryRepository
import com.dietpizza.rxplayer.core.datastore.AppearanceRepository
import com.dietpizza.rxplayer.core.datastore.LibrarySettingsRepository
import com.dietpizza.rxplayer.core.datastore.PlayerSettingsRepository
import com.dietpizza.rxplayer.core.datastore.SubtitleSettings
import com.dietpizza.rxplayer.core.datastore.SubtitleSettingsRepository
import com.dietpizza.rxplayer.core.model.AppearanceSettings
import com.dietpizza.rxplayer.core.model.LibrarySettings
import com.dietpizza.rxplayer.core.model.PlayerSettings
import com.dietpizza.rxplayer.core.model.ThemeMode
import com.dietpizza.rxplayer.core.model.VideoSort
import com.dietpizza.rxplayer.core.model.VideoViewMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(val appName: String, val versionName: String)

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Ready(
        val appearance: AppearanceSettings,
        val appInfo: AppInfo,
        val library: LibrarySettings = LibrarySettings(),
        val player: PlayerSettings = PlayerSettings(),
        val subtitles: SubtitleSettings = SubtitleSettings(),
        /** Thumbnail + seek-sprite cache size in bytes; null while it's being measured. */
        val cacheBytes: Long? = null,
    ) : SettingsUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appearanceRepository: AppearanceRepository,
    private val libraryRepository: LibrarySettingsRepository,
    private val playerRepository: PlayerSettingsRepository,
    private val subtitleRepository: SubtitleSettingsRepository,
    private val history: HistoryRepository,
    private val imageLoader: ImageLoader,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val appInfo: AppInfo = context.readAppInfo()

    /**
     * What the user just picked. The controls render from this immediately (so the switch / toggle
     * animates right away), while the real theme change is written [APPLY_DELAY_MS] later, after
     * the control's own animation has finished. Without that, the whole screen restyles mid-animation.
     */
    private val pending = MutableStateFlow<AppearanceSettings?>(null)

    private val cacheBytes = MutableStateFlow<Long?>(null)

    private val appearance: Flow<AppearanceSettings> =
        combine(appearanceRepository.appearance, pending) { saved, picked -> picked ?: saved }

    val uiState: StateFlow<SettingsUiState> =
        combine(
            appearance,
            libraryRepository.settings,
            playerRepository.settings,
            subtitleRepository.settings,
            cacheBytes,
        ) { a, library, player, subtitles, cache ->
            SettingsUiState.Ready(a, appInfo, library, player, subtitles, cache)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        measureCache()
    }

    // ---- Appearance (applied after a short delay, plan §9) ------------------------------------

    fun setThemeMode(mode: ThemeMode) = updateAppearance { it.copy(themeMode = mode) }
    fun setDynamicColor(enabled: Boolean) = updateAppearance { it.copy(dynamicColor = enabled) }
    fun setPureBlack(enabled: Boolean) = updateAppearance { it.copy(pureBlack = enabled) }

    private fun updateAppearance(change: (AppearanceSettings) -> AppearanceSettings) {
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

    // ---- Library ------------------------------------------------------------------------------

    fun setVideoViewMode(mode: VideoViewMode) = write { libraryRepository.setVideoViewMode(mode) }
    fun setVideoSort(sort: VideoSort) = write { libraryRepository.setVideoSort(sort) }
    fun setGalleryGridColumns(columns: Int) = write { libraryRepository.setGalleryGridColumns(columns) }
    fun setShowHidden(show: Boolean) = write { libraryRepository.setShowHidden(show) }

    // ---- Player / subtitles -------------------------------------------------------------------

    fun editPlayer(transform: (PlayerSettings) -> PlayerSettings) = write { playerRepository.edit(transform) }
    fun editSubtitles(transform: (SubtitleSettings) -> SubtitleSettings) = write { subtitleRepository.edit(transform) }

    /** DataStore writes outlive the screen (closing Settings right after a tap still saves it). */
    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch { withContext(NonCancellable) { block() } }
    }

    // ---- Storage ------------------------------------------------------------------------------

    private val spritesDir: File get() = File(context.cacheDir, SPRITES_DIR)

    private fun measureCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val sprites = spritesDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            cacheBytes.value = sprites + (imageLoader.diskCache?.size ?: 0L) + (imageLoader.memoryCache?.size ?: 0L)
        }
    }

    fun clearCache() {
        cacheBytes.value = null
        viewModelScope.launch(Dispatchers.IO) {
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
            spritesDir.listFiles()?.forEach { it.deleteRecursively() }
            _messages.send("Cache cleared")
            measureCache()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            withContext(NonCancellable) { history.clear() }
            _messages.send("History cleared")
        }
    }

    private companion object {
        const val APPLY_DELAY_MS = 200L

        /** Seek-bar sprites (core/player SeekPreviewEngine). */
        const val SPRITES_DIR = "sprites"
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
