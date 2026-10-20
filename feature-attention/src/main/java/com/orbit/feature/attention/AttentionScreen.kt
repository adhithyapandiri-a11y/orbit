package com.orbit.feature.attention

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.orbit.core.ui.component.OrbitEmptyState
import com.orbit.core.ui.component.OrbitTopAppBar

@Composable
fun AttentionScreen(
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            OrbitTopAppBar(title = "Attention & Privacy")
        },
        modifier = modifier
    ) { innerPadding ->
        OrbitEmptyState(
            title = "Attention Tracking",
            description = "Private app usage and manual Instagram reel tracking. All data stays local.",
            icon = Icons.Default.Visibility,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}
