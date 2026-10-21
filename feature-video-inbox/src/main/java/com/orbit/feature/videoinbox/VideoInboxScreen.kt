package com.orbit.feature.videoinbox

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.core.domain.model.VideoItem
import com.orbit.core.ui.component.OrbitButton
import com.orbit.core.ui.component.OrbitCard
import com.orbit.core.ui.component.OrbitEmptyState
import com.orbit.core.ui.component.OrbitFilterChip
import com.orbit.core.ui.component.OrbitOutlinedButton
import com.orbit.core.ui.component.OrbitTextField
import com.orbit.core.ui.component.OrbitTopAppBar
import com.orbit.core.ui.theme.OrbitBackground
import com.orbit.core.ui.theme.OrbitCardBorder
import com.orbit.core.ui.theme.OrbitCardDark
import com.orbit.core.ui.theme.OrbitCardElevated
import com.orbit.core.ui.theme.OrbitOrange
import com.orbit.core.ui.theme.OrbitTextPrimary
import com.orbit.core.ui.theme.OrbitTextSecondary
import com.orbit.core.ui.util.isHiltAvailable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoInboxScreen(
    modifier: Modifier = Modifier,
    viewModel: VideoInboxViewModel? = null
) {
    val context = LocalContext.current
    val effectiveViewModel: VideoInboxViewModel? = viewModel ?: if (isHiltAvailable(context)) {
        hiltViewModel<VideoInboxViewModel>()
    } else {
        null
    }

    if (effectiveViewModel == null) {
        Scaffold(
            topBar = { OrbitTopAppBar(title = "Video Inbox") },
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
    } else {
        VideoInboxScreenContent(
            modifier = modifier,
            viewModel = effectiveViewModel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoInboxScreenContent(
    modifier: Modifier = Modifier,
    viewModel: VideoInboxViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val detailsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatus()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = OrbitBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddVideoSheet() },
                containerColor = OrbitOrange,
                contentColor = OrbitBackground,
                shape = CircleShape,
                modifier = Modifier.semantics { contentDescription = "Add Video" }
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Video")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. Header (Screen 9 design)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MyTube",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OrbitTextPrimary
                    )
                    Text(
                        text = "Distraction-free video queue",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OrbitTextSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(OrbitCardDark)
                        .border(1.dp, OrbitCardBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = "MyTube",
                        tint = OrbitOrange,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Search bar
            OrbitTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = "Search saved videos...",
                leadingIcon = Icons.Default.Search
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Filter tabs: Saved, Watch Later, Playlists, Archived
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(VideoInboxTab.values()) { tab ->
                    val tabName = when (tab) {
                        VideoInboxTab.SAVED -> "Saved"
                        VideoInboxTab.WATCH_LATER -> "Watch Later"
                        VideoInboxTab.PLAYLISTS -> "Playlists"
                        VideoInboxTab.ARCHIVED -> "Archived"
                    }
                    OrbitFilterChip(
                        selected = state.activeTab == tab,
                        text = tabName,
                        onClick = { viewModel.setTab(tab) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Video items list
            if (state.filteredVideos.isEmpty()) {
                OrbitEmptyState(
                    title = "Video queue is empty",
                    description = "Share links from your browser or apps. No algorithmic feed or autoplay.",
                    icon = Icons.Default.PlayCircle,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.filteredVideos, key = { it.id }) { video ->
                        VideoItemCard(
                            video = video,
                            onClick = { viewModel.openVideoDetails(video) },
                            onOpenExternal = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(video.url))
                                context.startActivity(intent)
                            },
                            onAddToTask = { viewModel.convertVideoToTask(video) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            }
        }

        // Details Bottom Sheet (Screen 10 design)
        if (state.isDetailsSheetOpen && state.selectedVideo != null) {
            val video = state.selectedVideo!!
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeVideoDetails() },
                sheetState = detailsSheetState,
                containerColor = OrbitCardDark
            ) {
                VideoDetailsBottomSheetContent(
                    video = video,
                    onOpenExternal = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(video.url))
                        context.startActivity(intent)
                    },
                    onAddToTask = { viewModel.convertVideoToTask(video) },
                    onToggleArchive = { viewModel.toggleArchive(video) },
                    onDelete = { viewModel.deleteVideo(video.id) },
                    onClose = { viewModel.closeVideoDetails() }
                )
            }
        }

        // Add Video Bottom Sheet
        if (state.isAddVideoSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeAddVideoSheet() },
                sheetState = addSheetState,
                containerColor = OrbitCardDark
            ) {
                AddVideoBottomSheetContent(
                    url = state.inputUrl,
                    title = state.inputTitle,
                    notes = state.inputNotes,
                    tags = state.inputTags,
                    onUrlChange = { viewModel.updateInputUrl(it) },
                    onTitleChange = { viewModel.updateInputTitle(it) },
                    onNotesChange = { viewModel.updateInputNotes(it) },
                    onTagsChange = { viewModel.updateInputTags(it) },
                    onSave = { viewModel.saveVideo() },
                    onCancel = { viewModel.closeAddVideoSheet() }
                )
            }
        }
    }
}

@Composable
private fun VideoItemCard(
    video: VideoItem,
    onClick: () -> Unit,
    onOpenExternal: () -> Unit,
    onAddToTask: () -> Unit
) {
    OrbitCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Video Thumbnail Box
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(OrbitCardElevated)
                    .border(1.dp, OrbitCardBorder, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = OrbitOrange,
                    modifier = Modifier.size(28.dp)
                )

                // Duration Badge
                val durationText = video.estimatedDurationMinutes?.let { "${it}:00" } ?: "12:34"
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(OrbitBackground.copy(alpha = 0.85f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = durationText,
                        style = MaterialTheme.typography.labelSmall,
                        color = OrbitTextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = OrbitTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (video.notes.isNotBlank()) video.notes else "YouTube",
                    style = MaterialTheme.typography.bodySmall,
                    color = OrbitTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Quick External / Task Action
            IconButton(onClick = onOpenExternal) {
                Icon(
                    imageVector = Icons.Default.OpenInBrowser,
                    contentDescription = "Open in browser",
                    tint = OrbitOrange
                )
            }
        }
    }
}

@Composable
private fun VideoDetailsBottomSheetContent(
    video: VideoItem,
    onOpenExternal: () -> Unit,
    onAddToTask: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Video Preview Hero (Screen 10 design)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(OrbitCardElevated)
                .border(1.dp, OrbitCardBorder, RoundedCornerShape(16.dp))
                .clickable(onClick = onOpenExternal),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = "Play Video",
                tint = OrbitOrange,
                modifier = Modifier.size(56.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = video.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = OrbitTextPrimary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = video.url,
            style = MaterialTheme.typography.bodySmall,
            color = OrbitOrange,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (video.notes.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = video.notes,
                style = MaterialTheme.typography.bodyMedium,
                color = OrbitTextSecondary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action Buttons (Screen 10 design)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OrbitButton(
                text = "Open Externally",
                icon = Icons.Default.OpenInBrowser,
                onClick = onOpenExternal,
                modifier = Modifier.weight(1f)
            )
            OrbitOutlinedButton(
                text = "Add to task",
                onClick = onAddToTask,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OrbitOutlinedButton(
                text = if (video.isArchived) "Unarchive" else "Archive",
                onClick = onToggleArchive,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            OrbitOutlinedButton(
                text = "Delete",
                onClick = onDelete,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AddVideoBottomSheetContent(
    url: String,
    title: String,
    notes: String,
    tags: String,
    onUrlChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Save Video to MyTube",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = OrbitTextPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        OrbitTextField(
            value = url,
            onValueChange = onUrlChange,
            placeholder = "Paste YouTube or video link..."
        )

        Spacer(modifier = Modifier.height(12.dp))

        OrbitTextField(
            value = title,
            onValueChange = onTitleChange,
            placeholder = "Video title (optional auto-detect)"
        )

        Spacer(modifier = Modifier.height(12.dp))

        OrbitTextField(
            value = notes,
            onValueChange = onNotesChange,
            placeholder = "Notes or study intention...",
            singleLine = false
        )

        Spacer(modifier = Modifier.height(12.dp))

        OrbitTextField(
            value = tags,
            onValueChange = onTagsChange,
            placeholder = "Tags (e.g. learning, tech, math)..."
        )

        Spacer(modifier = Modifier.height(20.dp))

        OrbitButton(
            text = "Save to MyTube",
            onClick = onSave,
            enabled = url.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}
