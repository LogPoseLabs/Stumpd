package com.oreki.stumpd.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors

/**
 * User-triggered restore: download all user data from Firestore into the local database.
 */
class CloudDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val syncManager = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncWorkEntryPoint::class.java,
        ).completeSyncManager()
        syncManager.initialize()
        return when (syncManager.downloadAllFromCloud()) {
            is SyncResult.Success,
            is SyncResult.PartialSuccess,
            is SyncResult.NoDataToSync -> Result.success()
            // Quota only resets on Google's schedule, so an immediate retry is wasted work.
            // Leave it to the next scheduled sync instead of burning WorkManager backoff.
            is SyncResult.QuotaExceeded -> Result.success()
            SyncResult.Offline -> Result.retry()
            is SyncResult.Failure -> Result.retry()
        }
    }
}
