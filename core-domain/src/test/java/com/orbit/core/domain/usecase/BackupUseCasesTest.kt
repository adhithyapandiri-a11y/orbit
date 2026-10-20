package com.orbit.core.domain.usecase

import com.orbit.core.domain.model.OrbitErrorCode
import com.orbit.core.domain.model.OrbitResult
import com.orbit.core.domain.model.ValidationResult
import com.orbit.core.domain.repository.BackupRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class BackupUseCasesTest {

    private lateinit var fakeBackupRepository: FakeBackupRepository
    private lateinit var exportDataUseCase: ExportDataUseCase
    private lateinit var importDataUseCase: ImportDataUseCase
    private lateinit var eraseAllDataUseCase: EraseAllDataUseCase

    @Before
    fun setup() {
        fakeBackupRepository = FakeBackupRepository()
        exportDataUseCase = ExportDataUseCase(fakeBackupRepository)
        importDataUseCase = ImportDataUseCase(fakeBackupRepository)
        eraseAllDataUseCase = EraseAllDataUseCase(fakeBackupRepository)
    }

    @Test
    fun exportDataUseCase_returnsSuccess() = runTest {
        val result = exportDataUseCase()
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo("{\"schemaVersion\":1}")
    }

    @Test
    fun importDataUseCase_whenValidationFails_returnsValidationError() = runTest {
        fakeBackupRepository.validationResult = ValidationResult.Invalid("Bad JSON format")
        val result = importDataUseCase("{corrupted}")
        assertThat(result.isError).isTrue()
        val error = result as OrbitResult.Error
        assertThat(error.code).isEqualTo(OrbitErrorCode.VALIDATION_ERROR)
        assertThat(error.message).contains("Bad JSON format")
    }

    @Test
    fun importDataUseCase_whenValidationSucceeds_importsSuccessfully() = runTest {
        fakeBackupRepository.validationResult = ValidationResult.Valid
        val result = importDataUseCase("{\"schemaVersion\":1}")
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun eraseAllDataUseCase_invokesRepository() = runTest {
        val result = eraseAllDataUseCase()
        assertThat(result.isSuccess).isTrue()
        assertThat(fakeBackupRepository.eraseCalled).isTrue()
    }

    private class FakeBackupRepository : BackupRepository {
        var validationResult: ValidationResult = ValidationResult.Valid
        var eraseCalled = false

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
            eraseCalled = true
            return OrbitResult.Success(Unit)
        }
    }
}
