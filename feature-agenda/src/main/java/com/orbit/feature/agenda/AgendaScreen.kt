package com.orbit.feature.agenda

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.orbit.core.ui.component.OrbitEmptyState
import com.orbit.core.ui.component.OrbitTopAppBar

@Composable
fun AgendaScreen(
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            OrbitTopAppBar(title = "Agenda")
        },
        modifier = modifier
    ) { innerPadding ->
        OrbitEmptyState(
            title = "No upcoming events",
            description = "Calendar integration is optional and requests access only when enabled.",
            icon = Icons.Default.DateRange,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}
