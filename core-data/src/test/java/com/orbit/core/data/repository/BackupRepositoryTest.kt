package com.orbit.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.orbit.core.data.datastore.OrbitPreferencesDataSource
import com.orbit.core.data.db.OrbitDatabase
import com.orbit.core.domain.model.CalendarEventSnapshot
import com.orbit.core.domain.model.CounterAction
import com.orbit.core.domain.model.CounterEvent
import com.orbit.core.domain.model.DailyReelStatistic
import com.orbit.core.domain.model.MeetingNote
import com.orbit.core.domain.model.Priority
import com.orbit.core.domain.model.ReelCounterSession
import com.orbit.core.domain.model.SessionStatus
import com.orbit.core.domain.model.Task
import com.orbit.core.domain.model.ValidationResult
import com.orbit.core.domain.model.VideoItem
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {

    private lateinit var db: OrbitDatabase
    private lateinit var taskRepository: TaskRepositoryImpl
    private lateinit var calendarRepository: CalendarRepositoryImpl
    private lateinit var meetingNoteRepository: MeetingNoteRepositoryImpl
    private lateinit var videoInboxRepository: VideoInboxRepositoryImpl
    private lateinit var reelCounterRepository: ReelCounterRepositoryImpl
    private lateinit var preferencesRepository: PreferencesRepositoryImpl
    private lateinit var backupRepository: BackupRepositoryImpl
    private lateinit var testDataStoreFile: File

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, OrbitDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        testDataStoreFile = File(context.filesDir, "test_preferences.preferences_pb")
        testDataStoreFile.delete()

        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { testDataStoreFile }
        )
        val dataSource = OrbitPreferencesDataSource(dataStore)

        taskRepository = TaskRepositoryImpl(db.taskDao())
        calendarRepository = CalendarRepositoryImpl(db.calendarDao())
        meetingNoteRepository = MeetingNoteRepositoryImpl(db.meetingNoteDao())
        videoInboxRepository = VideoInboxRepositoryImpl(db.videoInboxDao())
        reelCounterRepository = ReelCounterRepositoryImpl(db, db.reelCounterDao())
        preferencesRepository = PreferencesRepositoryImpl(dataSource)

        backupRepository = BackupRepositoryImpl(
            database = db,
            taskRepository = taskRepository,
            calendarRepository = calendarRepository,
            meetingNoteRepository = meetingNoteRepository,
            videoInboxRepository = videoInboxRepository,
            reelCounterRepository = reelCounterRepository,
            preferencesRepository = preferencesRepository
        )
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
        testDataStoreFile.delete()
    }

    @Test
    fun exportAndRestore_restoresAll7EntitiesFaithfully() = runTest {
        // 1. Task
        val task = Task(
            id = "task-uuid-1",
            title = "Test export task",
            notes = "Secure notes",
            priority = Priority.HIGH,
            isArchived = false,
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L
        )
        taskRepository.upsertTask(task)

        // 2. Calendar Event
        val event = CalendarEventSnapshot(
            id = "cal-uuid-1",
            calendarId = 1L,
            externalEventId = 100L,
            title = "Executive Sync",
            description = "Quarterly roadmap",
            startEpochMs = 2000L,
            endEpochMs = 3000L,
            lastSyncedAtEpochMs = 1000L
        )
        calendarRepository.upsertEvents(listOf(event))

        // 3. Meeting Note linked to Calendar Event
        val note = MeetingNote(
            id = "note-uuid-1",
            eventSnapshotId = "cal-uuid-1",
            title = "Sync Agenda",
            content = "1. Budget, 2. Hiring",
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L
        )
        meetingNoteRepository.upsertNote(note)

        // 4. Video Item
        val video = VideoItem(
            id = "video-uuid-1",
            url = "https://youtube.com/watch?v=sample",
            title = "Sample Tech Talk",
            tags = listOf("android", "kotlin"),
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L
        )
        videoInboxRepository.upsertVideo(video)

        // 5 & 6. Reel Session and Counter Events
        val session = reelCounterRepository.startSession("2026-10-20", 1000L)
        val counterEv1 = reelCounterRepository.recordEvent(session.id, CounterAction.INCREMENT, 1010L)
        val counterEv2 = reelCounterRepository.recordEvent(session.id, CounterAction.INCREMENT, 1020L)
        reelCounterRepository.stopSession(session.id, 1030L)

        // Verify pre-export state
        val preStats = reelCounterRepository.getAllStatisticsDirect()
        assertThat(preStats).hasSize(1)
        assertThat(preStats.first().manualCount).isEqualTo(2)

        // Export to JSON
        val exportResult = backupRepository.exportToJson()
        assertThat(exportResult.isSuccess).isTrue()
        val json = exportResult.getOrThrow()
        assertThat(json).contains("task-uuid-1")
        assertThat(json).contains("Executive Sync")
        assertThat(json).contains("Sync Agenda")
        assertThat(json).contains("Sample Tech Talk")
        assertThat(json).contains(session.id)
        assertThat(json).contains("checksum")

        // Erase all data permanently
        val eraseResult = backupRepository.eraseAllData()
        assertThat(eraseResult.isSuccess).isTrue()
        assertThat(taskRepository.getAllTasksDirect()).isEmpty()
        assertThat(calendarRepository.getAllEventsDirect()).isEmpty()
        assertThat(meetingNoteRepository.getAllNotesDirect()).isEmpty()
        assertThat(videoInboxRepository.getAllVideosDirect()).isEmpty()
        assertThat(reelCounterRepository.getAllSessionsDirect()).isEmpty()
        assertThat(reelCounterRepository.getAllEventsDirect()).isEmpty()

        // Restore from JSON
        val restoreResult = backupRepository.importFromJson(json)
        assertThat(restoreResult.isSuccess).isTrue()

        // Verify restoration across all 7 entities
        val restoredTasks = taskRepository.getAllTasksDirect()
        assertThat(restoredTasks).hasSize(1)
        assertThat(restoredTasks.first().title).isEqualTo("Test export task")

        val restoredCal = calendarRepository.getAllEventsDirect()
        assertThat(restoredCal).hasSize(1)
        assertThat(restoredCal.first().title).isEqualTo("Executive Sync")

        val restoredNotes = meetingNoteRepository.getAllNotesDirect()
        assertThat(restoredNotes).hasSize(1)
        assertThat(restoredNotes.first().content).isEqualTo("1. Budget, 2. Hiring")

        val restoredVideos = videoInboxRepository.getAllVideosDirect()
        assertThat(restoredVideos).hasSize(1)
        assertThat(restoredVideos.first().url).isEqualTo("https://youtube.com/watch?v=sample")

        val restoredSessions = reelCounterRepository.getAllSessionsDirect()
        assertThat(restoredSessions).hasSize(1)
        assertThat(restoredSessions.first().id).isEqualTo(session.id)

        val restoredEvents = reelCounterRepository.getAllEventsDirect()
        assertThat(restoredEvents).hasSize(2)

        val restoredStats = reelCounterRepository.getAllStatisticsDirect()
        assertThat(restoredStats).hasSize(1)
        assertThat(restoredStats.first().manualCount).isEqualTo(2)
    }

    @Test
    fun repeatedImport_isIdempotentAndDoesNotDuplicate() = runTest {
        val task = Task(
            id = "task-idem",
            title = "Idempotent task",
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L
        )
        taskRepository.upsertTask(task)
        val json = backupRepository.exportToJson().getOrThrow()

        // Import twice
        backupRepository.importFromJson(json)
        backupRepository.importFromJson(json)

        val allTasks = taskRepository.getAllTasksDirect()
        assertThat(allTasks).hasSize(1)
        assertThat(allTasks.first().id).isEqualTo("task-idem")
    }

    @Test
    fun importTamperedJson_failsValidationWithChecksumMismatch() = runTest {
        val task = Task(
            id = "task-secret",
            title = "Legitimate task",
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L
        )
        taskRepository.upsertTask(task)
        val validJson = backupRepository.exportToJson().getOrThrow()

        // Tamper with data content
        val tamperedJson = validJson.replace("Legitimate task", "Malicious modified task")

        val validation = backupRepository.validateBackupJson(tamperedJson)
        assertThat(validation).isInstanceOf(ValidationResult.Invalid::class.java)
        val reason = (validation as ValidationResult.Invalid).reason
        assertThat(reason).contains("checksum mismatch")

        val importResult = backupRepository.importFromJson(tamperedJson)
        assertThat(importResult.isError).isTrue()
    }

    @Test
    fun importUnsafeUrl_failsValidation() = runTest {
        val maliciousJson = """
            {
                "schemaVersion": 1,
                "exportedAtEpochMs": 1000,
                "checksum": "fake",
                "videoItems": [
                    {
                        "id": "bad-video",
                        "url": "javascript:alert('pwned')",
                        "title": "Bad Link",
                        "tags": [],
                        "notes": "",
                        "priority": "MEDIUM",
                        "watchIntent": "",
                        "isArchived": false,
                        "createdAtEpochMs": 1000,
                        "updatedAtEpochMs": 1000
                    }
                ]
            }
        """.trimIndent()

        val validation = backupRepository.validateBackupJson(maliciousJson)
        assertThat(validation).isInstanceOf(ValidationResult.Invalid::class.java)
    }

    @Test
    fun importOrphanedCounterEvent_failsValidation() = runTest {
        val json = """
            {
                "schemaVersion": 1,
                "exportedAtEpochMs": 1000,
                "checksum": "placeholder",
                "reelSessions": [],
                "counterEvents": [
                    {
                        "id": "evt-orphan",
                        "sessionId": "non-existent-session-id",
                        "timestamp": 1050,
                        "action": "INCREMENT",
                        "sequenceNumber": 1
                    }
                ]
            }
        """.trimIndent()

        val validation = backupRepository.validateBackupJson(json)
        assertThat(validation).isInstanceOf(ValidationResult.Invalid::class.java)
    }

    @Test
    fun importDuplicateSequenceNumber_failsValidation() = runTest {
        val json = """
            {
                "schemaVersion": 1,
                "exportedAtEpochMs": 1000,
                "checksum": "placeholder",
                "reelSessions": [
                    {
                        "id": "sess-test",
                        "localDate": "2026-10-20",
                        "startedAt": 1000,
                        "status": "STOPPED",
                        "createdAt": 1000,
                        "updatedAt": 1000
                    }
                ],
                "counterEvents": [
                    {
                        "id": "evt-1",
                        "sessionId": "sess-test",
                        "timestamp": 1010,
                        "action": "INCREMENT",
                        "sequenceNumber": 1
                    },
                    {
                        "id": "evt-2",
                        "sessionId": "sess-test",
                        "timestamp": 1020,
                        "action": "INCREMENT",
                        "sequenceNumber": 1
                    }
                ]
            }
        """.trimIndent()

        val validation = backupRepository.validateBackupJson(json)
        assertThat(validation).isInstanceOf(ValidationResult.Invalid::class.java)
    }
}
