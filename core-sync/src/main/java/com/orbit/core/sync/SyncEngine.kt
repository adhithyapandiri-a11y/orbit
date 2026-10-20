package com.orbit.core.sync

/**
 * SyncEngine is a stub interface for future opt-in end-to-end encrypted cloud sync.
 * Local-first architecture guarantees that 100% of app functionality operates offline
 * without an account or cloud dependency.
 */
interface SyncEngine {
    val isSyncEnabled: Boolean
        get() = false

    suspend fun syncNow(): Result<Unit> {
        return Result.success(Unit)
    }
}
