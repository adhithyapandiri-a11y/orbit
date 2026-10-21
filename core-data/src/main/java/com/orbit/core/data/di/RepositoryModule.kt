package com.orbit.core.data.di

import com.orbit.core.data.datastore.OrbitPreferencesDataSource
import com.orbit.core.data.db.OrbitDatabase
import com.orbit.core.data.db.dao.CalendarDao
import com.orbit.core.data.db.dao.MeetingNoteDao
import com.orbit.core.data.db.dao.ReelCounterDao
import com.orbit.core.data.db.dao.TaskDao
import com.orbit.core.data.db.dao.VideoInboxDao
import com.orbit.core.data.reminder.TaskReminderSchedulerImpl
import com.orbit.core.data.repository.BackupRepositoryImpl
import com.orbit.core.data.repository.CalendarRepositoryImpl
import com.orbit.core.data.repository.MeetingNoteRepositoryImpl
import com.orbit.core.data.repository.PreferencesRepositoryImpl
import com.orbit.core.data.repository.ReelCounterRepositoryImpl
import com.orbit.core.data.repository.TaskRepositoryImpl
import com.orbit.core.data.repository.VideoInboxRepositoryImpl
import com.orbit.core.domain.model.TaskExtractor
import com.orbit.core.domain.repository.BackupRepository
import com.orbit.core.domain.repository.CalendarRepository
import com.orbit.core.domain.repository.MeetingNoteRepository
import com.orbit.core.domain.repository.PreferencesRepository
import com.orbit.core.domain.repository.ReelCounterRepository
import com.orbit.core.domain.repository.TaskReminderScheduler
import com.orbit.core.domain.repository.TaskRepository
import com.orbit.core.domain.repository.VideoInboxRepository
import com.orbit.core.domain.usecase.CreateTaskUseCase
import com.orbit.core.domain.usecase.DeleteTaskUseCase
import com.orbit.core.domain.usecase.EraseAllDataUseCase
import com.orbit.core.domain.usecase.ExportDataUseCase
import com.orbit.core.domain.usecase.ExtractTaskWithReviewUseCase
import com.orbit.core.domain.usecase.GetPreferencesUseCase
import com.orbit.core.domain.usecase.GetTasksUseCase
import com.orbit.core.domain.usecase.ImportDataUseCase
import com.orbit.core.domain.usecase.ToggleTaskCompletionUseCase
import com.orbit.core.domain.usecase.UpdatePreferencesUseCase
import com.orbit.core.domain.usecase.UpdateTaskUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideTaskReminderScheduler(
        scheduler: TaskReminderSchedulerImpl
    ): TaskReminderScheduler = scheduler

    @Provides
    @Singleton
    fun provideTaskRepository(
        taskDao: TaskDao
    ): TaskRepository = TaskRepositoryImpl(taskDao)

    @Provides
    @Singleton
    fun provideCalendarRepository(
        calendarDao: CalendarDao
    ): CalendarRepository = CalendarRepositoryImpl(calendarDao)

    @Provides
    @Singleton
    fun provideMeetingNoteRepository(
        meetingNoteDao: MeetingNoteDao
    ): MeetingNoteRepository = MeetingNoteRepositoryImpl(meetingNoteDao)

    @Provides
    @Singleton
    fun provideVideoInboxRepository(
        videoInboxDao: VideoInboxDao
    ): VideoInboxRepository = VideoInboxRepositoryImpl(videoInboxDao)

    @Provides
    @Singleton
    fun provideReelCounterRepository(
        database: OrbitDatabase,
        reelCounterDao: ReelCounterDao
    ): ReelCounterRepository = ReelCounterRepositoryImpl(database, reelCounterDao)

    @Provides
    @Singleton
    fun providePreferencesRepository(
        dataSource: OrbitPreferencesDataSource
    ): PreferencesRepository = PreferencesRepositoryImpl(dataSource)

    @Provides
    @Singleton
    fun provideBackupRepository(
        database: OrbitDatabase,
        taskRepository: TaskRepository,
        calendarRepository: CalendarRepository,
        meetingNoteRepository: MeetingNoteRepository,
        videoInboxRepository: VideoInboxRepository,
        reelCounterRepository: ReelCounterRepository,
        preferencesRepository: PreferencesRepository
    ): BackupRepository = BackupRepositoryImpl(
        database = database,
        taskRepository = taskRepository,
        calendarRepository = calendarRepository,
        meetingNoteRepository = meetingNoteRepository,
        videoInboxRepository = videoInboxRepository,
        reelCounterRepository = reelCounterRepository,
        preferencesRepository = preferencesRepository
    )

    @Provides
    fun provideExportDataUseCase(repo: BackupRepository): ExportDataUseCase = ExportDataUseCase(repo)

    @Provides
    fun provideImportDataUseCase(repo: BackupRepository): ImportDataUseCase = ImportDataUseCase(repo)

    @Provides
    fun provideEraseAllDataUseCase(repo: BackupRepository): EraseAllDataUseCase = EraseAllDataUseCase(repo)

    @Provides
    fun provideGetPreferencesUseCase(repo: PreferencesRepository): GetPreferencesUseCase = GetPreferencesUseCase(repo)

    @Provides
    fun provideUpdatePreferencesUseCase(repo: PreferencesRepository): UpdatePreferencesUseCase = UpdatePreferencesUseCase(repo)

    @Provides
    fun provideGetTasksUseCase(repo: TaskRepository): GetTasksUseCase = GetTasksUseCase(repo)

    @Provides
    fun provideCreateTaskUseCase(
        repo: TaskRepository,
        scheduler: TaskReminderScheduler
    ): CreateTaskUseCase = CreateTaskUseCase(repo, scheduler)

    @Provides
    fun provideUpdateTaskUseCase(
        repo: TaskRepository,
        scheduler: TaskReminderScheduler
    ): UpdateTaskUseCase = UpdateTaskUseCase(repo, scheduler)

    @Provides
    fun provideToggleTaskCompletionUseCase(
        repo: TaskRepository,
        scheduler: TaskReminderScheduler
    ): ToggleTaskCompletionUseCase = ToggleTaskCompletionUseCase(repo, scheduler)

    @Provides
    fun provideDeleteTaskUseCase(
        repo: TaskRepository,
        scheduler: TaskReminderScheduler
    ): DeleteTaskUseCase = DeleteTaskUseCase(repo, scheduler)
}
