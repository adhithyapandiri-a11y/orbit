package com.orbit.core.data.repository

import com.orbit.core.data.datastore.OrbitPreferencesDataSource
import com.orbit.core.domain.model.FeatureFlags
import com.orbit.core.domain.model.OverlayPreference
import com.orbit.core.domain.model.UserPreferences
import com.orbit.core.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow

class PreferencesRepositoryImpl(
    private val preferencesDataSource: OrbitPreferencesDataSource
) : PreferencesRepository {

    override fun getUserPreferences(): Flow<UserPreferences> {
        return preferencesDataSource.userPreferences
    }

    override suspend fun updateFeatureFlags(update: (FeatureFlags) -> FeatureFlags) {
        preferencesDataSource.updateFeatureFlags(update)
    }

    override suspend fun updateOverlayPreference(update: (OverlayPreference) -> OverlayPreference) {
        preferencesDataSource.updateOverlayPreference(update)
    }

    override suspend fun updateDarkTheme(theme: String) {
        preferencesDataSource.updateDarkTheme(theme)
    }

    override suspend fun clearPreferences() {
        preferencesDataSource.clearAll()
    }
}
