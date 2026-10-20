package com.orbit.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FeatureFlags(
    val aiExtractionEnabled: Boolean = false,
    val instagramAwarenessEnabled: Boolean = false,
    val overlayWidgetEnabled: Boolean = false,
    val calendarIntegrationEnabled: Boolean = false
)

@Serializable
data class UserPreferences(
    val featureFlags: FeatureFlags = FeatureFlags(),
    val overlayPreference: OverlayPreference = OverlayPreference(),
    val darkThemeConfig: String = "SYSTEM",
    val crashReportingOptIn: Boolean = false,
    val analyticsOptIn: Boolean = false
)
