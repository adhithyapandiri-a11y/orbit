package com.orbit.core.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class OrbitSpacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp
)

val LocalOrbitSpacing = androidx.compose.runtime.staticCompositionLocalOf { OrbitSpacing() }
