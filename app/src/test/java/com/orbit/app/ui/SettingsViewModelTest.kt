package com.orbit.app.ui

import com.orbit.core.domain.model.FeatureFlags
import com.orbit.core.domain.model.OrbitResult
import com.orbit.core.domain.model.OverlayPreference
import com.orbit.core.domain.model.UserPreferences
import com.orbit.core.domain.model.ValidationResult
import com.orbit.core.domain.repository.BackupRepository
import com.orbit.core.domain.repository.PreferencesRepository
import com.orbit.core.domain.usecase.EraseAllDataUseCase
import com.orbit.core.domain.usecase.ExportDataUseCase
import com.orbit.core.domain.usecase.GetPreferencesUseCase
import com.orbit.core.domain.usecase.ImportDataUseCase
import com.orbit.core.domain.usecase.UpdatePreferencesUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
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

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakePreferencesRepository: FakePreferencesRepository
    private lateinit var fakeBackupRepository: FakeBackupRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakePreferencesRepository = FakePreferencesRepository()
        fakeBackupRepository = FakeBackupRepository()

        val getPreferencesUseCase = GetPreferencesUseCase(fakePreferencesRepository)
        val updatePreferencesUseCase = UpdatePreferencesUseCase(fakePreferencesRepository)
        val exportDataUseCase = ExportDataUseCase(fakeBackupRepository)
        val importDataUseCase = ImportDataUseCase(fakeBackupRepository)
        val eraseAllDataUseCase = EraseAllDataUseCase(fakeBackupRepository)

        viewModel = SettingsViewModel(
            getPreferencesUseCase = getPreferencesUseCase,
            updatePreferencesUseCase = updatePreferencesUseCase,
            exportDataUseCase = exportDataUseCase,
            importDataUseCase = importDataUseCase,
            eraseAllDataUseCase = eraseAllDataUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun exportData_updatesUiStateWithExportJson() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.exportData()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isBusy).isFalse()
        assertThat(state.exportJsonPreview).isEqualTo("{\"schemaVersion\":1}")
        assertThat(state.statusMessage).contains("exported")
    }

    @Test
    fun importData_whenValid_updatesStatusMessage() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.importData("{\"schemaVersion\":1}")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.statusMessage).contains("restored")
    }

    @Test
    fun importData_whenInvalid_setsErrorMessage() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        fakeBackupRepository.validationResult = ValidationResult.Invalid("Corrupted JSON")
        viewModel.importData("{corrupted}")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.errorMessage).contains("Corrupted JSON")
    }

    @Test
    fun eraseAllData_invokesEraseAndReportsSuccess() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.eraseAllData()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.statusMessage).contains("permanently deleted")
        assertThat(fakeBackupRepository.erased).isTrue()
    }

    @Test
    fun toggleFeatureFlags_updatesPreferences() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.toggleAiExtraction(true)
        advanceUntilIdle()

        val prefs = fakePreferencesRepository.getUserPreferences().first()
        assertThat(prefs.featureFlags.aiExtractionEnabled).isTrue()
    }

    private class FakePreferencesRepository : PreferencesRepository {
        private val _prefs = MutableStateFlow(UserPreferences())

        override fun getUserPreferences(): Flow<UserPreferences> = _prefs

        override suspend fun updateFeatureFlags(update: (FeatureFlags) -> FeatureFlags) {
            _prefs.update { it.copy(featureFlags = update(it.featureFlags)) }
        }

        override suspend fun updateOverlayPreference(update: (OverlayPreference) -> OverlayPreference) {
            _prefs.update { it.copy(overlayPreference = update(it.overlayPreference)) }
        }

        override suspend fun updateDarkTheme(theme: String) {
            _prefs.update { it.copy(darkThemeConfig = theme) }
        }

        override suspend fun clearPreferences() {
            _prefs.value = UserPreferences()
        }
    }

    private class FakeBackupRepository : BackupRepository {
        var validationResult: ValidationResult = ValidationResult.Valid
        var erased = false

        override suspend fun exportToJson(): OrbitResult<String> {
            return OrbitResult.Success("{\"schemaVersion\":1}")
        }

        override suspend fun validateBackupJson(json: String): ValidationResult {
            return validationResult
        }

        override suspend fun importFromJson(json: String): OrbitResult<Unit> {
            return OrbitResult.Success(Unit)
        }

        override suspend fun eraseAllData(): OrbitResult<Unit> {
            erased = true
            return OrbitResult.Success(Unit)
        }
    }
}
