package com.orbit.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.orbit.core.domain.model.FeatureFlags
import com.orbit.core.domain.model.OverlayDisplayMode
import com.orbit.core.domain.model.OverlayPreference
import com.orbit.core.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

class OrbitPreferencesDataSource(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val KEY_FEATURE_AI = booleanPreferencesKey("feature_ai_extraction")
        val KEY_FEATURE_INSTAGRAM = booleanPreferencesKey("feature_instagram_awareness")
        val KEY_FEATURE_OVERLAY = booleanPreferencesKey("feature_overlay_widget")
        val KEY_FEATURE_CALENDAR = booleanPreferencesKey("feature_calendar_integration")

        val KEY_OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val KEY_OVERLAY_X = floatPreferencesKey("overlay_normalized_x")
        val KEY_OVERLAY_Y = floatPreferencesKey("overlay_normalized_y")
        val KEY_OVERLAY_OPACITY = floatPreferencesKey("overlay_opacity")
        val KEY_OVERLAY_MODE = stringPreferencesKey("overlay_display_mode")

        val KEY_DARK_THEME = stringPreferencesKey("dark_theme_config")
        val KEY_CRASH_OPT_IN = booleanPreferencesKey("crash_reporting_opt_in")
        val KEY_ANALYTICS_OPT_IN = booleanPreferencesKey("analytics_opt_in")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val userPreferences: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val featureFlags = FeatureFlags(
                aiExtractionEnabled = preferences[KEY_FEATURE_AI] ?: false,
                instagramAwarenessEnabled = preferences[KEY_FEATURE_INSTAGRAM] ?: false,
                overlayWidgetEnabled = preferences[KEY_FEATURE_OVERLAY] ?: false,
                calendarIntegrationEnabled = preferences[KEY_FEATURE_CALENDAR] ?: false
            )
            val overlayModeString = preferences[KEY_OVERLAY_MODE] ?: OverlayDisplayMode.COMPACT.name
            val overlayMode = try {
                OverlayDisplayMode.valueOf(overlayModeString)
            } catch (_: Exception) {
                OverlayDisplayMode.COMPACT
            }
            val overlayPreference = OverlayPreference(
                enabled = preferences[KEY_OVERLAY_ENABLED] ?: false,
                normalizedX = preferences[KEY_OVERLAY_X] ?: 0.85f,
                normalizedY = preferences[KEY_OVERLAY_Y] ?: 0.35f,
                opacity = preferences[KEY_OVERLAY_OPACITY] ?: 0.9f,
                displayMode = overlayMode
            )
            UserPreferences(
                featureFlags = featureFlags,
                overlayPreference = overlayPreference,
                darkThemeConfig = preferences[KEY_DARK_THEME] ?: "SYSTEM",
                crashReportingOptIn = preferences[KEY_CRASH_OPT_IN] ?: false,
                analyticsOptIn = preferences[KEY_ANALYTICS_OPT_IN] ?: false,
                hasCompletedOnboarding = preferences[KEY_ONBOARDING_COMPLETED] ?: false
            )
        }

    suspend fun updateFeatureFlags(update: (FeatureFlags) -> FeatureFlags) {
        dataStore.edit { preferences ->
            val current = FeatureFlags(
                aiExtractionEnabled = preferences[KEY_FEATURE_AI] ?: false,
                instagramAwarenessEnabled = preferences[KEY_FEATURE_INSTAGRAM] ?: false,
                overlayWidgetEnabled = preferences[KEY_FEATURE_OVERLAY] ?: false,
                calendarIntegrationEnabled = preferences[KEY_FEATURE_CALENDAR] ?: false
            )
            val updated = update(current)
            preferences[KEY_FEATURE_AI] = updated.aiExtractionEnabled
            preferences[KEY_FEATURE_INSTAGRAM] = updated.instagramAwarenessEnabled
            preferences[KEY_FEATURE_OVERLAY] = updated.overlayWidgetEnabled
            preferences[KEY_FEATURE_CALENDAR] = updated.calendarIntegrationEnabled
        }
    }

    suspend fun updateOverlayPreference(update: (OverlayPreference) -> OverlayPreference) {
        dataStore.edit { preferences ->
            val currentMode = try {
                OverlayDisplayMode.valueOf(preferences[KEY_OVERLAY_MODE] ?: OverlayDisplayMode.COMPACT.name)
            } catch (_: Exception) {
                OverlayDisplayMode.COMPACT
            }
            val current = OverlayPreference(
                enabled = preferences[KEY_OVERLAY_ENABLED] ?: false,
                normalizedX = preferences[KEY_OVERLAY_X] ?: 0.85f,
                normalizedY = preferences[KEY_OVERLAY_Y] ?: 0.35f,
                opacity = preferences[KEY_OVERLAY_OPACITY] ?: 0.9f,
                displayMode = currentMode
            )
            val updated = update(current)
            preferences[KEY_OVERLAY_ENABLED] = updated.enabled
            preferences[KEY_OVERLAY_X] = updated.normalizedX
            preferences[KEY_OVERLAY_Y] = updated.normalizedY
            preferences[KEY_OVERLAY_OPACITY] = updated.opacity
            preferences[KEY_OVERLAY_MODE] = updated.displayMode.name
        }
    }

    suspend fun updateDarkTheme(theme: String) {
        dataStore.edit { preferences ->
            preferences[KEY_DARK_THEME] = theme
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean = true) {
        dataStore.edit { preferences ->
            preferences[KEY_ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun clearAll() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
