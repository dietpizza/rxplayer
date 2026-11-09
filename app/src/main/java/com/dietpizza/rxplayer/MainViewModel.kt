package com.dietpizza.rxplayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dietpizza.rxplayer.core.datastore.AppearanceRepository
import com.dietpizza.rxplayer.core.model.AppearanceSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface MainUiState {
    /** Appearance not read from DataStore yet; the activity holds the first frame (system splash). */
    data object Loading : MainUiState

    data class Ready(val appearance: AppearanceSettings) : MainUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    appearanceRepository: AppearanceRepository,
) : ViewModel() {

    val uiState: StateFlow<MainUiState> = appearanceRepository.appearance
        .map<AppearanceSettings, MainUiState> { MainUiState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)
}
