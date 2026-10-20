package com.orbit.core.domain.usecase

import com.orbit.core.domain.model.UserPreferences
import com.orbit.core.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow

class GetPreferencesUseCase(
    private val preferencesRepository: PreferencesRepository
) {
    operator fun invoke(): Flow<UserPreferences> {
        return preferencesRepository.getUserPreferences()
    }
}
