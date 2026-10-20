package com.orbit.core.domain.usecase

import com.orbit.core.domain.model.FeatureFlags
import com.orbit.core.domain.model.OverlayPreference
import com.orbit.core.domain.repository.PreferencesRepository

class UpdatePreferencesUseCase(
    private val preferencesRepository: PreferencesRepository
) {
    suspend fun updateFeatureFlags(update: (FeatureFlags) -> FeatureFlags) {
        preferencesRepository.updateFeatureFlags(update)
    }

    suspend fun updateOverlayPreference(update: (OverlayPreference) -> OverlayPreference) {
        preferencesRepository.updateOverlayPreference(update)
    }

    suspend fun updateDarkTheme(theme: String) {
        preferencesRepository.updateDarkTheme(theme)
    }
}
