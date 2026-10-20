package com.orbit.core.domain.usecase

import com.orbit.core.domain.model.OrbitResult
import com.orbit.core.domain.repository.BackupRepository

class EraseAllDataUseCase(
    private val backupRepository: BackupRepository
) {
    suspend operator fun invoke(): OrbitResult<Unit> {
        return backupRepository.eraseAllData()
    }
}
