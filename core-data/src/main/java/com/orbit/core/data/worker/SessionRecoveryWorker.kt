package com.orbit.core.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.orbit.core.domain.repository.ReelCounterRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SessionRecoveryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val reelCounterRepository: ReelCounterRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            reelCounterRepository.reconcileUnfinishedSessions()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "session_recovery_worker"
    }
}
