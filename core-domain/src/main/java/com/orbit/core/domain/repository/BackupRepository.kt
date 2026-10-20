package com.orbit.core.domain.repository

import com.orbit.core.domain.model.OrbitResult
import com.orbit.core.domain.model.ValidationResult

interface BackupRepository {
    suspend fun exportToJson(): OrbitResult<String>
    suspend fun validateBackupJson(json: String): ValidationResult
    suspend fun importFromJson(json: String): OrbitResult<Unit>
    suspend fun eraseAllData(): OrbitResult<Unit>
}
