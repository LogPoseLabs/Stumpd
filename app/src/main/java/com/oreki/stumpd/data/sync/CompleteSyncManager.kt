package com.oreki.stumpd.data.sync

import android.content.Context
import android.util.Log
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.data.local.db.StumpdDb
import com.oreki.stumpd.data.repository.GroupRepository
import com.oreki.stumpd.data.repository.MatchRepository
import com.oreki.stumpd.data.repository.PlayerRepository
import com.oreki.stumpd.data.sync.firebase.FirebaseAuthHelper
import com.oreki.stumpd.data.sync.firebase.FirestoreGroupDao
import com.oreki.stumpd.data.sync.firebase.FirestoreMatchDao
import com.oreki.stumpd.data.sync.firebase.FirestorePlayerDao
import com.oreki.stumpd.data.sync.firebase.FirestoreInProgressMatchDao
import com.oreki.stumpd.data.sync.firebase.FirestoreUserPreferencesDao
import com.oreki.stumpd.data.sync.firebase.FirestoreGroupLastTeamsDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Complete sync manager for ALL data types
 *
 * Handles:
 * - Matches (with stats, partnerships, fall of wickets, deliveries, impacts)
 * - Players
 * - Groups (with members, unavailable players, defaults)
 * - Automatic sync on network reconnection
 * - Manual sync on demand
 *
 * Room Database = Source of truth (offline-first)
 * Firestore = Cloud backup + sync
 */
class CompleteSyncManager(
    private val context: Context,
    private val db: StumpdDb,
    private val matchRepository: MatchRepository,
    private val playerRepository: PlayerRepository,
    private val groupRepository: GroupRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val networkMonitor = NetworkMonitor(context)

    // Firebase components
    private val authHelper = FirebaseAuthHelper()
    private val firestoreMatchDao = FirestoreMatchDao()
    private val firestorePlayerDao = FirestorePlayerDao()
    private val firestoreGroupDao = FirestoreGroupDao()
    private val firestoreInProgressMatchDao = FirestoreInProgressMatchDao()
    private val firestoreUserPreferencesDao = FirestoreUserPreferencesDao()
    private val firestoreGroupLastTeamsDao = FirestoreGroupLastTeamsDao()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    // Toast events for showing sync progress outside of CloudSyncActivity
    private val _toastEvents = Channel<String>(Channel.BUFFERED)
    val toastEvents = _toastEvents.receiveAsFlow()

    private val _syncMetadata = MutableStateFlow(
        SyncMetadata(deviceId = getDeviceId())
    )
    val syncMetadata: StateFlow<SyncMetadata> = _syncMetadata.asStateFlow()

    private var isInitialized = false

    companion object {
        private const val TAG = "CompleteSyncManager"
        private const val PREFS_NAME = "complete_sync_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val KEY_LAST_MATCH_SYNC = "last_match_sync_timestamp"
        private const val KEY_LAST_PLAYER_SYNC = "last_player_sync_timestamp"
        private const val KEY_LAST_GROUP_SYNC = "last_group_sync_timestamp"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
    }

    /**
     * Initialize the sync manager
     * Sets up authentication, network monitoring, and starts auto-sync
     */
    suspend fun initialize() {
        if (isInitialized) return

        Log.d(TAG, "Initializing CompleteSyncManager")

        // Ensure user is authenticated (creates anonymous account if needed)
        val userId = authHelper.ensureSignedIn()
        if (userId != null) {
            _syncMetadata.value = _syncMetadata.value.copy(userId = userId)
            saveUserId(userId)
            Log.d(TAG, "Authenticated with userId: $userId")
        } else {
            Log.w(TAG, "Failed to authenticate - sync will be unavailable")
        }

        // Monitor network state and sync changes incrementally when online
        // Only syncs data modified since last sync to save Firebase quota
        if (isAutoSyncEnabled()) {
            scope.launch {
                networkMonitor.isOnline.collect { isOnline ->
                    if (isOnline && userId != null) {
                        Log.d(TAG, "Network reconnected, syncing incremental changes (timestamp-based)")
                        syncIncrementalChanges()
                    } else if (!isOnline) {
                        _syncState.value = SyncState.Offline
                    }
                }
            }
        }

        // Note: Auto-download disabled to prevent overwriting local stats with empty cloud data
        // Users should manually use "Download All from Cloud" when they want to restore data
        // The incremental sync (below) handles regular syncing without overwriting
        // if (networkMonitor.isCurrentlyOnline() && userId != null) {
        //     scope.launch {
        //         downloadAllFromCloud()
        //     }
        // }

        isInitialized = true
        Log.d(TAG, "CompleteSyncManager initialized successfully")
    }

    /**
     * Sync a specific match immediately after saving
     */
    suspend fun syncMatch(match: MatchHistory): SyncResult {
        val userId = _syncMetadata.value.userId ?: return SyncResult.Offline

        if (!networkMonitor.isCurrentlyOnline()) {
            Log.w(TAG, "Offline - match will sync later: ${match.id}")
            return SyncResult.Offline
        }

        return try {
            Log.d(TAG, "Syncing match: ${match.team1Name} vs ${match.team2Name}")
            firestoreMatchDao.uploadCompleteMatch(userId, match)
            Log.d(TAG, "Match synced successfully: ${match.id}")
            SyncResult.Success(1)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync match: ${match.id}", e)
            SyncResult.Failure("Failed to sync match", e)
        }
    }

    /**
     * Incremental sync - only syncs data that changed since last sync
     * Called on network reconnect to minimize Firebase quota usage
     * Uses timestamps to track what was synced last time
     */
    private suspend fun syncIncrementalChanges() {
        val userId = _syncMetadata.value.userId ?: return

        if (!networkMonitor.isCurrentlyOnline()) {
            return
        }

        val currentTime = System.currentTimeMillis()
        var totalWrites = 0

        try {
            Log.d(TAG, "=== Starting incremental sync (timestamp-based) ===")
            _toastEvents.trySend("Syncing...")

            // 1. Sync active in-progress match (always, for live spectators)
            val inProgressMatch = db.inProgressMatchDao().getLatest()
            if (inProgressMatch != null) {
                Log.d(TAG, "Syncing active match: ${inProgressMatch.matchId}")
                firestoreInProgressMatchDao.uploadInProgressMatch(userId, inProgressMatch)
                totalWrites++
            }

            // 2. Sync completed matches added/modified since last sync (with stats)
            val lastMatchSync = getLastMatchSyncTimestamp()
            val matches = matchRepository.getAllMatchesWithStats()
            val newMatches = matches.filter { it.matchDate > lastMatchSync }

            if (newMatches.isNotEmpty()) {
                Log.d(TAG, "Syncing ${newMatches.size} new/modified matches (since ${lastMatchSync})")
                newMatches.forEach { match ->
                    try {
                        firestoreMatchDao.uploadCompleteMatch(userId, match)
                        totalWrites++
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync match: ${match.id}", e)
                    }
                }
                saveLastMatchSyncTimestamp(currentTime)
            } else {
                Log.d(TAG, "No new matches to sync")
            }

            // 3. Sync players (only if modified since last sync)
            // Since players don't have timestamps, we check if the list changed
            val lastPlayerSync = getLastPlayerSyncTimestamp()
            if (currentTime - lastPlayerSync > 300000) { // 5 minutes threshold
                val players = db.playerDao().list()
                if (players.isNotEmpty()) {
                    Log.d(TAG, "Syncing ${players.size} players (periodic check)")
                    firestorePlayerDao.uploadPlayers(userId, players)
                    totalWrites += players.size
                    saveLastPlayerSyncTimestamp(currentTime)
                }
            }

            // 4. Sync groups (only if modified since last sync)
            val lastGroupSync = getLastGroupSyncTimestamp()
            if (currentTime - lastGroupSync > 300000) { // 5 minutes threshold
                val groups = db.groupDao().getAllGroups()
                if (groups.isNotEmpty()) {
                    Log.d(TAG, "Syncing ${groups.size} groups (periodic check)")
                    val allMembers = db.groupDao().getAllGroupMembers()
                    val allUnavailable = db.groupDao().getAllGroupUnavailablePlayers()

                    groups.forEach { group ->
                        try {
                            val members = allMembers.filter { it.groupId == group.id }
                            val unavailable = allUnavailable.filter { it.groupId == group.id }
                            val defaults = db.groupDao().getDefaults(group.id)

                            firestoreGroupDao.uploadGroup(userId, group, members, unavailable, defaults)
                            totalWrites++
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to sync group: ${group.id}", e)
                        }
                    }
                    saveLastGroupSyncTimestamp(currentTime)
                }
            }

            Log.d(TAG, "✅ Incremental sync complete: $totalWrites writes")
            if (totalWrites > 0) {
                _toastEvents.trySend("Sync complete: $totalWrites items synced")
            } else {
                _toastEvents.trySend("Sync complete: everything up to date")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync incremental changes", e)
            _toastEvents.trySend("Sync failed: ${e.message?.take(50) ?: "unknown error"}")
        }
    }

    // Timestamp tracking helpers
    private fun getLastMatchSyncTimestamp(): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_MATCH_SYNC, 0L)
    }

    private fun saveLastMatchSyncTimestamp(timestamp: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_MATCH_SYNC, timestamp)
            .apply()
    }

    private fun getLastPlayerSyncTimestamp(): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_PLAYER_SYNC, 0L)
    }

    private fun saveLastPlayerSyncTimestamp(timestamp: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_PLAYER_SYNC, timestamp)
            .apply()
    }

    private fun getLastGroupSyncTimestamp(): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_GROUP_SYNC, 0L)
    }

    private fun saveLastGroupSyncTimestamp(timestamp: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_GROUP_SYNC, timestamp)
            .apply()
    }

    /**
     * @deprecated Use syncIncrementalChanges() instead for quota efficiency
     * Only syncs the active in-progress match
     */
    @Deprecated("Use syncIncrementalChanges() instead")
    private suspend fun syncActiveMatchOnly() {
        val userId = _syncMetadata.value.userId ?: return

        if (!networkMonitor.isCurrentlyOnline()) {
            return
        }

        try {
            val inProgressMatch = db.inProgressMatchDao().getLatest()
            if (inProgressMatch != null) {
                Log.d(TAG, "Syncing active match only: ${inProgressMatch.matchId}")
                firestoreInProgressMatchDao.uploadInProgressMatch(userId, inProgressMatch)
                Log.d(TAG, "✅ Active match synced (1 write)")
            } else {
                Log.d(TAG, "No active match to sync")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync active match", e)
        }
    }

    /**
     * Sync all local data to Firestore
     * ⚠️ WARNING: This uploads ALL matches, players, and groups
     * Use sparingly to avoid hitting Firebase quota limits
     * Prefer incremental sync for automatic syncs
     *
     * This also updates all sync timestamps so future incremental
     * syncs won't re-upload this data
     */
    suspend fun syncAll(): SyncResult {
        val userId = _syncMetadata.value.userId ?: return SyncResult.Offline

        if (!networkMonitor.isCurrentlyOnline()) {
            Log.w(TAG, "Cannot sync - device is offline")
            _syncState.value = SyncState.Offline
            return SyncResult.Offline
        }

        _syncState.value = SyncState.Syncing(0, 6, "Preparing to sync...")
        _toastEvents.trySend("Starting full sync...")

        return try {
            Log.d(TAG, "Starting full sync to cloud...")

            var totalSynced = 0
            val errors = mutableListOf<String>()

            // 1. Sync all matches (with stats included for proper subcollection upload)
            Log.d(TAG, "Step 1/6: Syncing matches...")
            _syncState.value = SyncState.Syncing(0, 6, "Uploading matches...", 0, 0)
            try {
                val matches = matchRepository.getAllMatchesWithStats()
                Log.d(TAG, "Syncing ${matches.size} matches...")

                matches.forEachIndexed { index, match ->
                    _syncState.value = SyncState.Syncing(0, 6, "Uploading match ${index + 1}/${matches.size}...", index + 1, matches.size)
                    try {
                        firestoreMatchDao.uploadCompleteMatch(userId, match)
                        totalSynced++
                    } catch (e: Exception) {
                        errors.add("Match ${match.id}: ${e.message}")
                        Log.e(TAG, "Failed to sync match: ${match.id}", e)
                    }
                }

                Log.d(TAG, "Matches synced: ${matches.size}")
            } catch (e: Exception) {
                errors.add("Failed to fetch matches: ${e.message}")
                Log.e(TAG, "Failed to fetch matches", e)
            }
            _syncState.value = SyncState.Syncing(1, 6, "Matches uploaded", 0, 0)

            // 2. Sync all players
            Log.d(TAG, "Step 2/6: Syncing players...")
            _syncState.value = SyncState.Syncing(1, 6, "Uploading players...", 0, 0)
            try {
                val players = db.playerDao().list()
                Log.d(TAG, "Syncing ${players.size} players...")
                _syncState.value = SyncState.Syncing(1, 6, "Uploading ${players.size} players...", 1, 1)

                firestorePlayerDao.uploadPlayers(userId, players)
                totalSynced += players.size

                Log.d(TAG, "Players synced: ${players.size}")
            } catch (e: Exception) {
                errors.add("Failed to sync players: ${e.message}")
                Log.e(TAG, "Failed to sync players", e)
            }
            _syncState.value = SyncState.Syncing(2, 6, "Players uploaded", 0, 0)

            // 3. Sync all groups with their members and settings
            Log.d(TAG, "Step 3/6: Syncing groups...")
            _syncState.value = SyncState.Syncing(2, 6, "Uploading groups...", 0, 0)
            try {
                val groups = db.groupDao().getAllGroups()
                Log.d(TAG, "Syncing ${groups.size} groups...")

                val allMembers = db.groupDao().getAllGroupMembers()
                val allUnavailable = db.groupDao().getAllGroupUnavailablePlayers()

                groups.forEachIndexed { index, group ->
                    _syncState.value = SyncState.Syncing(2, 6, "Uploading group ${index + 1}/${groups.size}...", index + 1, groups.size)
                    try {
                        val members = allMembers.filter { it.groupId == group.id }
                        val unavailable = allUnavailable.filter { it.groupId == group.id }
                        val defaults = db.groupDao().getDefaults(group.id)

                        firestoreGroupDao.uploadGroup(userId, group, members, unavailable, defaults)
                        totalSynced++
                    } catch (e: Exception) {
                        errors.add("Group ${group.name}: ${e.message}")
                        Log.e(TAG, "Failed to sync group: ${group.id}", e)
                    }
                }

                Log.d(TAG, "Groups synced: ${groups.size}")
            } catch (e: Exception) {
                errors.add("Failed to sync groups: ${e.message}")
                Log.e(TAG, "Failed to sync groups", e)
            }
            _syncState.value = SyncState.Syncing(3, 6, "Groups uploaded", 0, 0)

            // 4. Sync in-progress matches (ongoing games)
            Log.d(TAG, "Step 4/6: Syncing in-progress matches...")
            _syncState.value = SyncState.Syncing(3, 6, "Uploading live match data...", 0, 1)
            try {
                val inProgressMatch = db.inProgressMatchDao().getLatest()
                if (inProgressMatch != null) {
                    _syncState.value = SyncState.Syncing(3, 6, "Uploading live match...", 1, 1)
                    Log.d(TAG, "Syncing in-progress match...")
                    firestoreInProgressMatchDao.uploadInProgressMatch(userId, inProgressMatch)
                    totalSynced++
                    Log.d(TAG, "In-progress match synced")
                }
            } catch (e: Exception) {
                errors.add("Failed to sync in-progress match: ${e.message}")
                Log.e(TAG, "Failed to sync in-progress match", e)
            }
            _syncState.value = SyncState.Syncing(4, 6, "Live match data uploaded", 0, 0)

            // 5. Sync user preferences (app settings)
            Log.d(TAG, "Step 5/6: Syncing preferences...")
            _syncState.value = SyncState.Syncing(4, 6, "Uploading preferences...", 0, 0)
            try {
                val preferences = db.userPreferencesDao().getAll()
                if (preferences.isNotEmpty()) {
                    _syncState.value = SyncState.Syncing(4, 6, "Uploading ${preferences.size} preferences...", 1, 1)
                    Log.d(TAG, "Syncing ${preferences.size} user preferences...")
                    firestoreUserPreferencesDao.uploadPreferences(userId, preferences)
                    totalSynced += preferences.size
                    Log.d(TAG, "User preferences synced: ${preferences.size}")
                }
            } catch (e: Exception) {
                errors.add("Failed to sync preferences: ${e.message}")
                Log.e(TAG, "Failed to sync preferences", e)
            }
            _syncState.value = SyncState.Syncing(5, 6, "Preferences uploaded", 0, 0)

            // 6. Sync group last teams configurations
            Log.d(TAG, "Step 6/6: Syncing group last teams...")
            _syncState.value = SyncState.Syncing(5, 6, "Uploading team configurations...", 0, 0)
            try {
                val groupLastTeams = db.groupDao().getAllGroupLastTeams()
                if (groupLastTeams.isNotEmpty()) {
                    _syncState.value = SyncState.Syncing(5, 6, "Uploading ${groupLastTeams.size} team configs...", 1, 1)
                    Log.d(TAG, "Syncing ${groupLastTeams.size} group last teams configs...")
                    firestoreGroupLastTeamsDao.uploadGroupLastTeams(userId, groupLastTeams)
                    totalSynced += groupLastTeams.size
                    Log.d(TAG, "Group last teams synced: ${groupLastTeams.size}")
                }
            } catch (e: Exception) {
                errors.add("Failed to sync group last teams: ${e.message}")
                Log.e(TAG, "Failed to sync group last teams", e)
            }
            _syncState.value = SyncState.Syncing(6, 6, "Finalizing...", 0, 0)

            // Update metadata and timestamps
            val currentTime = System.currentTimeMillis()
            _syncMetadata.value = _syncMetadata.value.copy(
                lastSyncTimestamp = currentTime,
                lastSyncSuccess = errors.isEmpty(),
                pendingUploads = 0
            )
            saveLastSyncTimestamp()

            // Update incremental sync timestamps (all data is now synced)
            saveLastMatchSyncTimestamp(currentTime)
            saveLastPlayerSyncTimestamp(currentTime)
            saveLastGroupSyncTimestamp(currentTime)

            _syncState.value = if (errors.isEmpty()) {
                SyncState.Success(totalSynced)
            } else {
                SyncState.Error("Partial sync - ${errors.size} errors", null)
            }

            Log.d(TAG, "Full sync completed - $totalSynced items synced, ${errors.size} errors")

            if (errors.isEmpty()) {
                _toastEvents.trySend("Sync complete: $totalSynced items synced")
                SyncResult.Success(totalSynced)
            } else {
                _toastEvents.trySend("Sync partially done: $totalSynced synced, ${errors.size} errors")
                SyncResult.PartialSuccess(totalSynced, errors.size, errors)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Full sync failed catastrophically", e)
            _syncState.value = SyncState.Error("Sync failed: ${e.message}", e)
            _toastEvents.trySend("Sync failed: ${e.message?.take(50) ?: "unknown error"}")
            SyncResult.Failure("Full sync failed", e)
        }
    }

    /**
     * Launch syncAll in the manager's internal scope
     * This survives UI navigation - use from Composables/Activities.
     * Starts a foreground service to keep sync alive when the app is backgrounded.
     */
    fun launchSyncAll() {
        SyncForegroundService.start(context)
        scope.launch {
            syncAll()
        }
    }

    /**
     * Launch downloadAllFromCloud in the manager's internal scope
     * This survives UI navigation - use from Composables/Activities.
     * Starts a foreground service to keep sync alive when the app is backgrounded.
     */
    fun launchDownloadAllFromCloud() {
        SyncForegroundService.start(context)
        scope.launch {
            downloadAllFromCloud()
        }
    }

    /**
     * Download all data from Firestore and update local database
     * Use this to restore data on a new device or recover from local data loss
     */
    suspend fun downloadAllFromCloud(): SyncResult {
        val userId = _syncMetadata.value.userId ?: return SyncResult.Offline

        if (!networkMonitor.isCurrentlyOnline()) {
            return SyncResult.Offline
        }

        return try {
            Log.d(TAG, "Downloading all data from cloud...")
            _syncState.value = SyncState.Syncing(0, 6, "Preparing to download...")
            _toastEvents.trySend("Downloading from cloud...")

            var totalDownloaded = 0

            // 1. Download all matches
            Log.d(TAG, "Step 1/6: Downloading matches...")
            _syncState.value = SyncState.Syncing(0, 6, "Downloading matches...", 0, 0)
            val matches = firestoreMatchDao.downloadAllMatches(userId)
            Log.d(TAG, "Step 1/6: Downloaded ${matches.size} matches from cloud")

            matches.forEachIndexed { index, match ->
                _syncState.value = SyncState.Syncing(0, 6, "Saving match ${index + 1}/${matches.size}...", index + 1, matches.size)
                try {
                    matchRepository.saveMatch(match)
                    totalDownloaded++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save match locally: ${match.id}", e)
                }
            }
            _syncState.value = SyncState.Syncing(1, 6, "Matches downloaded", 0, 0)

            // 2. Download all players
            Log.d(TAG, "Step 2/6: Downloading players...")
            _syncState.value = SyncState.Syncing(1, 6, "Downloading players...", 0, 0)
            val players = firestorePlayerDao.downloadAllPlayers(userId)
            Log.d(TAG, "Step 2/6: Downloaded ${players.size} players from cloud")
            _syncState.value = SyncState.Syncing(1, 6, "Saving ${players.size} players...", 1, 1)

            if (players.isNotEmpty()) {
                try {
                    db.playerDao().upsert(players)
                    totalDownloaded += players.size
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save players locally", e)
                }
            }
            _syncState.value = SyncState.Syncing(2, 6, "Players downloaded", 0, 0)

            // 3. Download only groups where this device is a member
            Log.d(TAG, "Step 3/6: Downloading groups...")
            _syncState.value = SyncState.Syncing(2, 6, "Downloading groups...", 0, 0)
            val groupsData = firestoreGroupDao.downloadMyGroups(userId)
            Log.d(TAG, "Step 3/6: Downloaded ${groupsData.size} groups for this device from cloud")

            groupsData.forEachIndexed { index, groupData ->
                _syncState.value = SyncState.Syncing(2, 6, "Saving group ${index + 1}/${groupsData.size}...", index + 1, groupsData.size)
                try {
                    db.groupDao().upsertGroup(groupData.group)

                    // Clear existing members before inserting
                    db.groupDao().clearMembers(groupData.group.id)
                    groupData.members.forEach { member ->
                        db.groupDao().upsertMembers(listOf(member))
                    }

                    groupData.unavailable.forEach { unavailable ->
                        db.groupDao().markPlayerUnavailable(unavailable)
                    }

                    groupData.defaults?.let { defaults ->
                        db.groupDao().upsertDefaults(defaults)
                    }

                    totalDownloaded++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save group locally: ${groupData.group.id}", e)
                }
            }
            _syncState.value = SyncState.Syncing(3, 6, "Groups downloaded", 0, 0)

            // 4. Download in-progress matches
            Log.d(TAG, "Step 4/6: Downloading in-progress matches...")
            _syncState.value = SyncState.Syncing(3, 6, "Downloading live match data...", 0, 0)
            val inProgressMatches = firestoreInProgressMatchDao.downloadAllInProgressMatches(userId)
            Log.d(TAG, "Step 4/6: Downloaded ${inProgressMatches.size} in-progress matches from cloud")
            _syncState.value = SyncState.Syncing(3, 6, "Saving live match data...", 1, 1)

            inProgressMatches.forEach { match ->
                try {
                    db.inProgressMatchDao().upsert(match)
                    totalDownloaded++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save in-progress match locally: ${match.matchId}", e)
                }
            }
            _syncState.value = SyncState.Syncing(4, 6, "Live data downloaded", 0, 0)

            // 5. Download user preferences
            Log.d(TAG, "Step 5/6: Downloading user preferences...")
            _syncState.value = SyncState.Syncing(4, 6, "Downloading preferences...", 0, 0)
            val preferences = firestoreUserPreferencesDao.downloadAllPreferences(userId)
            Log.d(TAG, "Step 5/6: Downloaded ${preferences.size} user preferences from cloud")
            _syncState.value = SyncState.Syncing(4, 6, "Saving preferences...", 1, 1)

            preferences.forEach { pref ->
                try {
                    db.userPreferencesDao().upsert(pref)
                    totalDownloaded++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save preference locally: ${pref.key}", e)
                }
            }
            _syncState.value = SyncState.Syncing(5, 6, "Preferences downloaded", 0, 0)

            // 6. Download group last teams
            Log.d(TAG, "Step 6/6: Downloading group last teams...")
            _syncState.value = SyncState.Syncing(5, 6, "Downloading team configurations...", 0, 0)
            val groupLastTeams = firestoreGroupLastTeamsDao.downloadAllGroupLastTeams(userId)
            Log.d(TAG, "Step 6/6: Downloaded ${groupLastTeams.size} group last teams from cloud")
            _syncState.value = SyncState.Syncing(5, 6, "Saving team configurations...", 1, 1)

            groupLastTeams.forEach { lastTeams ->
                try {
                    db.groupDao().upsertLastTeams(lastTeams)
                    totalDownloaded++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save group last teams locally: ${lastTeams.groupId}", e)
                }
            }

            Log.d(TAG, "Cloud download completed - $totalDownloaded items")
            _syncState.value = SyncState.Syncing(6, 6, "Download complete!", 0, 0)
            _syncState.value = SyncState.Success(totalDownloaded)
            _toastEvents.trySend("Download complete: $totalDownloaded items")
            SyncResult.Success(totalDownloaded)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to download from cloud", e)
            _syncState.value = SyncState.Error("Download failed: ${e.message}", e)
            _toastEvents.trySend("Download failed: ${e.message?.take(50) ?: "unknown error"}")
            SyncResult.Failure("Download failed", e)
        }
    }

    /**
     * Enable or disable auto-sync
     */
    fun setAutoSyncEnabled(enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_SYNC_ENABLED, enabled).apply()
        Log.d(TAG, "Auto-sync ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Check if auto-sync is enabled
     */
    fun isAutoSyncEnabled(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_SYNC_ENABLED, true) // Default: enabled
    }

    /**
     * Get current user ID
     */
    fun getUserId(): String? = _syncMetadata.value.userId

    /**
     * Sign out and clear sync data
     */
    fun signOut() {
        authHelper.signOut()
        _syncMetadata.value = _syncMetadata.value.copy(userId = null)
        Log.d(TAG, "Signed out successfully")
    }

    // ========== Private Helper Methods ==========

    private fun getDeviceId(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)

        if (deviceId == null) {
            deviceId = java.util.UUID.randomUUID().toString()
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

    private fun saveUserId(userId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_USER_ID, userId).apply()
    }
}
