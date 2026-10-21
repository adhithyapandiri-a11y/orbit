package com.orbit.feature.attention

import com.google.common.truth.Truth.assertThat
import com.orbit.core.domain.model.CounterAction
import com.orbit.core.domain.model.CounterEvent
import com.orbit.core.domain.model.DailyReelStatistic
import com.orbit.core.domain.model.FeatureFlags
import com.orbit.core.domain.model.OverlayPreference
import com.orbit.core.domain.model.ReelCounterSession
import com.orbit.core.domain.model.SessionStatus
import com.orbit.core.domain.model.UserPreferences
import com.orbit.core.domain.repository.PreferencesRepository
import com.orbit.core.domain.repository.ReelCounterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalCoroutinesApi::class)
class AttentionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeStat = MutableStateFlow<DailyReelStatistic?>(null)
    private val fake7Days = MutableStateFlow<List<DailyReelStatistic>>(emptyList())
    private val recordedEvents = mutableListOf<CounterAction>()
    private val fakeSessions = mutableListOf<ReelCounterSession>()
    var startSessionCalls = 0

    private val fakeReelCounterRepository = object : ReelCounterRepository {
        override fun getActiveSession(): Flow<ReelCounterSession?> = flowOf(null)
        override fun getSessionById(sessionId: String): Flow<ReelCounterSession?> = flowOf(null)
        override fun getEventsForSession(sessionId: String): Flow<List<CounterEvent>> = flowOf(emptyList())
        override fun getDailyStatistic(localDate: String): Flow<DailyReelStatistic?> = fakeStat.asStateFlow()
        override fun getDailyStatistics(limit: Int): Flow<List<DailyReelStatistic>> = fake7Days.asStateFlow()
        override suspend fun startSession(localDate: String, timestamp: Long): ReelCounterSession {
            startSessionCalls++
            val session = ReelCounterSession(
                id = "s-new",
                localDate = localDate,
                startedAt = timestamp,
                status = SessionStatus.ACTIVE,
                createdAt = timestamp,
                updatedAt = timestamp
            )
            fakeSessions.add(session)
            return session
        }
        override suspend fun recordEvent(sessionId: String, action: CounterAction, timestamp: Long): CounterEvent {
            recordedEvents.add(action)
            return CounterEvent("e-1", sessionId, timestamp, action, 1)
        }
        override suspend fun pauseSession(sessionId: String, timestamp: Long) {}
        override suspend fun resumeSession(sessionId: String, timestamp: Long) {}
        override suspend fun stopSession(sessionId: String, timestamp: Long) {}
        override suspend fun resetSession(sessionId: String, timestamp: Long) {}
        override suspend fun reconcileUnfinishedSessions() {}
        override suspend fun refreshDailyRollup(localDate: String) {}
        override suspend fun updateInstagramForegroundDuration(localDate: String, foregroundDurationMs: Long) {}
        override suspend fun getAllSessionsDirect(): List<ReelCounterSession> = fakeSessions
        override suspend fun getAllEventsDirect(): List<CounterEvent> = emptyList()
        override suspend fun getAllStatisticsDirect(): List<DailyReelStatistic> = emptyList()
        override suspend fun deleteAllCounterData() {}
    }

    private val fakePrefs = MutableStateFlow(UserPreferences())
    private val fakePreferencesRepository = object : PreferencesRepository {
        override fun getUserPreferences(): Flow<UserPreferences> = fakePrefs.asStateFlow()
        override suspend fun updateFeatureFlags(update: (FeatureFlags) -> FeatureFlags) {
            fakePrefs.value = fakePrefs.value.copy(featureFlags = update(fakePrefs.value.featureFlags))
        }
        override suspend fun updateOverlayPreference(update: (OverlayPreference) -> OverlayPreference) {
            fakePrefs.value = fakePrefs.value.copy(overlayPreference = update(fakePrefs.value.overlayPreference))
        }
        override suspend fun updateDarkTheme(theme: String) {}
        override suspend fun setOnboardingCompleted(completed: Boolean) {
            fakePrefs.value = fakePrefs.value.copy(hasCompletedOnboarding = completed)
        }
        override suspend fun clearPreferences() {}
    }

    private lateinit var viewModel: AttentionViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSessions.clear()
        startSessionCalls = 0
        recordedEvents.clear()
        viewModel = AttentionViewModel(
            reelCounterRepository = fakeReelCounterRepository,
            preferencesRepository = fakePreferencesRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_computesDailyStatsAndWeeklyBars() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val todayIso = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        fakeStat.value = DailyReelStatistic(
            localDate = todayIso,
            manualCount = 42,
            activeDurationMillis = 45 * 60 * 1000L,
            instagramForegroundDurationMillis = 15 * 60 * 1000L
        )
        fake7Days.value = listOf(fakeStat.value!!)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.todayCount).isEqualTo(42)
        assertThat(state.todayDurationMinutes).isEqualTo(60L) // 45 + 15 min = 60m
        assertThat(state.todayDurationFormatted).isEqualTo("1h 0m")
        assertThat(state.weeklyDays).hasSize(7)
        assertThat(state.weeklyDays.last().isToday).isTrue()
    }

    @Test
    fun manualIncrement_recordsIncrementEvent() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.manualIncrement()
        advanceUntilIdle()

        assertThat(recordedEvents).containsExactly(CounterAction.INCREMENT)
        assertThat(startSessionCalls).isEqualTo(1)
    }

    @Test
    fun manualIncrement_reusesExistingActiveSession_preventsDuplicateSessionAndTeardown() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val todayIso = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        fakeSessions.add(
            ReelCounterSession(
                id = "existing-active-session",
                localDate = todayIso,
                startedAt = 1000L,
                status = SessionStatus.ACTIVE,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )

        viewModel.manualIncrement()
        advanceUntilIdle()

        assertThat(startSessionCalls).isEqualTo(0) // Did not start a new session!
        assertThat(recordedEvents).containsExactly(CounterAction.INCREMENT)
    }

    @Test
    fun togglingSettings_updatesPreferences() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.toggleTracking(true)
        viewModel.toggleFloatingCounter(true)
        advanceUntilIdle()

        assertThat(fakePrefs.value.featureFlags.instagramAwarenessEnabled).isTrue()
        assertThat(fakePrefs.value.featureFlags.overlayWidgetEnabled).isTrue()
    }
}
