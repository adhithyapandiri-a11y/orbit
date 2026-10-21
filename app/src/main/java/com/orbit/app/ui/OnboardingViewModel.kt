package com.orbit.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.core.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingStep(
    val title: String,
    val subtitle: String,
    val description: String,
    val badge: String
)

data class OnboardingUiState(
    val currentStepIndex: Int = 0,
    val steps: List<OnboardingStep> = listOf(
        OnboardingStep(
            title = "Welcome to Orbit",
            subtitle = "Private Local Intelligence",
            description = "Orbit is an offline-first, private personal cockpit. Your tasks, agenda, videos, and focus data stay 100% on this device with zero external telemetry.",
            badge = "PRIVACY FIRST"
        ),
        OnboardingStep(
            title = "Four Unified Pillars",
            subtitle = "Everything in One Place",
            description = "• Today: Daily focus & overdue task recovery\n• Agenda: 24-hour visual schedule & linked notes\n• MyTube: Distraction-free curated video queue\n• Attention: Non-intrusive focus tracking & awareness timer",
            badge = "LOCAL COCKPIT"
        ),
        OnboardingStep(
            title = "Permissions & Ready",
            subtitle = "Transparent & Optional",
            description = "• Calendar: (Optional) Read device events for your Agenda\n• Overlay: (Optional) Show floating awareness counter over apps\n• Usage Stats: (Optional) Measure focused vs reel screen time\n\nYou are in full control and can adjust permissions in Settings at any time.",
            badge = "READY TO LAUNCH"
        )
    ),
    val isCompleted: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun nextStep() {
        val nextIndex = _uiState.value.currentStepIndex + 1
        if (nextIndex < _uiState.value.steps.size) {
            _uiState.update { it.copy(currentStepIndex = nextIndex) }
        } else {
            completeOnboarding()
        }
    }

    fun previousStep() {
        val prevIndex = _uiState.value.currentStepIndex - 1
        if (prevIndex >= 0) {
            _uiState.update { it.copy(currentStepIndex = prevIndex) }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            preferencesRepository.setOnboardingCompleted(true)
            _uiState.update { it.copy(isCompleted = true) }
        }
    }
}
