package com.orbit.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Today : Screen("today", "Today", Icons.Default.CheckCircle)
    data object Agenda : Screen("agenda", "Agenda", Icons.Default.DateRange)
    data object VideoInbox : Screen("video_inbox", "Videos", Icons.Default.PlayArrow)
    data object Attention : Screen("attention", "Attention", Icons.Default.Visibility)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)

    companion object {
        val bottomNavItems = listOf(Today, Agenda, VideoInbox, Attention, Settings)
    }
}
