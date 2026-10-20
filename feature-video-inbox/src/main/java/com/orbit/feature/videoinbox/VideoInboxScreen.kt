package com.orbit.feature.videoinbox

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.orbit.core.ui.component.OrbitEmptyState
import com.orbit.core.ui.component.OrbitTopAppBar

@Composable
fun VideoInboxScreen(
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            OrbitTopAppBar(title = "Video Inbox")
        },
        modifier = modifier
    ) { innerPadding ->
        OrbitEmptyState(
            title = "Video queue is empty",
            description = "Share links from your browser or apps. No algorithmic feed or autoplay.",
            icon = Icons.Default.PlayCircle,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}
