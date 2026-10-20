package com.orbit.core.data.db

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.orbit.core.data.db.dao.ReelCounterDao
import com.orbit.core.data.db.entity.CounterEventEntity
import com.orbit.core.data.db.entity.ReelCounterSessionEntity
import com.orbit.core.domain.model.CounterAction
import com.orbit.core.domain.model.SessionStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class ReelCounterDaoTest {

    private lateinit var db: OrbitDatabase
    private lateinit var dao: ReelCounterDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, OrbitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.reelCounterDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun appendOnlyLog_enforcesMonotonicSequenceNumbers() = runTest {
        val session = ReelCounterSessionEntity(
            id = "sess-1",
            localDate = "2026-10-20",
            startedAtEpochMs = 1000L,
            status = SessionStatus.ACTIVE,
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L
        )
        dao.insertSession(session)

        val event1 = CounterEventEntity(
            id = "evt-1",
            sessionId = "sess-1",
            timestampEpochMs = 1010L,
            action = CounterAction.INCREMENT,
            sequenceNumber = 1L
        )
        val event2 = CounterEventEntity(
            id = "evt-2",
            sessionId = "sess-1",
            timestampEpochMs = 1020L,
            action = CounterAction.INCREMENT,
            sequenceNumber = 2L
        )

        dao.insertEvent(event1)
        dao.insertEvent(event2)

        val maxSeq = dao.getMaxSequenceNumber("sess-1")
        assertThat(maxSeq).isEqualTo(2L)

        val events = dao.getEventsForSession("sess-1").first()
        assertThat(events).hasSize(2)
        assertThat(events[0].sequenceNumber).isEqualTo(1L)
        assertThat(events[1].sequenceNumber).isEqualTo(2L)
    }

    @Test(expected = SQLiteConstraintException::class)
    fun duplicateSequenceNumber_abortsWithConstraintException() = runTest {
        val session = ReelCounterSessionEntity(
            id = "sess-2",
            localDate = "2026-10-20",
            startedAtEpochMs = 1000L,
            status = SessionStatus.ACTIVE,
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L
        )
        dao.insertSession(session)

        val event1 = CounterEventEntity(
            id = "evt-a",
            sessionId = "sess-2",
            timestampEpochMs = 1010L,
            action = CounterAction.INCREMENT,
            sequenceNumber = 1L
        )
        val duplicateSeqEvent = CounterEventEntity(
            id = "evt-b",
            sessionId = "sess-2",
            timestampEpochMs = 1020L,
            action = CounterAction.INCREMENT,
            sequenceNumber = 1L // Duplicate seq!
        )

        dao.insertEvent(event1)
        dao.insertEvent(duplicateSeqEvent) // Should throw SQLiteConstraintException
    }

    @Test
    fun cascadeDelete_deletesEventsWhenSessionDeleted() = runTest {
        val session = ReelCounterSessionEntity(
            id = "sess-3",
            localDate = "2026-10-20",
            startedAtEpochMs = 1000L,
            status = SessionStatus.ACTIVE,
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L
        )
        dao.insertSession(session)

        val event = CounterEventEntity(
            id = "evt-c",
            sessionId = "sess-3",
            timestampEpochMs = 1010L,
            action = CounterAction.INCREMENT,
            sequenceNumber = 1L
        )
        dao.insertEvent(event)

        assertThat(dao.getEventsForSessionDirect("sess-3")).hasSize(1)

        dao.deleteAllSessions()
        assertThat(dao.getEventsForSessionDirect("sess-3")).isEmpty()
    }
}
