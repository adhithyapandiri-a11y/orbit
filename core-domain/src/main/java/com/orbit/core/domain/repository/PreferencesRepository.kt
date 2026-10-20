package com.orbit.core.domain.repository

import com.orbit.core.domain.model.FeatureFlags
import com.orbit.core.domain.model.OverlayPreference
import com.orbit.core.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    fun getUserPreferences(): Flow<UserPreferences>
    suspend fun updateFeatureFlags(update: (FeatureFlags) -> FeatureFlags)
    suspend fun updateOverlayPreference(update: (OverlayPreference) -> OverlayPreference)
    suspend fun updateDarkTheme(theme: String)
    suspend fun clearPreferences()
}
