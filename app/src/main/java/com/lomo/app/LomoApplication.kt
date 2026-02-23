package com.lomo.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.lomo.app.BuildConfig
import com.lomo.data.worker.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.time.Duration
import javax.inject.Inject

@HiltAndroidApp
class LomoApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        try {
            schedulePeriodicSync()
        } catch (e: Exception) {
            Timber.e(e, "Failed to schedule sync")
        }
    }

    private fun schedulePeriodicSync() {
        val syncRequest =
            PeriodicWorkRequestBuilder<SyncWorker>(Duration.ofHours(1))
                .setConstraints(
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(
                            NetworkType.NOT_REQUIRED,
                        ) // Memos are local
                        .build(),
                ).build()

        WorkManager
            .getInstance(this)
            .enqueueUniquePeriodicWork(
                SyncWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest,
            )
    }
}
