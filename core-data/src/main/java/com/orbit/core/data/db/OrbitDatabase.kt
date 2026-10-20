package com.orbit.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.orbit.core.data.db.converter.Converters
import com.orbit.core.data.db.dao.CalendarDao
import com.orbit.core.data.db.dao.MeetingNoteDao
import com.orbit.core.data.db.dao.ReelCounterDao
import com.orbit.core.data.db.dao.TaskDao
import com.orbit.core.data.db.dao.VideoInboxDao
import com.orbit.core.data.db.entity.CalendarEventEntity
import com.orbit.core.data.db.entity.CounterEventEntity
import com.orbit.core.data.db.entity.DailyReelStatisticEntity
import com.orbit.core.data.db.entity.MeetingNoteEntity
import com.orbit.core.data.db.entity.ReelCounterSessionEntity
import com.orbit.core.data.db.entity.TaskEntity
import com.orbit.core.data.db.entity.VideoItemEntity

@Database(
    entities = [
        TaskEntity::class,
        CalendarEventEntity::class,
        MeetingNoteEntity::class,
        VideoItemEntity::class,
        ReelCounterSessionEntity::class,
        CounterEventEntity::class,
        DailyReelStatisticEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class OrbitDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun calendarDao(): CalendarDao
    abstract fun meetingNoteDao(): MeetingNoteDao
    abstract fun videoInboxDao(): VideoInboxDao
    abstract fun reelCounterDao(): ReelCounterDao

    companion object {
        const val DATABASE_NAME = "orbit_database.db"
    }
}
