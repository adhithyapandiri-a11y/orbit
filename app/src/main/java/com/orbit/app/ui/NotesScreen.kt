package com.orbit.app.ui

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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.core.domain.model.MeetingNote
import com.orbit.core.ui.component.OrbitButton
import com.orbit.core.ui.component.OrbitCard
import com.orbit.core.ui.component.OrbitEmptyState
import com.orbit.core.ui.component.OrbitFilterChip
import com.orbit.core.ui.component.OrbitOutlinedButton
import com.orbit.core.ui.component.OrbitTextField
import com.orbit.core.ui.theme.OrbitBackground
import com.orbit.core.ui.theme.OrbitCardBorder
import com.orbit.core.ui.theme.OrbitCardDark
import com.orbit.core.ui.theme.OrbitCardElevated
import com.orbit.core.ui.theme.OrbitOrange
import com.orbit.core.ui.theme.OrbitTextPrimary
import com.orbit.core.ui.theme.OrbitTextSecondary
import java.time.Instant
import java.time.ZoneId
import com.orbit.core.ui.component.OrbitTopAppBar
import com.orbit.core.ui.util.isHiltAvailable
import androidx.compose.ui.platform.LocalContext
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    modifier: Modifier = Modifier,
    viewModel: NotesViewModel? = null,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val effectiveViewModel: NotesViewModel? = viewModel ?: if (isHiltAvailable(context)) {
        hiltViewModel<NotesViewModel>()
    } else {
        null
    }

    if (effectiveViewModel == null) {
        Scaffold(
            topBar = { OrbitTopAppBar(title = "Notes") },
            modifier = modifier
        ) { innerPadding ->
            OrbitEmptyState(
                title = "No notes found",
                description = "Capture thoughts and meeting context safely.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
        return
    }

    val viewModel = effectiveViewModel
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = OrbitBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openNewNoteSheet() },
                containerColor = OrbitOrange,
                contentColor = OrbitBackground,
                shape = CircleShape,
                modifier = Modifier.semantics { contentDescription = "Add Note" }
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Note")
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

            // 1. Header (Screen 8 design)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Notes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OrbitTextPrimary
                    )
                    Text(
                        text = "Meeting prep, ideas, and knowledge",
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
                        imageVector = Icons.Default.Description,
                        contentDescription = "Notes",
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
                placeholder = "Search notes...",
                leadingIcon = Icons.Default.Search
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Category Filter Chips (All, College, Personal, Ideas, Meetings)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(NoteCategory.values()) { category ->
                    val name = category.name.lowercase().replaceFirstChar { it.uppercase() }
                    OrbitFilterChip(
                        selected = state.activeCategory == category,
                        text = name,
                        onClick = { viewModel.setCategory(category) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Notes List
            if (state.filteredNotes.isEmpty()) {
                OrbitEmptyState(
                    title = "No notes found",
                    description = "Capture meeting notes, ideas, or study material locally.",
                    icon = Icons.Default.Description,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.filteredNotes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onClick = { viewModel.openEditNoteSheet(note) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            }
        }

        // Edit / Add Note Sheet
        if (state.isEditSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeEditSheet() },
                sheetState = editSheetState,
                containerColor = OrbitCardDark
            ) {
                    NoteEditBottomSheetContent(
                    isNew = state.selectedNote == null,
                    title = state.inputTitle,
                    content = state.inputContent,
                    category = state.inputCategory,
                    onTitleChange = { viewModel.updateInputTitle(it) },
                    onContentChange = { viewModel.updateInputContent(it) },
                    onCategoryChange = { viewModel.updateInputCategory(it) },
                    onSave = { viewModel.saveNote() },
                    onDelete = { state.selectedNote?.let { viewModel.deleteNote(it.id) } },
                    onCancel = { viewModel.closeEditSheet() }
                )
            }
        }
    }
}

@Composable
private fun NoteCard(
    note: MeetingNote,
    onClick: () -> Unit,
    onTogglePin: () -> Unit = {}
) {
    val isPinned = note.content.contains("[Pinned]")
    val displayContent = note.content
        .replace("\\[(College|Personal|Ideas|Meetings|Pinned)\\]\\s*".toRegex(RegexOption.IGNORE_CASE), "")
        .trim()

    val dateText = try {
        val instant = Instant.ofEpochMilli(note.updatedAtEpochMs)
        val zonedDateTime = instant.atZone(ZoneId.systemDefault())
        zonedDateTime.format(DateTimeFormatter.ofPattern("MMM dd, yyyy · HH:mm"))
    } catch (_: Exception) {
        "Recent"
    }

    OrbitCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OrbitTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isPinned) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (isPinned) "Unpin Note" else "Pin Note",
                        tint = OrbitOrange,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (displayContent.isNotBlank()) {
                Text(
                    text = displayContent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OrbitTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.labelSmall,
                    color = OrbitOrange
                )
                if (isPinned) {
                    Text(
                        text = "PINNED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = OrbitOrange
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteEditBottomSheetContent(
    isNew: Boolean,
    title: String,
    content: String,
    category: String,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isNew) "New Note" else "Edit Note",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = OrbitTextPrimary
            )
            if (!isNew) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Note",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Category selection chips
        val categories = listOf("General", "Personal", "College", "Ideas", "Meetings")
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { cat ->
                OrbitFilterChip(
                    selected = category.equals(cat, ignoreCase = true),
                    text = cat,
                    onClick = { onCategoryChange(cat) }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        OrbitTextField(
            value = title,
            onValueChange = onTitleChange,
            placeholder = "Note title..."
        )

        Spacer(modifier = Modifier.height(12.dp))

        OrbitTextField(
            value = content,
            onValueChange = onContentChange,
            placeholder = "Write your note here...",
            singleLine = false,
            modifier = Modifier.height(160.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        OrbitButton(
            text = "Save Note",
            onClick = onSave,
            enabled = title.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}
