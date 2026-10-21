package com.orbit.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.core.domain.model.OrbitResult
import com.orbit.core.domain.model.UserPreferences
import com.orbit.core.domain.usecase.EraseAllDataUseCase
import com.orbit.core.domain.usecase.ExportDataUseCase
import com.orbit.core.domain.usecase.GetPreferencesUseCase
import com.orbit.core.domain.usecase.ImportDataUseCase
import com.orbit.core.domain.usecase.UpdatePreferencesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val exportJsonPreview: String? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val isBusy: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    getPreferencesUseCase: GetPreferencesUseCase,
    private val updatePreferencesUseCase: UpdatePreferencesUseCase,
    private val exportDataUseCase: ExportDataUseCase,
    private val importDataUseCase: ImportDataUseCase,
    private val eraseAllDataUseCase: EraseAllDataUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> = combine(
        getPreferencesUseCase(),
        _uiState
    ) { prefs, state ->
        state.copy(preferences = prefs)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun exportData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, statusMessage = null, errorMessage = null) }
            when (val result = exportDataUseCase()) {
                is OrbitResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            exportJsonPreview = result.data,
                            statusMessage = "Data successfully exported locally."
                        )
                    }
                }
                is OrbitResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun importData(json: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, statusMessage = null, errorMessage = null) }
            when (val result = importDataUseCase(json)) {
                is OrbitResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            statusMessage = "Data successfully restored from backup."
                        )
                    }
                }
                is OrbitResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun eraseAllData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, statusMessage = null, errorMessage = null) }
            when (val result = eraseAllDataUseCase()) {
                is OrbitResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            exportJsonPreview = null,
                            statusMessage = "All personal data has been permanently deleted."
                        )
                    }
                }
                is OrbitResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun toggleAiExtraction(enabled: Boolean) {
        viewModelScope.launch {
            updatePreferencesUseCase.updateFeatureFlags { it.copy(aiExtractionEnabled = enabled) }
        }
    }

    fun toggleInstagramAwareness(enabled: Boolean) {
        viewModelScope.launch {
            updatePreferencesUseCase.updateFeatureFlags { it.copy(instagramAwarenessEnabled = enabled) }
        }
    }

    fun toggleOverlayWidget(enabled: Boolean) {
        viewModelScope.launch {
            updatePreferencesUseCase.updateFeatureFlags { it.copy(overlayWidgetEnabled = enabled) }
        }
    }

    fun toggleCalendarIntegration(enabled: Boolean) {
        viewModelScope.launch {
            updatePreferencesUseCase.updateFeatureFlags { it.copy(calendarIntegrationEnabled = enabled) }
        }
    }

    fun updateDarkTheme(theme: String) {
        viewModelScope.launch {
            updatePreferencesUseCase.updateDarkTheme(theme)
        }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }
}
