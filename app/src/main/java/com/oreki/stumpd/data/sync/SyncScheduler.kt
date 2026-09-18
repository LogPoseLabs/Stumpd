package com.oreki.stumpd.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    fun schedulePeriodicIncrementalSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME_PERIODIC_INCREMENTAL,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancelPeriodicIncrementalSync() {
        workManager.cancelUniqueWork(WORK_NAME_PERIODIC_INCREMENTAL)
    }

    fun enqueueFullSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<FullSyncWorker>()
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniqueWork(
            WORK_NAME_FULL_SYNC,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun enqueueDownloadFromCloud() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<CloudDownloadWorker>()
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniqueWork(
            WORK_NAME_CLOUD_DOWNLOAD,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        const val WORK_NAME_PERIODIC_INCREMENTAL = "stumpd_sync_periodic_incremental"
        const val WORK_NAME_FULL_SYNC = "stumpd_sync_full_upload"
        const val WORK_NAME_CLOUD_DOWNLOAD = "stumpd_sync_cloud_download"
    }
}
