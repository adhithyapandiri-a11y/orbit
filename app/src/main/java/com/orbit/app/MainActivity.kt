package com.orbit.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.app.navigation.OrbitAppShell
import com.orbit.app.ui.SettingsViewModel
import com.orbit.core.ui.theme.OrbitTheme
import dagger.hilt.android.AndroidEntryPoint

import androidx.lifecycle.lifecycleScope
import com.orbit.core.domain.model.Priority
import com.orbit.core.domain.model.VideoItem
import com.orbit.core.domain.repository.VideoInboxRepository
import com.orbit.feature.videoinbox.parser.YouTubeUrlParser
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var videoInboxRepository: VideoInboxRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleSharedIntent(intent)

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val state by settingsViewModel.uiState.collectAsStateWithLifecycle()

            val isDark = when (state.preferences.darkThemeConfig) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            OrbitTheme(darkTheme = isDark) {
                OrbitAppShell()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSharedIntent(intent)
    }

    private fun handleSharedIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            val parsed = YouTubeUrlParser.parse(sharedText) ?: return
            val now = System.currentTimeMillis()
            val videoItem = VideoItem(
                id = parsed.videoId,
                url = parsed.canonicalUrl,
                title = parsed.suggestedTitle,
                tags = if (parsed.isShort) listOf("shorts") else emptyList(),
                notes = "Shared to Orbit",
                priority = Priority.MEDIUM,
                estimatedDurationMinutes = if (parsed.isShort) 1 else 15,
                watchIntent = "SAVED",
                isArchived = false,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
            lifecycleScope.launch {
                videoInboxRepository.upsertVideo(videoItem)
            }
        }
    }
}
