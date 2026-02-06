package com.oreki.stumpd.data.sync

import android.content.Context
import android.util.Log
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.data.repository.MatchRepository
import com.oreki.stumpd.data.repository.PlayerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Manages bidirectional sync between Room Database and Firebase Firestore
 * 
 * Architecture:
 * - Room is the source of truth for local data (offline-first)
 * - Firestore is used for cloud backup and multi-device sync
 * - Automatic sync on network reconnection
 * - Manual sync on demand
 * 
 * Usage:
 * 1. Call initialize() when app starts
 * 2. Call syncMatch() after saving a match
 * 3. Call syncAll() to sync all pending data
 * 4. Observe syncState for UI updates
 */
class FirestoreSyncManager(
    private val context: Context,
    private val matchRepository: MatchRepository,
    private val playerRepository: PlayerRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val networkMonitor = NetworkMonitor(context)
    
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    
    private val _syncMetadata = MutableStateFlow(
        SyncMetadata(deviceId = getDeviceId())
    )
    val syncMetadata: StateFlow<SyncMetadata> = _syncMetadata.asStateFlow()
    
    private var isInitialized = false
    
    companion object {
        private const val TAG = "FirestoreSyncManager"
        private const val PREFS_NAME = "sync_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val KEY_USER_ID = "user_id"
    }
    
    /**
     * Initialize the sync manager
     * Sets up network monitoring and starts auto-sync if enabled
     */
    fun initialize() {
        if (isInitialized) return
        
        Log.d(TAG, "Initializing FirestoreSyncManager")
        
        // Monitor network state and trigger sync when online
        if (FirebaseConfig.SYNC_ON_NETWORK_RECONNECT) {
            scope.launch {
                networkMonitor.isOnline.collect { isOnline ->
                    if (isOnline && FirebaseConfig.AUTO_SYNC_ENABLED) {
                        Log.d(TAG, "Network reconnected, triggering auto-sync")
                        syncAll()
                    } else if (!isOnline) {
                        _syncState.value = SyncState.Offline
                    }
                }
            }
        }
        
        // Initial sync on app start if enabled
        if (FirebaseConfig.SYNC_ON_APP_START && networkMonitor.isCurrentlyOnline()) {
            scope.launch {
                syncAll()
            }
        }
        
        isInitialized = true
        Log.d(TAG, "FirestoreSyncManager initialized successfully")
    }
    
    /**
     * Sync a specific match to Firestore
     * Called after saving a match locally
     */
    suspend fun syncMatch(matchId: String): SyncResult {
        if (!networkMonitor.isCurrentlyOnline()) {
            Log.w(TAG, "Offline - match will sync when online: $matchId")
            return SyncResult.Offline
        }
        
        return try {
            Log.d(TAG, "Syncing match: $matchId")
            
            // TODO: Implement Firebase upload
            // val match = matchRepository.getMatchById(matchId)
            // firestore.collection("users").document(userId)
            //     .collection("matches").document(matchId)
            //     .set(match.toFirestoreMap())
            
            // For now, just log (Firebase not yet integrated)
            Log.d(TAG, "Match sync queued: $matchId")
            SyncResult.Success(1)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync match: $matchId", e)
            SyncResult.Failure("Failed to sync match", e)
        }
    }
    
    /**
     * Sync all pending data to Firestore
     * Call this manually or it will auto-trigger when network reconnects
     */
    suspend fun syncAll(): SyncResult {
        if (!networkMonitor.isCurrentlyOnline()) {
            Log.w(TAG, "Cannot sync - device is offline")
            _syncState.value = SyncState.Offline
            return SyncResult.Offline
        }
        
        _syncState.value = SyncState.Syncing(0, 0)
        
        return try {
            Log.d(TAG, "Starting full sync...")
            
            // TODO: Implement full sync logic
            // 1. Get all matches from Room that haven't been synced
            // 2. Upload to Firestore
            // 3. Download any new data from Firestore
            // 4. Update Room database
            // 5. Mark items as synced
            
            // For now, just simulate success
            val syncedCount = 0
            
            _syncState.value = SyncState.Success(syncedCount)
            _syncMetadata.value = _syncMetadata.value.copy(
                lastSyncTimestamp = System.currentTimeMillis(),
                lastSyncSuccess = true,
                pendingUploads = 0
            )
            
            saveLastSyncTimestamp()
            
            Log.d(TAG, "Full sync completed successfully - $syncedCount items")
            SyncResult.Success(syncedCount)
            
        } catch (e: Exception) {
            Log.e(TAG, "Full sync failed", e)
            _syncState.value = SyncState.Error("Sync failed: ${e.message}", e)
            SyncResult.Failure("Full sync failed", e)
        }
    }
    
    /**
     * Download all data from Firestore and update local database
     * Useful for setting up a new device or recovering data
     */
    suspend fun downloadAllFromCloud(): SyncResult {
        if (!networkMonitor.isCurrentlyOnline()) {
            return SyncResult.Offline
        }
        
        return try {
            Log.d(TAG, "Downloading all data from cloud...")
            
            // TODO: Implement cloud download
            // 1. Fetch all matches from Firestore
            // 2. Fetch all players from Firestore
            // 3. Fetch all groups from Firestore
            // 4. Save to Room database
            
            Log.d(TAG, "Cloud download completed")
            SyncResult.Success(0)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download from cloud", e)
            SyncResult.Failure("Download failed", e)
        }
    }
    
    /**
     * Enable or disable auto-sync
     */
    fun setAutoSyncEnabled(enabled: Boolean) {
        // TODO: Save to preferences
        Log.d(TAG, "Auto-sync ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Set the user ID for sync
     * Required for Firebase authentication
     */
    fun setUserId(userId: String?) {
        _syncMetadata.value = _syncMetadata.value.copy(userId = userId)
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_USER_ID, userId).apply()
        
        Log.d(TAG, "User ID updated: $userId")
    }
    
    private fun getDeviceId(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        
        return deviceId
    }
    
    private fun saveLastSyncTimestamp() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()
    }
}
