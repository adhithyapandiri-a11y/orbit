package com.orbit.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.orbit.core.data.worker.SessionRecoveryWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OrbitApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        enqueueSessionRecovery()
    }

    private fun enqueueSessionRecovery() {
        try {
            val recoveryRequest = OneTimeWorkRequestBuilder<SessionRecoveryWorker>()
                .build()
            WorkManager.getInstance(this).enqueueUniqueWork(
                SessionRecoveryWorker.WORK_NAME,
                ExistingWorkPolicy.KEEP,
                recoveryRequest
            )
        } catch (_: Exception) {
            // Safe fallback if WorkManager is initializing in test mode
        }
    }
}
