package com.orbit.feature.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.core.domain.model.ExtractedTaskDraft
import com.orbit.core.domain.model.Priority
import com.orbit.core.domain.model.Task
import com.orbit.core.domain.usecase.TaskFilter
import com.orbit.core.ui.component.OrbitButton
import com.orbit.core.ui.component.OrbitCard
import com.orbit.core.ui.component.OrbitEmptyState
import com.orbit.core.ui.component.OrbitErrorBanner
import com.orbit.core.ui.component.OrbitOutlinedButton
import com.orbit.core.ui.component.OrbitTopAppBar
import com.orbit.core.ui.theme.LocalOrbitSpacing
import com.orbit.core.ui.theme.OrbitAmber
import com.orbit.core.ui.theme.OrbitBlue
import com.orbit.core.ui.theme.OrbitRed
import com.orbit.core.ui.theme.OrbitTeal
import com.orbit.core.ui.util.isHiltAvailable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel? = null,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAgenda: () -> Unit = {},
    onNavigateToVideos: () -> Unit = {},
    onNavigateToAttention: () -> Unit = {},
    onNavigateToNotes: () -> Unit = {},
    onNavigateToAi: () -> Unit = {}
) {
    val context = LocalContext.current
    val effectiveViewModel: TodayViewModel? = viewModel ?: if (isHiltAvailable(context)) {
        hiltViewModel<TodayViewModel>()
    } else {
        null
    }

    if (effectiveViewModel == null) {
        val todayFormatted = remember {
            LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
        }
        Scaffold(
            topBar = {
                OrbitTopAppBar(
                    title = "Today — $todayFormatted",
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Open Settings"
                            )
                        }
                    }
                )
            },
            modifier = modifier
        ) { innerPadding ->
            OrbitEmptyState(
                title = "All clear for today",
                description = "Tasks you add will appear here.",
                icon = Icons.Default.CheckCircle,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    } else {
        TodayScreenContent(
            modifier = modifier,
            viewModel = effectiveViewModel,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToAgenda = onNavigateToAgenda,
            onNavigateToVideos = onNavigateToVideos,
            onNavigateToAttention = onNavigateToAttention,
            onNavigateToNotes = onNavigateToNotes,
            onNavigateToAi = onNavigateToAi
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayScreenContent(
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAgenda: () -> Unit = {},
    onNavigateToVideos: () -> Unit = {},
    onNavigateToAttention: () -> Unit = {},
    onNavigateToNotes: () -> Unit = {},
    onNavigateToAi: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LocalOrbitSpacing.current

    val todayFormatted = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
    }

    Scaffold(
        topBar = {
            OrbitTopAppBar(
                title = "Today — $todayFormatted",
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Open Settings"
                        )
                    }
                }
            )
        },
        bottomBar = {
            // Fast Quick Capture Bar
            QuickCaptureBar(
                text = uiState.quickCaptureText,
                onTextChange = { viewModel.updateQuickCaptureText(it) },
                onSubmit = { viewModel.quickCaptureSubmit() },
                onAiExtract = { viewModel.startAiReviewExtraction() },
                isAiEnabled = uiState.isAiExtractionEnabled,
                isBusy = uiState.isBusy
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Status and Error Banners
            if (uiState.errorMessage != null) {
                OrbitErrorBanner(
                    errorMessage = uiState.errorMessage ?: "",
                    onDismiss = { viewModel.clearStatus() },
                    modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small)
                )
            }

            // Filter Chips
            FilterChipsRow(
                currentFilter = uiState.filter,
                onFilterSelected = { viewModel.setFilter(it) },
                modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small)
            )

            // Task List
            if (uiState.tasks.isEmpty()) {
                val emptyDesc = when (uiState.filter) {
                    TaskFilter.TODAY -> "No active tasks for today. Capture a task below."
                    TaskFilter.UPCOMING -> "No upcoming scheduled tasks."
                    TaskFilter.COMPLETED -> "No completed tasks yet."
                    TaskFilter.ALL -> "No tasks found. Fast capture one below!"
                }
                OrbitEmptyState(
                    title = if (uiState.filter == TaskFilter.TODAY) "All clear for today" else "All clear",
                    description = emptyDesc,
                    icon = Icons.Default.CheckCircleOutline,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    items(uiState.tasks, key = { it.id }) { task ->
                        TaskItemCard(
                            task = task,
                            onToggleCompletion = { viewModel.toggleTaskCompletion(task.id) },
                            onEdit = { viewModel.selectTaskForEdit(task) },
                            onDelete = { viewModel.deleteTask(task.id) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(spacing.medium))
                    }
                }
            }
        }
    }

    // AI Task Extraction Review Screen (Always shown before saving AI-extracted tasks)
    if (uiState.reviewDraft != null) {
        val draft = uiState.reviewDraft!!
        AiReviewDialog(
            draft = draft,
            onConfirm = { title, notes, dueDate, dueTime, priority, reminder ->
                viewModel.confirmReviewDraft(title, notes, dueDate, dueTime, priority, reminder)
            },
            onDismiss = { viewModel.dismissReview() }
        )
    }

    // Edit Task Dialog
    if (uiState.editingTask != null) {
        val task = uiState.editingTask!!
        EditTaskDialog(
            task = task,
            onSave = { title, notes, dueDate, dueTime, priority, reminder ->
                viewModel.saveEditedTask(title, notes, dueDate, dueTime, priority, reminder)
            },
            onDismiss = { viewModel.dismissEdit() }
        )
    }
}

@Composable
private fun FilterChipsRow(
    currentFilter: TaskFilter,
    onFilterSelected: (TaskFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalOrbitSpacing.current
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        items(TaskFilter.entries) { filter ->
            val label = when (filter) {
                TaskFilter.TODAY -> "Today"
                TaskFilter.UPCOMING -> "Upcoming"
                TaskFilter.ALL -> "All"
                TaskFilter.COMPLETED -> "Done"
            }
            FilterChip(
                selected = currentFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

@Composable
private fun TaskItemCard(
    task: Task,
    onToggleCompletion: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val spacing = LocalOrbitSpacing.current

    OrbitCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggleCompletion() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.width(spacing.small))

            // Task Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (task.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = task.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(spacing.extraSmall))

                // Badges row (Priority, Due Date, Reminder)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PriorityBadge(priority = task.priority)

                    task.dueDateEpochDay?.let { epochDay ->
                        DueDateBadge(epochDay = epochDay, minuteOfDay = task.dueTimeMinuteOfDay)
                    }

                    if (task.reminderAtEpochMs != null) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = "Reminder set",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Actions
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete Task",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PriorityBadge(priority: Priority) {
    val (color, text) = when (priority) {
        Priority.URGENT -> OrbitRed to "Urgent"
        Priority.HIGH -> OrbitAmber to "High"
        Priority.MEDIUM -> OrbitBlue to "Medium"
        Priority.LOW -> Color.Gray to "Low"
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun DueDateBadge(epochDay: Long, minuteOfDay: Int?) {
    val today = LocalDate.now().toEpochDay()
    val date = LocalDate.ofEpochDay(epochDay)
    val text = when {
        epochDay == today -> "Today"
        epochDay == today + 1 -> "Tomorrow"
        epochDay < today -> "Overdue"
        else -> date.format(DateTimeFormatter.ofPattern("MMM d"))
    }
    val timeText = if (minuteOfDay != null) {
        val h = minuteOfDay / 60
        val m = minuteOfDay % 60
        " %02d:%02d".format(h, m)
    } else ""

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = text + timeText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun QuickCaptureBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onAiExtract: () -> Unit,
    isAiEnabled: Boolean,
    isBusy: Boolean
) {
    val spacing = LocalOrbitSpacing.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = spacing.medium, vertical = spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = {
                    Text(
                        "Add task... e.g. Buy groceries tomorrow at 5pm",
                        fontSize = 14.sp
                    )
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() })
            )

            Spacer(modifier = Modifier.width(spacing.small))

            // AI Extract Button (Optional review extraction)
            IconButton(
                onClick = onAiExtract,
                enabled = text.isNotBlank() && !isBusy
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Review Extraction",
                    tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Quick Add Submit Button
            IconButton(
                onClick = onSubmit,
                enabled = text.isNotBlank() && !isBusy
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Quick Add",
                        tint = if (text.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * MANDATORY REVIEW SCREEN FOR AI EXTRACTION
 * Fulfills: "Optional AI-assisted task extraction must always show a review screen before writing data.
 * If AI is unavailable, malformed, or disabled, preserve user text and keep the task flow functional."
 */
@Composable
private fun AiReviewDialog(
    draft: ExtractedTaskDraft,
    onConfirm: (String, String, Long?, Int?, Priority, Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(draft.title) }
    var notes by remember { mutableStateOf(draft.notes) }
    var priority by remember { mutableStateOf(draft.priority) }
    var dueDate by remember { mutableStateOf(draft.dueDateEpochDay) }
    var dueTime by remember { mutableStateOf(draft.dueTimeMinuteOfDay) }

    val spacing = LocalOrbitSpacing.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Review Task Extraction")
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // User input preservation reference
                Text(
                    text = "Original Input:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = spacing.extraSmall)
                ) {
                    Text(
                        text = draft.originalUserInput,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(spacing.small)
                    )
                }

                Spacer(modifier = Modifier.height(spacing.small))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(spacing.small))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(spacing.small))

                // Priority Selector
                Text("Priority:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                    Priority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.name) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            OrbitButton(
                text = "Save Task",
                onClick = {
                    onConfirm(title, notes, dueDate, dueTime, priority, null)
                },
                enabled = title.isNotBlank()
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EditTaskDialog(
    task: Task,
    onSave: (String, String, Long?, Int?, Priority, Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(task.title) }
    var notes by remember { mutableStateOf(task.notes) }
    var priority by remember { mutableStateOf(task.priority) }
    var dueDate by remember { mutableStateOf(task.dueDateEpochDay) }
    var dueTime by remember { mutableStateOf(task.dueTimeMinuteOfDay) }

    val spacing = LocalOrbitSpacing.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Task") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(spacing.small))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                Spacer(modifier = Modifier.height(spacing.small))
                Text("Priority:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                    Priority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.name) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            OrbitButton(
                text = "Save Changes",
                onClick = {
                    onSave(title, notes, dueDate, dueTime, priority, task.reminderAtEpochMs)
                },
                enabled = title.isNotBlank()
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
