package com.oreki.stumpd.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors

/**
 * Periodic / opportunistic incremental sync (upload deltas). Network is enforced by [WorkRequest] constraints.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val syncManager = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncWorkEntryPoint::class.java,
        ).completeSyncManager()
        syncManager.initialize()
        return when (syncManager.syncIncrementalChanges()) {
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
