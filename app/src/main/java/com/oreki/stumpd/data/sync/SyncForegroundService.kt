package com.oreki.stumpd.data.sync

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.oreki.stumpd.R
import com.oreki.stumpd.StumpdApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the app process alive while sync is running.
 * 
 * When the user triggers a sync (upload or download) and then switches to another app,
 * this service shows a notification and prevents Android from killing the process
 * until the sync completes.
 * 
 * The service observes [CompleteSyncManager.syncState] and automatically stops itself
 * when sync transitions out of the [SyncState.Syncing] state.
 */
class SyncForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var hasSyncStarted = false

    companion object {
        private const val TAG = "SyncForegroundService"

        fun start(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        // Show initial notification immediately to satisfy the foreground service contract
        val notification = NotificationCompat.Builder(this, StumpdApplication.SYNC_CHANNEL_ID)
            .setContentTitle("Stumpd")
            .setContentText("Syncing...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .build()

        startForeground(StumpdApplication.SYNC_NOTIFICATION_ID, notification)

        // Observe sync state to update notification and auto-stop
        val app = applicationContext as? StumpdApplication ?: run {
            Log.e(TAG, "Application context is not StumpdApplication")
            stopSelf()
            return
        }

        serviceScope.launch {
            app.syncManager.syncState.collect { state ->
                when (state) {
                    is SyncState.Syncing -> {
                        hasSyncStarted = true
                        updateNotification(state.message)
                    }
                    is SyncState.Success -> {
                        Log.d(TAG, "Sync succeeded, stopping service")
                        stopSelf()
                    }
                    is SyncState.Error -> {
                        Log.d(TAG, "Sync errored, stopping service")
                        stopSelf()
                    }
                    is SyncState.Idle, is SyncState.Offline -> {
                        // Only stop if sync had already started (avoid stopping on initial Idle)
                        if (hasSyncStarted) {
                            Log.d(TAG, "Sync state is $state after sync started, stopping service")
                            stopSelf()
                        }
                    }
                }
            }
        }
    }

    private fun updateNotification(message: String) {
        val notification = NotificationCompat.Builder(this, StumpdApplication.SYNC_CHANNEL_ID)
            .setContentTitle("Stumpd")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .build()

        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(StumpdApplication.SYNC_NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
    }
}
