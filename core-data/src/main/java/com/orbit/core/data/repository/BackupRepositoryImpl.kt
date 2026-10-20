package com.orbit.core.data.repository

import androidx.room.withTransaction
import com.orbit.core.data.db.OrbitDatabase
import com.orbit.core.data.db.entity.CalendarEventEntity
import com.orbit.core.data.db.entity.CounterEventEntity
import com.orbit.core.data.db.entity.DailyReelStatisticEntity
import com.orbit.core.data.db.entity.MeetingNoteEntity
import com.orbit.core.data.db.entity.ReelCounterSessionEntity
import com.orbit.core.data.db.entity.TaskEntity
import com.orbit.core.data.db.entity.VideoItemEntity
import com.orbit.core.domain.model.OrbitBackup
import com.orbit.core.domain.model.OrbitErrorCode
import com.orbit.core.domain.model.OrbitResult
import com.orbit.core.domain.model.ValidationResult
import com.orbit.core.domain.repository.BackupRepository
import com.orbit.core.domain.repository.CalendarRepository
import com.orbit.core.domain.repository.MeetingNoteRepository
import com.orbit.core.domain.repository.PreferencesRepository
import com.orbit.core.domain.repository.ReelCounterRepository
import com.orbit.core.domain.repository.TaskRepository
import com.orbit.core.domain.repository.VideoInboxRepository
import com.orbit.core.security.ChecksumVerifier
import com.orbit.core.security.InputSanitizer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BackupRepositoryImpl(
    private val database: OrbitDatabase,
    private val taskRepository: TaskRepository,
    private val calendarRepository: CalendarRepository,
    private val meetingNoteRepository: MeetingNoteRepository,
    private val videoInboxRepository: VideoInboxRepository,
    private val reelCounterRepository: ReelCounterRepository,
    private val preferencesRepository: PreferencesRepository
) : BackupRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    override suspend fun exportToJson(): OrbitResult<String> {
        return try {
            val now = System.currentTimeMillis()
            val tasks = taskRepository.getAllTasksDirect()
            val calendarEvents = calendarRepository.getAllEventsDirect()
            val meetingNotes = meetingNoteRepository.getAllNotesDirect()
            val videoItems = videoInboxRepository.getAllVideosDirect()
            val reelSessions = reelCounterRepository.getAllSessionsDirect()
            val counterEvents = reelCounterRepository.getAllEventsDirect()
            val dailyStats = reelCounterRepository.getAllStatisticsDirect()

            val rawBackup = OrbitBackup(
                schemaVersion = 1,
                exportedAtEpochMs = now,
                checksum = "",
                tasks = tasks,
                calendarEvents = calendarEvents,
                meetingNotes = meetingNotes,
                videoItems = videoItems,
                reelSessions = reelSessions,
                counterEvents = counterEvents,
                dailyReelStatistics = dailyStats
            )

            // Calculate checksum without the checksum field
            val contentJson = json.encodeToString(rawBackup)
            val checksum = ChecksumVerifier.calculateSha256(contentJson)

            val signedBackup = rawBackup.copy(checksum = checksum)
            val finalJson = json.encodeToString(signedBackup)

            OrbitResult.Success(finalJson)
        } catch (e: Exception) {
            OrbitResult.Error(
                code = OrbitErrorCode.IO_ERROR,
                message = "Failed to export backup safely",
                cause = e
            )
        }
    }

    override suspend fun validateBackupJson(jsonString: String): ValidationResult {
        if (jsonString.isBlank()) {
            return ValidationResult.Invalid("Backup file is empty")
        }
        if (!InputSanitizer.isSafeImportSize(jsonString.toByteArray(Charsets.UTF_8).size.toLong())) {
            return ValidationResult.Invalid("Backup file exceeds maximum permitted size")
        }

        val parsedBackup: OrbitBackup = try {
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            return ValidationResult.Invalid("Invalid backup format or malformed JSON")
        }

        if (parsedBackup.schemaVersion != 1) {
            return ValidationResult.Invalid("Unsupported schema version: ${parsedBackup.schemaVersion}")
        }

        if (parsedBackup.checksum.isBlank()) {
            return ValidationResult.Invalid("Missing integrity checksum")
        }

        // Recompute checksum
        val rawBackup = parsedBackup.copy(checksum = "")
        val contentJson = json.encodeToString(rawBackup)
        if (!ChecksumVerifier.verifySha256(contentJson, parsedBackup.checksum)) {
            return ValidationResult.Invalid("Integrity checksum mismatch: data has been altered or corrupted")
        }

        // Validate URLs in video items
        for (video in parsedBackup.videoItems) {
            if (!InputSanitizer.isValidUrl(video.url)) {
                return ValidationResult.Invalid("Backup contains unsafe or invalid video URL")
            }
        }

        return ValidationResult.Valid
    }

    override suspend fun importFromJson(jsonString: String): OrbitResult<Unit> {
        val validation = validateBackupJson(jsonString)
        if (validation is ValidationResult.Invalid) {
            return OrbitResult.Error(
                code = OrbitErrorCode.VALIDATION_ERROR,
                message = validation.reason
            )
        }

        return try {
            val backup: OrbitBackup = json.decodeFromString(jsonString)

            database.withTransaction {
                // Task import
                if (backup.tasks.isNotEmpty()) {
                    database.taskDao().upsertTasks(backup.tasks.map { TaskEntity.fromDomain(it) })
                }
                // Calendar and notes import
                if (backup.calendarEvents.isNotEmpty()) {
                    database.calendarDao().insertEvents(backup.calendarEvents.map { CalendarEventEntity.fromDomain(it) })
                }
                if (backup.meetingNotes.isNotEmpty()) {
                    database.meetingNoteDao().upsertNotes(backup.meetingNotes.map { MeetingNoteEntity.fromDomain(it) })
                }
                // Video items import
                if (backup.videoItems.isNotEmpty()) {
                    database.videoInboxDao().upsertVideos(backup.videoItems.map { VideoItemEntity.fromDomain(it) })
                }
                // Reel counter import
                if (backup.reelSessions.isNotEmpty()) {
                    database.reelCounterDao().insertSessions(backup.reelSessions.map { ReelCounterSessionEntity.fromDomain(it) })
                }
                if (backup.counterEvents.isNotEmpty()) {
                    database.reelCounterDao().insertEvents(backup.counterEvents.map { CounterEventEntity.fromDomain(it) })
                }
                if (backup.dailyReelStatistics.isNotEmpty()) {
                    database.reelCounterDao().upsertDailyStatistics(backup.dailyReelStatistics.map { DailyReelStatisticEntity.fromDomain(it) })
                }
            }

            OrbitResult.Success(Unit)
        } catch (e: Exception) {
            OrbitResult.Error(
                code = OrbitErrorCode.STORAGE_ERROR,
                message = "Failed to import backup transactionally",
                cause = e
            )
        }
    }

    override suspend fun eraseAllData(): OrbitResult<Unit> {
        return try {
            database.withTransaction {
                database.taskDao().deleteAll()
                database.meetingNoteDao().deleteAll()
                database.calendarDao().deleteAll()
                database.videoInboxDao().deleteAll()
                database.reelCounterDao().deleteAllEvents()
                database.reelCounterDao().deleteAllSessions()
                database.reelCounterDao().deleteAllStatistics()
            }
            preferencesRepository.clearPreferences()
            OrbitResult.Success(Unit)
        } catch (e: Exception) {
            OrbitResult.Error(
                code = OrbitErrorCode.STORAGE_ERROR,
                message = "Failed to erase data safely",
                cause = e
            )
        }
    }
}
