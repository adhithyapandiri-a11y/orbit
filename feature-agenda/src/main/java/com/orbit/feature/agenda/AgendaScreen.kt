package com.orbit.feature.agenda

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.core.ui.component.OrbitButton
import com.orbit.core.ui.component.OrbitCard
import com.orbit.core.ui.component.OrbitEmptyState
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    modifier: Modifier = Modifier,
    viewModel: AgendaViewModel? = null,
    onAddTask: () -> Unit = {}
) {
    val context = LocalContext.current
    val effectiveViewModel: AgendaViewModel? = viewModel ?: if (isHiltAvailable(context)) {
        hiltViewModel<AgendaViewModel>()
    } else {
        null
    }

    if (effectiveViewModel == null) {
        Scaffold(
            topBar = { OrbitTopAppBar(title = "Agenda") },
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
    } else {
        AgendaScreenContent(
            modifier = modifier,
            viewModel = effectiveViewModel,
            onAddTask = onAddTask
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgendaScreenContent(
    modifier: Modifier = Modifier,
    viewModel: AgendaViewModel,
    onAddTask: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.setCalendarPermission(isGranted)
    }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.setCalendarPermission(hasPermission)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = OrbitBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTask,
                containerColor = OrbitOrange,
                contentColor = OrbitBackground,
                shape = CircleShape,
                modifier = Modifier.semantics { contentDescription = "Add Item" }
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
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

            // 1. Month Header with Navigation
            val monthYearTitle = state.selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Calendar",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OrbitTextPrimary
                    )
                    Text(
                        text = monthYearTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OrbitTextSecondary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.previousDay() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Day",
                            tint = OrbitTextSecondary
                        )
                    }
                    IconButton(onClick = { viewModel.nextDay() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next Day",
                            tint = OrbitTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Horizontal Date Strip (Screen 6 design)
            val baseDate = state.selectedDate
            val weekDates = (-3..3).map { baseDate.plusDays(it.toLong()) }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                items(weekDates) { date ->
                    val isSelected = date == state.selectedDate
                    val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    val dayOfMonth = date.dayOfMonth.toString()

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { viewModel.selectDate(date) }
                            .padding(vertical = 8.dp, horizontal = 10.dp)
                    ) {
                        Text(
                            text = dayOfWeek,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) OrbitOrange else OrbitTextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) OrbitOrange else OrbitCardDark)
                                .border(
                                    1.dp,
                                    if (isSelected) OrbitOrange else OrbitCardBorder,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayOfMonth,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) OrbitBackground else OrbitTextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Permission Fallback Banner (if permission not granted)
            if (!state.hasCalendarPermission) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                    shape = RoundedCornerShape(12.dp),
                    color = OrbitCardDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, OrbitCardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = OrbitOrange,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Calendar not connected",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = OrbitTextPrimary
                            )
                            Text(
                                text = "Tap to sync read-only events from your device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = OrbitTextSecondary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 4. Day Timeline View
            if (state.timelineItems.isEmpty()) {
                OrbitEmptyState(
                    title = "No upcoming events",
                    description = "Calendar integration is optional and requests access only when enabled.",
                    icon = Icons.Default.DateRange,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.timelineItems, key = { it.id }) { item ->
                        TimelineCard(
                            item = item,
                            onToggleTask = {
                                if (item.originalTask != null) {
                                    viewModel.toggleTaskCompletion(item.originalTask)
                                }
                            },
                            onOpenMeetingNote = {
                                if (item.originalEvent != null) {
                                    viewModel.openMeetingNote(item.originalEvent)
                                }
                            }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            }
        }

        // Meeting Note Bottom Sheet
        if (state.isMeetingNoteSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeMeetingNote() },
                sheetState = bottomSheetState,
                containerColor = OrbitCardDark
            ) {
                MeetingNoteBottomSheetContent(
                    title = state.meetingNoteTitle,
                    content = state.meetingNoteContent,
                    onContentChange = { viewModel.updateMeetingNoteContent(it) },
                    onSave = { viewModel.saveMeetingNote() },
                    onClose = { viewModel.closeMeetingNote() }
                )
            }
        }
    }
}

@Composable
private fun TimelineCard(
    item: AgendaTimelineItem,
    onToggleTask: () -> Unit,
    onOpenMeetingNote: () -> Unit
) {
    val borderColor = if (item.isLocalTask) OrbitOrange else OrbitCardBorder
    val containerColor = if (item.isLocalTask) OrbitCardElevated else OrbitCardDark

    OrbitCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            if (item.originalEvent != null) {
                onOpenMeetingNote()
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time Pill
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.width(80.dp)
            ) {
                Text(
                    text = item.timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = OrbitOrange,
                    fontWeight = FontWeight.Bold
                )
                if (item.durationMinutes > 0) {
                    Text(
                        text = "${item.durationMinutes}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = OrbitTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.isCompleted) OrbitTextSecondary else OrbitTextPrimary,
                    textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.location.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = OrbitTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = item.location,
                            style = MaterialTheme.typography.labelSmall,
                            color = OrbitTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Action / Note indicator
            if (item.isLocalTask) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (item.isCompleted) OrbitOrange else OrbitCardElevated)
                        .clickable(onClick = onToggleTask),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Completed",
                            tint = OrbitBackground,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                IconButton(onClick = onOpenMeetingNote) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = "Meeting Prep Notes",
                        tint = OrbitOrange
                    )
                }
            }
        }
    }
}

@Composable
private fun MeetingNoteBottomSheetContent(
    title: String,
    content: String,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Meeting Prep & Notes",
            style = MaterialTheme.typography.labelSmall,
            color = OrbitOrange,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = OrbitTextPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        OrbitTextField(
            value = content,
            onValueChange = onContentChange,
            placeholder = "Add key discussion points, attendees, action items...",
            singleLine = false,
            modifier = Modifier.height(140.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        OrbitButton(
            text = "Save Note",
            onClick = onSave,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}
