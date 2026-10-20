package com.orbit.core.domain.usecase

import com.orbit.core.domain.model.OrbitErrorCode
import com.orbit.core.domain.model.OrbitResult
import com.orbit.core.domain.model.ValidationResult
import com.orbit.core.domain.repository.BackupRepository

class ImportDataUseCase(
    private val backupRepository: BackupRepository
) {
    suspend operator fun invoke(json: String): OrbitResult<Unit> {
        val validation = backupRepository.validateBackupJson(json)
        if (validation is ValidationResult.Invalid) {
            return OrbitResult.Error(
                code = OrbitErrorCode.VALIDATION_ERROR,
                message = "Validation failed: ${validation.reason}"
            )
        }
        return backupRepository.importFromJson(json)
    }
}
