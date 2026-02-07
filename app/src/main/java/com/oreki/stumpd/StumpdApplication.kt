package com.oreki.stumpd

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.google.firebase.FirebaseApp
import com.oreki.stumpd.data.local.db.StumpdDb
import com.oreki.stumpd.data.repository.GroupRepository
import com.oreki.stumpd.data.repository.MatchRepository
import com.oreki.stumpd.data.repository.PlayerRepository
import com.oreki.stumpd.data.sync.CompleteSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class for global initialization
 * 
 * Initializes:
 * - Firebase
 * - Room Database
 * - Sync Manager
 * - Notification channel for foreground sync service
 */
class StumpdApplication : Application() {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Database and repositories
    val database by lazy { StumpdDb.get(this) }
    val matchRepository by lazy { MatchRepository(database, this) }
    val playerRepository by lazy { PlayerRepository(database) }
    val groupRepository by lazy { GroupRepository(database) }
    
    // Sync manager
    val syncManager by lazy {
        CompleteSyncManager(
            context = this,
            db = database,
            matchRepository = matchRepository,
            playerRepository = playerRepository,
            groupRepository = groupRepository
        )
    }
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "StumpdApplication starting...")
        
        // Create notification channel for sync foreground service
        createSyncNotificationChannel()
        
        // Initialize Firebase
        try {
            FirebaseApp.initializeApp(this)
            Log.d(TAG, "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase - sync will not work", e)
            Log.e(TAG, "Make sure you have added google-services.json to your app folder")
        }
        
        // Initialize sync manager
        applicationScope.launch {
            try {
                syncManager.initialize()
                Log.d(TAG, "Sync manager initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize sync manager", e)
            }
        }
        
        // Observe sync toast events and show on main thread
        applicationScope.launch {
            syncManager.toastEvents.collect { message ->
                mainHandler.post {
                    Toast.makeText(this@StumpdApplication, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun createSyncNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SYNC_CHANNEL_ID,
                "Sync Progress",
                NotificationManager.IMPORTANCE_LOW // No sound, just shows in notification tray
            ).apply {
                description = "Shows progress while syncing data with the cloud"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            Log.d(TAG, "Sync notification channel created")
        }
    }
    
    companion object {
        private const val TAG = "StumpdApplication"
        const val SYNC_CHANNEL_ID = "stumpd_sync"
        const val SYNC_NOTIFICATION_ID = 1001
    }
}
