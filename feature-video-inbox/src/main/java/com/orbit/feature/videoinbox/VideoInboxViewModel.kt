package com.orbit.feature.videoinbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.core.domain.model.Priority
import com.orbit.core.domain.model.Task
import com.orbit.core.domain.model.VideoItem
import com.orbit.core.domain.repository.TaskRepository
import com.orbit.core.domain.repository.VideoInboxRepository
import com.orbit.feature.videoinbox.parser.YouTubeUrlParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

enum class VideoInboxTab {
    SAVED,
    WATCH_LATER,
    PLAYLISTS,
    ARCHIVED
}

data class VideoInboxUiState(
    val videos: List<VideoItem> = emptyList(),
    val filteredVideos: List<VideoItem> = emptyList(),
    val activeTab: VideoInboxTab = VideoInboxTab.SAVED,
    val searchQuery: String = "",
    val selectedVideo: VideoItem? = null,
    val isDetailsSheetOpen: Boolean = false,
    val isAddVideoSheetOpen: Boolean = false,
    val inputUrl: String = "",
    val inputTitle: String = "",
    val inputNotes: String = "",
    val inputTags: String = "",
    val statusMessage: String? = null
)

@HiltViewModel
class VideoInboxViewModel @Inject constructor(
    private val videoInboxRepository: VideoInboxRepository,
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoInboxUiState())

    val uiState: StateFlow<VideoInboxUiState> = combine(
        videoInboxRepository.getVideos(includeArchived = true),
        _uiState
    ) { allVideos, state ->
        val query = state.searchQuery.trim().lowercase()

        val tabFiltered = when (state.activeTab) {
            VideoInboxTab.SAVED -> allVideos.filter { !it.isArchived && !it.watchIntent.equals("WATCH_LATER", ignoreCase = true) }
            VideoInboxTab.WATCH_LATER -> allVideos.filter { !it.isArchived && it.watchIntent.equals("WATCH_LATER", ignoreCase = true) }
            VideoInboxTab.PLAYLISTS -> allVideos.filter { !it.isArchived && (it.tags.any { tag -> tag.contains("playlist", ignoreCase = true) } || it.url.contains("playlist?list=")) }
            VideoInboxTab.ARCHIVED -> allVideos.filter { it.isArchived }
        }

        val searchFiltered = if (query.isNotBlank()) {
            tabFiltered.filter {
                it.title.lowercase().contains(query) ||
                it.notes.lowercase().contains(query) ||
                it.tags.any { tag -> tag.lowercase().contains(query) }
            }
        } else {
            tabFiltered
        }

        state.copy(
            videos = allVideos,
            filteredVideos = searchFiltered
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VideoInboxUiState()
    )

    fun setTab(tab: VideoInboxTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun openVideoDetails(video: VideoItem) {
        _uiState.update { it.copy(selectedVideo = video, isDetailsSheetOpen = true) }
    }

    fun closeVideoDetails() {
        _uiState.update { it.copy(isDetailsSheetOpen = false, selectedVideo = null) }
    }

    fun openAddVideoSheet() {
        _uiState.update {
            it.copy(
                isAddVideoSheetOpen = true,
                inputUrl = "",
                inputTitle = "",
                inputNotes = "",
                inputTags = ""
            )
        }
    }

    fun closeAddVideoSheet() {
        _uiState.update { it.copy(isAddVideoSheetOpen = false) }
    }

    fun updateInputUrl(url: String) {
        val parsed = YouTubeUrlParser.parse(url)
        _uiState.update {
            it.copy(
                inputUrl = url,
                inputTitle = if (it.inputTitle.isBlank() && parsed != null) parsed.suggestedTitle else it.inputTitle
            )
        }
    }

    fun updateInputTitle(title: String) {
        _uiState.update { it.copy(inputTitle = title) }
    }

    fun updateInputNotes(notes: String) {
        _uiState.update { it.copy(inputNotes = notes) }
    }

    fun updateInputTags(tags: String) {
        _uiState.update { it.copy(inputTags = tags) }
    }

    fun saveVideo() {
        val state = _uiState.value
        val rawUrl = state.inputUrl.trim()
        if (rawUrl.isBlank()) return

        val parsed = YouTubeUrlParser.parse(rawUrl)
        val canonicalUrl = parsed?.canonicalUrl ?: rawUrl
        val title = if (state.inputTitle.isNotBlank()) state.inputTitle.trim() else parsed?.suggestedTitle ?: "Saved Video"

        val tagList = state.inputTags
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableList()

        if (parsed?.isPlaylist == true && !tagList.any { it.equals("playlist", ignoreCase = true) }) {
            tagList.add("playlist")
        }

        val now = System.currentTimeMillis()
        val videoItem = VideoItem(
            id = parsed?.videoId ?: UUID.randomUUID().toString(),
            url = canonicalUrl,
            title = title,
            tags = tagList,
            notes = state.inputNotes.trim(),
            priority = Priority.MEDIUM,
            estimatedDurationMinutes = if (parsed?.isShort == true) 1 else 15,
            watchIntent = if (state.activeTab == VideoInboxTab.WATCH_LATER) "WATCH_LATER" else "SAVED",
            isArchived = false,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )

        viewModelScope.launch {
            videoInboxRepository.upsertVideo(videoItem)
            closeAddVideoSheet()
        }
    }

    fun saveSharedUrl(rawText: String) {
        val parsed = YouTubeUrlParser.parse(rawText) ?: return
        val now = System.currentTimeMillis()
        val autoTags = buildList {
            if (parsed.isShort) add("shorts")
            if (parsed.isPlaylist) add("playlist")
        }
        val videoItem = VideoItem(
            id = parsed.videoId,
            url = parsed.canonicalUrl,
            title = parsed.suggestedTitle,
            tags = autoTags,
            notes = "Shared to Orbit",
            priority = Priority.MEDIUM,
            estimatedDurationMinutes = if (parsed.isShort) 1 else 15,
            watchIntent = "SAVED",
            isArchived = false,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )

        viewModelScope.launch {
            videoInboxRepository.upsertVideo(videoItem)
        }
    }

    fun toggleArchive(video: VideoItem) {
        viewModelScope.launch {
            videoInboxRepository.archiveVideo(video.id, !video.isArchived)
        }
    }

    fun deleteVideo(videoId: String) {
        viewModelScope.launch {
            videoInboxRepository.deleteVideo(videoId)
            closeVideoDetails()
        }
    }

    fun convertVideoToTask(video: VideoItem) {
        val now = System.currentTimeMillis()
        val task = Task(
            id = UUID.randomUUID().toString(),
            title = "Watch: ${video.title}",
            notes = "Source: ${video.url}\n${video.notes}".trim(),
            dueDateEpochDay = LocalDate.now().toEpochDay(),
            dueTimeMinuteOfDay = null,
            priority = video.priority,
            isCompleted = false,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )

        viewModelScope.launch {
            taskRepository.upsertTask(task)
            _uiState.update {
                it.copy(statusMessage = "Added to today's tasks!")
            }
        }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
