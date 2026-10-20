package com.orbit.core.domain.usecase

import com.orbit.core.domain.model.OrbitResult
import com.orbit.core.domain.repository.BackupRepository

class ExportDataUseCase(
    private val backupRepository: BackupRepository
) {
    suspend operator fun invoke(): OrbitResult<String> {
        return backupRepository.exportToJson()
    }
}
