package com.orbit.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.orbit.core.ui.component.OrbitEmptyState
import com.orbit.core.ui.component.OrbitTopAppBar

@Composable
fun TodayScreen(
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            OrbitTopAppBar(title = "Today")
        },
        modifier = modifier
    ) { innerPadding ->
        OrbitEmptyState(
            title = "All clear for today",
            description = "Quick task capture and daily timeline will be active here.",
            icon = Icons.Default.CheckCircle,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}
