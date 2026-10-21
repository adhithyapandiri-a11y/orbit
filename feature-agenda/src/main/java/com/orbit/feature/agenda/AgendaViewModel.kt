package com.orbit.feature.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.core.domain.model.CalendarEventSnapshot
import com.orbit.core.domain.model.MeetingNote
import com.orbit.core.domain.model.Task
import com.orbit.core.domain.repository.CalendarRepository
import com.orbit.core.domain.repository.MeetingNoteRepository
import com.orbit.core.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

data class AgendaTimelineItem(
    val id: String,
    val title: String,
    val timeLabel: String,
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val isLocalTask: Boolean,
    val isCompleted: Boolean = false,
    val location: String = "",
    val originalEvent: CalendarEventSnapshot? = null,
    val originalTask: Task? = null
)

data class AgendaUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val timelineItems: List<AgendaTimelineItem> = emptyList(),
    val hasCalendarPermission: Boolean = false,
    val isMeetingNoteSheetOpen: Boolean = false,
    val selectedEventForNote: CalendarEventSnapshot? = null,
    val currentMeetingNote: MeetingNote? = null,
    val meetingNoteTitle: String = "",
    val meetingNoteContent: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AgendaViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val taskRepository: TaskRepository,
    private val meetingNoteRepository: MeetingNoteRepository
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _hasCalendarPermission = MutableStateFlow(false)
    private val _noteSheetState = MutableStateFlow<Triple<Boolean, CalendarEventSnapshot?, MeetingNote?>>(
        Triple(false, null, null)
    )
    private val _noteContent = MutableStateFlow("")

    val uiState: StateFlow<AgendaUiState> = _selectedDate.flatMapLatest { date ->
        val startOfDayMs = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDayMs = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val epochDay = date.toEpochDay()

        combine(
            calendarRepository.getEventsBetween(startOfDayMs, endOfDayMs),
            taskRepository.getTasksForDate(epochDay),
            _hasCalendarPermission,
            _noteSheetState,
            _noteContent
        ) { events, tasks, hasPermission, noteState, noteContent ->
            val timelineItems = mutableListOf<AgendaTimelineItem>()

            // 1. Add calendar events if permission is available
            if (hasPermission) {
                events.filterNot {
                    it.id.startsWith("event_note_") ||
                    it.id.startsWith("note_snapshot_") ||
                    it.description == "Linked Orbit Note"
                }.forEach { event ->
                    val zone = ZoneId.systemDefault()
                    val eventDate = java.time.Instant.ofEpochMilli(event.startEpochMs).atZone(zone)
                    val startMinute = eventDate.hour * 60 + eventDate.minute
                    val duration = ((event.endEpochMs - event.startEpochMs) / (60 * 1000)).toInt().coerceAtLeast(30)
                    val startStr = "%02d:%02d".format(eventDate.hour, eventDate.minute)

                    val endDate = java.time.Instant.ofEpochMilli(event.endEpochMs).atZone(zone)
                    val endStr = "%02d:%02d".format(endDate.hour, endDate.minute)

                    timelineItems.add(
                        AgendaTimelineItem(
                            id = event.id,
                            title = event.title,
                            timeLabel = "$startStr - $endStr",
                            startMinuteOfDay = startMinute,
                            durationMinutes = duration,
                            isLocalTask = false,
                            location = event.location,
                            originalEvent = event
                        )
                    )
                }
            }

            // 2. Add local tasks scheduled for this day
            tasks.forEach { task ->
                val minute = task.dueTimeMinuteOfDay ?: (9 * 60) // default 9am if no specific minute
                val hour = minute / 60
                val min = minute % 60
                val timeLabel = "%02d:%02d".format(hour, min)

                timelineItems.add(
                    AgendaTimelineItem(
                        id = task.id,
                        title = task.title,
                        timeLabel = timeLabel,
                        startMinuteOfDay = minute,
                        durationMinutes = 45,
                        isLocalTask = true,
                        isCompleted = task.isCompleted,
                        location = if (task.notes.isNotBlank()) task.notes else "Orbit Task",
                        originalTask = task
                    )
                )
            }

            timelineItems.sortBy { it.startMinuteOfDay }

            AgendaUiState(
                selectedDate = date,
                timelineItems = timelineItems,
                hasCalendarPermission = hasPermission,
                isMeetingNoteSheetOpen = noteState.first,
                selectedEventForNote = noteState.second,
                currentMeetingNote = noteState.third,
                meetingNoteTitle = noteState.second?.title ?: "",
                meetingNoteContent = noteContent
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AgendaUiState()
    )

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun previousDay() {
        _selectedDate.update { it.minusDays(1) }
    }

    fun nextDay() {
        _selectedDate.update { it.plusDays(1) }
    }

    fun setCalendarPermission(granted: Boolean) {
        _hasCalendarPermission.value = granted
    }

    fun openMeetingNote(event: CalendarEventSnapshot) {
        viewModelScope.launch {
            val existing = meetingNoteRepository.getAllNotesDirect()
                .firstOrNull { it.eventSnapshotId == event.id }

            _noteContent.value = existing?.content ?: ""
            _noteSheetState.value = Triple(true, event, existing)
        }
    }

    fun updateMeetingNoteContent(content: String) {
        _noteContent.value = content
    }

    fun saveMeetingNote() {
        val currentEvent = _noteSheetState.value.second ?: return
        val currentNote = _noteSheetState.value.third
        val content = _noteContent.value
        val now = System.currentTimeMillis()

        viewModelScope.launch {
            val noteToSave = MeetingNote(
                id = currentNote?.id ?: UUID.randomUUID().toString(),
                eventSnapshotId = currentEvent.id,
                title = currentEvent.title,
                content = content,
                createdAtEpochMs = currentNote?.createdAtEpochMs ?: now,
                updatedAtEpochMs = now
            )
            meetingNoteRepository.upsertNote(noteToSave)
            closeMeetingNote()
        }
    }

    fun closeMeetingNote() {
        _noteSheetState.value = Triple(false, null, null)
        _noteContent.value = ""
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            taskRepository.toggleTaskCompletion(
                id = task.id,
                completedAtEpochMs = if (!task.isCompleted) System.currentTimeMillis() else null
            )
        }
    }
}
