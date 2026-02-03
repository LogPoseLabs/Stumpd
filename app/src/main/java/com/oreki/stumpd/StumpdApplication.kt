package com.oreki.stumpd

import android.app.Application
import android.util.Log
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
 */
class StumpdApplication : Application() {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
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
    }
    
    companion object {
        private const val TAG = "StumpdApplication"
    }
}
