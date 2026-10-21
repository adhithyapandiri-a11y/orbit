package com.orbit.app.ui

import com.google.common.truth.Truth.assertThat
import com.orbit.core.domain.model.FeatureFlags
import com.orbit.core.domain.model.OverlayPreference
import com.orbit.core.domain.model.UserPreferences
import com.orbit.core.domain.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakePreferences = MutableStateFlow(UserPreferences())
    private val fakePreferencesRepository = object : PreferencesRepository {
        var onboardingCompletedSaved = false

        override fun getUserPreferences(): Flow<UserPreferences> = fakePreferences.asStateFlow()
        override suspend fun updateFeatureFlags(update: (FeatureFlags) -> FeatureFlags) {}
        override suspend fun updateOverlayPreference(update: (OverlayPreference) -> OverlayPreference) {}
        override suspend fun updateDarkTheme(theme: String) {}
        override suspend fun setOnboardingCompleted(completed: Boolean) {
            onboardingCompletedSaved = completed
            fakePreferences.value = fakePreferences.value.copy(hasCompletedOnboarding = completed)
        }
        override suspend fun clearPreferences() {}
    }

    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = OnboardingViewModel(fakePreferencesRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_startsAtStepZero() {
        val state = viewModel.uiState.value
        assertThat(state.currentStepIndex).isEqualTo(0)
        assertThat(state.steps).hasSize(3)
        assertThat(state.steps[0].title).contains("Welcome to Orbit")
        assertThat(state.isCompleted).isFalse()
    }

    @Test
    fun nextStep_advancesThroughStepsAndCompletes() = runTest {
        viewModel.nextStep() // To step 1
        assertThat(viewModel.uiState.value.currentStepIndex).isEqualTo(1)

        viewModel.nextStep() // To step 2
        assertThat(viewModel.uiState.value.currentStepIndex).isEqualTo(2)

        viewModel.previousStep() // Back to step 1
        assertThat(viewModel.uiState.value.currentStepIndex).isEqualTo(1)

        viewModel.nextStep() // Back to step 2
        viewModel.nextStep() // Complete
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isCompleted).isTrue()
        assertThat(fakePreferencesRepository.onboardingCompletedSaved).isTrue()
    }

    @Test
    fun completeOnboarding_directlySetsCompleted() = runTest {
        viewModel.completeOnboarding()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isCompleted).isTrue()
        assertThat(fakePreferencesRepository.onboardingCompletedSaved).isTrue()
    }
}
