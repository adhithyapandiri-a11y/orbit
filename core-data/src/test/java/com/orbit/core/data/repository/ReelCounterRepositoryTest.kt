package com.orbit.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.orbit.core.data.db.OrbitDatabase
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
class ReelCounterRepositoryTest {

    private lateinit var db: OrbitDatabase
    private lateinit var repository: ReelCounterRepositoryImpl

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, OrbitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ReelCounterRepositoryImpl(db, db.reelCounterDao())
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }

    @Test
    fun startSession_createsActiveSession() = runTest {
        val session = repository.startSession("2026-10-20", 1000L)
        assertThat(session.status).isEqualTo(SessionStatus.ACTIVE)
        assertThat(session.localDate).isEqualTo("2026-10-20")

        val active = repository.getActiveSession().first()
        assertThat(active?.id).isEqualTo(session.id)
    }

    @Test
    fun recordEvent_incrementsMonotonicallyAndUpdatesDailyRollup() = runTest {
        val session = repository.startSession("2026-10-20", 1000L)

        val ev1 = repository.recordEvent(session.id, CounterAction.INCREMENT, 1010L)
        val ev2 = repository.recordEvent(session.id, CounterAction.INCREMENT, 1020L)
        val ev3 = repository.recordEvent(session.id, CounterAction.INCREMENT, 1030L)

        assertThat(ev1.sequenceNumber).isEqualTo(1L)
        assertThat(ev2.sequenceNumber).isEqualTo(2L)
        assertThat(ev3.sequenceNumber).isEqualTo(3L)

        val stat = repository.getDailyStatistic("2026-10-20").first()
        assertThat(stat).isNotNull()
        assertThat(stat?.manualCount).isEqualTo(3)
    }

    @Test
    fun resetAction_resetsManualCountInEventLogDerivation() = runTest {
        val session = repository.startSession("2026-10-20", 1000L)
        repository.recordEvent(session.id, CounterAction.INCREMENT, 1010L)
        repository.recordEvent(session.id, CounterAction.INCREMENT, 1020L)

        repository.resetSession(session.id, 1030L)

        val statAfterReset = repository.getDailyStatistic("2026-10-20").first()
        assertThat(statAfterReset?.manualCount).isEqualTo(0)

        // Count after reset
        repository.recordEvent(session.id, CounterAction.INCREMENT, 1040L)
        val statFinal = repository.getDailyStatistic("2026-10-20").first()
        assertThat(statFinal?.manualCount).isEqualTo(1)
    }

    @Test
    fun reconcileUnfinishedSessions_closesDanglingSessionsWithoutDoubleCounting() = runTest {
        val session = repository.startSession("2026-10-20", 1000L)
        repository.recordEvent(session.id, CounterAction.INCREMENT, 1050L)

        // Session left in ACTIVE state (e.g. process died)
        assertThat(repository.getActiveSession().first()).isNotNull()

        // Reconcile on startup/reboot
        repository.reconcileUnfinishedSessions()

        // Active session should now be STOPPED
        val activeAfterReconcile = repository.getActiveSession().first()
        assertThat(activeAfterReconcile).isNull()

        val stat = repository.getDailyStatistic("2026-10-20").first()
        assertThat(stat?.manualCount).isEqualTo(1)
    }

    @Test
    fun resetAcrossMultipleSessions_resetsDailyManualCount() = runTest {
        // Session 1: 3 reels watched
        val sess1 = repository.startSession("2026-10-20", 1000L)
        repository.recordEvent(sess1.id, CounterAction.INCREMENT, 1010L)
        repository.recordEvent(sess1.id, CounterAction.INCREMENT, 1020L)
        repository.recordEvent(sess1.id, CounterAction.INCREMENT, 1030L)
        repository.stopSession(sess1.id, 1040L)

        val statAfterSess1 = repository.getDailyStatistic("2026-10-20").first()
        assertThat(statAfterSess1?.manualCount).isEqualTo(3)

        // Session 2: User resets today's count
        val sess2 = repository.startSession("2026-10-20", 2000L)
        repository.resetSession(sess2.id, 2010L)

        val statAfterReset = repository.getDailyStatistic("2026-10-20").first()
        assertThat(statAfterReset?.manualCount).isEqualTo(0)

        // Session 2 continues: user watches 1 reel
        repository.recordEvent(sess2.id, CounterAction.INCREMENT, 2020L)
        val statFinal = repository.getDailyStatistic("2026-10-20").first()
        assertThat(statFinal?.manualCount).isEqualTo(1)
    }

    @Test(expected = IllegalStateException::class)
    fun recordEventOnStoppedSession_throwsIllegalStateException() = runTest {
        val session = repository.startSession("2026-10-20", 1000L)
        repository.stopSession(session.id, 1050L)

        // Attempting to record event on stopped session must fail
        repository.recordEvent(session.id, CounterAction.INCREMENT, 1060L)
    }

    @Test
    fun activeSessionDuration_accumulatesTimeAcrossPausesAndResumes() = runTest {
        val session = repository.startSession("2026-10-20", 1000L)
        // Interval 1: 1000 to 2000 = 1000ms active
        repository.pauseSession(session.id, 2000L)
        // Paused from 2000 to 3000 (1000ms paused)
        repository.resumeSession(session.id, 3000L)
        // Interval 2: 3000 to 5000 = 2000ms active
        repository.stopSession(session.id, 5000L)

        val stat = repository.getDailyStatistic("2026-10-20").first()
        assertThat(stat).isNotNull()
        // Total active: 1000ms + 2000ms = 3000ms
        assertThat(stat?.activeDurationMillis).isEqualTo(3000L)
    }
}
