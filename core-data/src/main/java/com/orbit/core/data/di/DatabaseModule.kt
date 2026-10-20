package com.orbit.core.data.di

import android.content.Context
import androidx.room.Room
import com.orbit.core.data.db.OrbitDatabase
import com.orbit.core.data.db.dao.CalendarDao
import com.orbit.core.data.db.dao.MeetingNoteDao
import com.orbit.core.data.db.dao.ReelCounterDao
import com.orbit.core.data.db.dao.TaskDao
import com.orbit.core.data.db.dao.VideoInboxDao
import com.orbit.core.data.db.migration.DatabaseMigrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideOrbitDatabase(
        @ApplicationContext context: Context
    ): OrbitDatabase {
        return Room.databaseBuilder(
            context,
            OrbitDatabase::class.java,
            OrbitDatabase.DATABASE_NAME
        )
            .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides
    fun provideTaskDao(database: OrbitDatabase): TaskDao = database.taskDao()

    @Provides
    fun provideCalendarDao(database: OrbitDatabase): CalendarDao = database.calendarDao()

    @Provides
    fun provideMeetingNoteDao(database: OrbitDatabase): MeetingNoteDao = database.meetingNoteDao()

    @Provides
    fun provideVideoInboxDao(database: OrbitDatabase): VideoInboxDao = database.videoInboxDao()

    @Provides
    fun provideReelCounterDao(database: OrbitDatabase): ReelCounterDao = database.reelCounterDao()
}
