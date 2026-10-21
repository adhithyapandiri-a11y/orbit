package com.orbit.app.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.orbit.app.ui.AiAssistantScreen
import com.orbit.app.ui.NotesScreen
import com.orbit.app.ui.OnboardingScreen
import com.orbit.app.ui.SettingsScreen
import com.orbit.feature.agenda.AgendaScreen
import com.orbit.feature.attention.AttentionScreen
import com.orbit.feature.today.TodayScreen
import com.orbit.feature.videoinbox.VideoInboxScreen

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun OrbitAppShell(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (currentDestination?.route != Screen.Onboarding.route) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                Screen.bottomNavItems.forEach { screen ->
                    val isSelected = currentDestination?.route == screen.route
                    NavigationBarItem(
                        modifier = Modifier.semantics {
                            contentDescription = screen.title
                        },
                        selected = isSelected,
                        onClick = {
                            if (!isSelected) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title
                            )
                        },
                        label = {
                            Text(text = screen.title)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Today.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Today.route) {
                TodayScreen(
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToAgenda = { navController.navigate(Screen.Agenda.route) },
                    onNavigateToVideos = { navController.navigate(Screen.VideoInbox.route) },
                    onNavigateToAttention = { navController.navigate(Screen.Attention.route) },
                    onNavigateToNotes = { navController.navigate(Screen.Notes.route) },
                    onNavigateToAi = { navController.navigate(Screen.AiAssistant.route) }
                )
            }
            composable(Screen.Agenda.route) {
                AgendaScreen(
                    onAddTask = { navController.navigate(Screen.Today.route) }
                )
            }
            composable(Screen.VideoInbox.route) {
                VideoInboxScreen()
            }
            composable(Screen.Attention.route) {
                AttentionScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToOnboarding = { navController.navigate(Screen.Onboarding.route) }
                )
            }
            composable(Screen.Notes.route) {
                NotesScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.AiAssistant.route) {
                AiAssistantScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onFinished = {
                        navController.navigate(Screen.Today.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
