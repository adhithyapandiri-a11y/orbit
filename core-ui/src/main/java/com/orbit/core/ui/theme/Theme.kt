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
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = OrbitDarkPrimary,
    onPrimary = OrbitDarkOnPrimary,
    background = OrbitDarkBackground,
    surface = OrbitDarkSurface,
    surfaceVariant = OrbitDarkSurfaceVariant,
    onBackground = OrbitDarkOnSurface,
    onSurface = OrbitDarkOnSurface,
    onSurfaceVariant = OrbitDarkOnSurfaceVariant,
    error = OrbitRed
)

private val LightColorScheme = lightColorScheme(
    primary = OrbitLightPrimary,
    onPrimary = OrbitLightOnPrimary,
    background = OrbitLightBackground,
    surface = OrbitLightSurface,
    surfaceVariant = OrbitLightSurfaceVariant,
    onBackground = OrbitLightOnSurface,
    onSurface = OrbitLightOnSurface,
    onSurfaceVariant = OrbitLightOnSurfaceVariant,
    error = OrbitRed
)

@Composable
fun OrbitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
