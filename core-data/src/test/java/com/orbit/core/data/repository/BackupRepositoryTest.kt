package com.orbit.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.orbit.core.data.datastore.OrbitPreferencesDataSource
import com.orbit.core.data.db.OrbitDatabase
import com.orbit.core.domain.model.Priority
import com.orbit.core.domain.model.Task
import com.orbit.core.domain.model.VideoItem
import com.orbit.core.domain.model.ValidationResult
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
    fun exportAndRestore_restoresAllDataFaithfully() = runTest {
        val task = Task(
            id = "task-uuid-1",
            title = "Test export",
            notes = "Secure notes",
            priority = Priority.HIGH,
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L
        )
        val video = VideoItem(
            id = "video-uuid-1",
            url = "https://youtube.com/watch?v=sample",
            title = "Sample Tech Talk",
            tags = listOf("android", "kotlin"),
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L
        )
        taskRepository.upsertTask(task)
        videoInboxRepository.upsertVideo(video)

        // Export to JSON
        val exportResult = backupRepository.exportToJson()
        assertThat(exportResult.isSuccess).isTrue()
        val json = exportResult.getOrThrow()
        assertThat(json).contains("task-uuid-1")
        assertThat(json).contains("Sample Tech Talk")
        assertThat(json).contains("checksum")

        // Erase all data
        val eraseResult = backupRepository.eraseAllData()
        assertThat(eraseResult.isSuccess).isTrue()
        assertThat(taskRepository.getAllTasksDirect()).isEmpty()
        assertThat(videoInboxRepository.getAllVideosDirect()).isEmpty()

        // Restore
        val restoreResult = backupRepository.importFromJson(json)
        assertThat(restoreResult.isSuccess).isTrue()

        // Verify restoration
        val restoredTasks = taskRepository.getAllTasksDirect()
        assertThat(restoredTasks).hasSize(1)
        assertThat(restoredTasks.first().title).isEqualTo("Test export")

        val restoredVideos = videoInboxRepository.getAllVideosDirect()
        assertThat(restoredVideos).hasSize(1)
        assertThat(restoredVideos.first().url).isEqualTo("https://youtube.com/watch?v=sample")
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
        // Create backup JSON containing malicious url
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
}
