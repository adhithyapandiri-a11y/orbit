package com.orbit.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = OrbitDarkPrimary,
    onPrimary = OrbitDarkOnPrimary,
    primaryContainer = Color(0xFF2A170F),
    onPrimaryContainer = OrbitOrangeLight,
    background = OrbitDarkBackground,
    surface = OrbitDarkSurface,
    surfaceVariant = OrbitDarkSurfaceVariant,
    onBackground = OrbitDarkOnSurface,
    onSurface = OrbitDarkOnSurface,
    onSurfaceVariant = OrbitDarkOnSurfaceVariant,
    outline = OrbitCardBorder,
    error = OrbitRed
)

private val LightColorScheme = lightColorScheme(
    primary = OrbitLightPrimary,
    onPrimary = OrbitLightOnPrimary,
    primaryContainer = Color(0xFFFFECE3),
    onPrimaryContainer = Color(0xFF5A1D00),
    background = OrbitLightBackground,
    surface = OrbitLightSurface,
    surfaceVariant = OrbitLightSurfaceVariant,
    onBackground = OrbitLightOnSurface,
    onSurface = OrbitLightOnSurface,
    onSurfaceVariant = OrbitLightOnSurfaceVariant,
    outline = Color(0xFFE2E8F0),
    error = OrbitRed
)

@Composable
fun OrbitTheme(
    darkTheme: Boolean = true, // Default to true for Orbit's signature dark aesthetic
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(
        LocalOrbitSpacing provides OrbitSpacing()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = OrbitTypography,
            content = content
        )
    }
}
