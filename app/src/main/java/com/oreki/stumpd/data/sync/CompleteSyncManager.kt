package com.oreki.stumpd.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.firestore.FirebaseFirestoreException
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.data.local.db.StumpdDb
import com.oreki.stumpd.data.local.entity.SyncProgressEntity
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
import com.oreki.stumpd.data.sync.firebase.FirestoreTournamentDao
import com.oreki.stumpd.data.util.Constants
import com.oreki.stumpd.utils.StumpdAnalytics
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
    private val groupRepository: GroupRepository,
    private val syncScheduler: SyncScheduler,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val networkMonitor = NetworkMonitor(context)

    // Declared before any property whose initializer reads sync preferences.
    private val prefsLock = Any()

    @Volatile
    private var encryptedPrefs: SharedPreferences? = null

    // Firebase components
    private val authHelper = FirebaseAuthHelper()
    private val firestoreMatchDao = FirestoreMatchDao()
    private val firestorePlayerDao = FirestorePlayerDao()
    private val firestoreGroupDao = FirestoreGroupDao()
    private val firestoreInProgressMatchDao = FirestoreInProgressMatchDao()
    private val firestoreUserPreferencesDao = FirestoreUserPreferencesDao()
    private val firestoreGroupLastTeamsDao = FirestoreGroupLastTeamsDao()
    private val firestoreTournamentDao = FirestoreTournamentDao()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    // Toast events for showing sync progress outside of the sync UI
    private val _toastEvents = Channel<String>(Channel.BUFFERED)
    val toastEvents = _toastEvents.receiveAsFlow()

    private val _syncMetadata = MutableStateFlow(
        SyncMetadata(deviceId = getDeviceId())
    )
    val syncMetadata: StateFlow<SyncMetadata> = _syncMetadata.asStateFlow()

    private var isInitialized = false

    /**
     * Caps how much of the daily free-tier quota one day's syncing may consume, so a large
     * backlog drains over several days instead of failing at the cap.
     */
    private val quotaBudget = SyncQuotaBudget(prefs = { syncPreferences() })

    /** Documents one match upload writes: root doc plus every stat, partnership, wicket, delivery and impact. */
    private fun estimatedWriteCost(match: MatchHistory): Int =
        1 +
            match.firstInningsBatting.size + match.firstInningsBowling.size +
            match.secondInningsBatting.size + match.secondInningsBowling.size +
            match.firstInningsPartnerships.size + match.secondInningsPartnerships.size +
            match.firstInningsFallOfWickets.size + match.secondInningsFallOfWickets.size +
            match.allDeliveries.size +
            match.playerImpacts.size

    /** How many matches still need uploading, for progress reporting. */
    suspend fun pendingUploadCount(): Int {
        val synced = db.syncProgressDao()
            .forCollection(SyncProgressEntity.COLLECTION_MATCHES)
            .associate { it.recordId to it.syncedUpdatedAt }
        return db.matchDao().list(null, Constants.MAX_MATCHES_STORED)
            .count { (synced[it.id] ?: -1L) < it.updatedAt }
    }

    companion object {
        private const val TAG = "CompleteSyncManager"
        private const val PREFS_NAME = "complete_sync_prefs"
        private const val PREFS_MIGRATION_META = "complete_sync_prefs_migration_meta"
        private const val KEY_MIGRATED_PLAIN_TO_ENCRYPTED = "migrated_plain_to_encrypted_v1"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val KEY_LAST_MATCH_SYNC = "last_match_sync_timestamp"
        private const val KEY_LAST_PLAYER_SYNC = "last_player_sync_timestamp"
        private const val KEY_LAST_GROUP_SYNC = "last_group_sync_timestamp"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
    }

    /**
     * Encrypted prefs for sync metadata. Migrates legacy plain [PREFS_NAME] once, then deletes the old file.
     */
    private fun syncPreferences(): SharedPreferences {
        encryptedPrefs?.let { return it }
        synchronized(prefsLock) {
            encryptedPrefs?.let { return it }
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val migrationMeta = context.getSharedPreferences(PREFS_MIGRATION_META, Context.MODE_PRIVATE)
            val alreadyMigrated = migrationMeta.getBoolean(KEY_MIGRATED_PLAIN_TO_ENCRYPTED, false)
            if (!alreadyMigrated) {
                val legacyPlain = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val snapshot = legacyPlain.all.filterValues { it != null }
                if (snapshot.isNotEmpty()) {
                    context.deleteSharedPreferences(PREFS_NAME)
                    val enc = EncryptedSharedPreferences.create(
                        context,
                        PREFS_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                    )
                    val editor = enc.edit()
                    for ((key, value) in snapshot) {
                        @Suppress("UNCHECKED_CAST")
                        when (value) {
                            is String -> editor.putString(key, value)
                            is Long -> editor.putLong(key, value)
                            is Int -> editor.putInt(key, value)
                            is Boolean -> editor.putBoolean(key, value)
                            is Float -> editor.putFloat(key, value)
                            is Set<*> -> if (value.all { it is String }) {
                                editor.putStringSet(key, value as Set<String>)
                            }
                        }
                    }
                    editor.apply()
                    migrationMeta.edit().putBoolean(KEY_MIGRATED_PLAIN_TO_ENCRYPTED, true).apply()
                    encryptedPrefs = enc
                    return enc
                }
                migrationMeta.edit().putBoolean(KEY_MIGRATED_PLAIN_TO_ENCRYPTED, true).apply()
            }
            val enc = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            encryptedPrefs = enc
            return enc
        }
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

        // Recalculate maidens and wicket counts from delivery data (fixes historical bugs)
        // Runs in background so local DB is corrected even without doing a full sync
        scope.launch {
            try {
                val fixed = matchRepository.recalculateDerivedStats()
                if (fixed > 0) Log.d(TAG, "Recalculated derived stats for $fixed matches on init")
            } catch (e: Exception) {
                Log.e(TAG, "Recalculate derived stats on init failed", e)
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

        StumpdAnalytics.syncTriggered("match")
        return try {
            Log.d(TAG, "Syncing match: ${match.team1Name} vs ${match.team2Name}")
            if (uploadMatchWithCloudPolicy(userId, match)) {
                Log.d(TAG, "Match synced successfully: ${match.id}")
                StumpdAnalytics.syncSuccess("match", itemCount = 1)
                SyncResult.Success(1)
            } else {
                SyncResult.NoDataToSync
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync match: ${match.id}", e)
            StumpdAnalytics.syncError("match", e)
            SyncResult.Failure("Failed to sync match", e)
        }
    }

    /**
     * Incremental sync - only syncs data that changed since last sync
     * Called on network reconnect to minimize Firebase quota usage
     * Uses timestamps to track what was synced last time
     */
    suspend fun syncIncrementalChanges(): SyncResult {
        val userId = _syncMetadata.value.userId ?: return SyncResult.Offline

        if (!networkMonitor.isCurrentlyOnline()) {
            return SyncResult.Offline
        }

        val currentTime = System.currentTimeMillis()
        var totalWrites = 0

        return try {
            StumpdAnalytics.syncTriggered("incremental")
            Log.d(TAG, "=== Starting incremental sync (timestamp-based) ===")
            _toastEvents.trySend("Syncing...")

            val lastMatchSync = getLastMatchSyncTimestamp()

            // Pulling group matches down is an enhancement, not a precondition for uploading.
            // It runs a composite-index query (matches on groupId + updatedAt); if that index is
            // missing the call throws FAILED_PRECONDITION, and letting that escape aborted the
            // entire run - including the upload of every pending match - before it started.
            try {
                totalWrites += pullGroupMatchesFromCloud(userId, lastMatchSync)
            } catch (e: Exception) {
                when {
                    e.isIndexBuilding() -> Log.i(
                        TAG,
                        "Firestore index on matches(groupId, updatedAt) is still building - " +
                            "group match downloads resume automatically once it is enabled."
                    )
                    e.isMissingIndex() -> Log.w(
                        TAG,
                        "Firestore needs a composite index on matches(groupId, updatedAt). " +
                            "Downloads of other devices' group matches stay disabled until it " +
                            "is created; uploads are unaffected."
                    )
                    else -> Log.e(TAG, "Skipping cloud pull, continuing with uploads", e)
                }
            }

            // 1. Sync active in-progress match (always, for live spectators)
            val inProgressMatch = db.inProgressMatchDao().getLatest()
            if (inProgressMatch != null) {
                Log.d(TAG, "Syncing active match: ${inProgressMatch.matchId}")
                firestoreInProgressMatchDao.uploadInProgressMatch(userId, inProgressMatch)
                totalWrites++
            }

            // 2. Sync completed matches added/modified since last sync (with stats)
            var matchesFailed = false
            var groupsFailed = false
            var quotaExhausted = false
            var budgetPaused = false
            var remainingAfterPause = 0

            // Same per-record pending set as syncAll. This is the path the periodic worker
            // runs, so it is what actually drains a backlog across days.
            val syncedUpdatedAt = db.syncProgressDao()
                .forCollection(SyncProgressEntity.COLLECTION_MATCHES)
                .associate { it.recordId to it.syncedUpdatedAt }
            val pendingEntities = db.matchDao()
                .list(null, Constants.MAX_MATCHES_STORED)
                .filter { (syncedUpdatedAt[it.id] ?: -1L) < it.updatedAt }
                .sortedBy { it.matchDate }

            if (pendingEntities.isNotEmpty()) {
                Log.d(
                    TAG,
                    "${pendingEntities.size} match(es) pending upload; " +
                        "${quotaBudget.remainingWrites()} write(s) left in today's budget"
                )
                for ((index, entity) in pendingEntities.withIndex()) {
                    val match = matchRepository.getMatchWithStats(entity.id) ?: continue
                    val cost = estimatedWriteCost(match)

                    if (!quotaBudget.canAffordWrites(cost)) {
                        budgetPaused = true
                        remainingAfterPause = pendingEntities.size - index
                        Log.i(TAG, "Daily budget spent - $remainingAfterPause match(es) deferred")
                        break
                    }

                    try {
                        // Only charge the budget for writes that actually happened; a skipped
                        // match (already current in the cloud) costs one read.
                        if (uploadMatchWithCloudPolicy(userId, match)) {
                            totalWrites++
                            quotaBudget.recordWrites(cost)
                        }
                        quotaBudget.recordReads(1)
                        db.syncProgressDao().upsert(
                            SyncProgressEntity(
                                collection = SyncProgressEntity.COLLECTION_MATCHES,
                                recordId = entity.id,
                                syncedUpdatedAt = entity.updatedAt,
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync match: ${match.id}", e)
                        matchesFailed = true
                        if (e.isQuotaExhausted()) {
                            quotaExhausted = true
                            Log.w(TAG, "Quota exhausted after $totalWrites write(s) - stopping")
                            break
                        }
                    }
                }
                // Only advance the watermark if every match uploaded. Otherwise the failed
                // ones would be treated as synced and never retried.
                if (!matchesFailed && !budgetPaused) saveLastMatchSyncTimestamp(currentTime)
            } else {
                Log.d(TAG, "No new matches to sync")
            }

            // 3. Sync players (only if modified since last sync)
            val lastPlayerSync = getLastPlayerSyncTimestamp()
            val changedPlayers = db.playerDao().list().filter { it.updatedAt > lastPlayerSync }
            if (changedPlayers.isNotEmpty()) {
                Log.d(TAG, "Syncing ${changedPlayers.size} changed player(s) (updatedAt > ${lastPlayerSync})")
                firestorePlayerDao.uploadPlayers(userId, changedPlayers)
                totalWrites += changedPlayers.size
                saveLastPlayerSyncTimestamp(currentTime)
            } else {
                Log.d(TAG, "No player changes to sync")
            }

            // 4. Sync groups changed since last sync (only owned groups)
            val lastGroupSync = getLastGroupSyncTimestamp()
            val changedGroups = db.groupDao().getAllGroups().filter { it.isOwner && it.updatedAt > lastGroupSync }
            if (changedGroups.isNotEmpty()) {
                Log.d(TAG, "Syncing ${changedGroups.size} changed owned group(s)")
                val allMembers = db.groupDao().getAllGroupMembers()
                val allUnavailable = db.groupDao().getAllGroupUnavailablePlayers()

                for (group in changedGroups) {
                    try {
                        val members = allMembers.filter { it.groupId == group.id }
                        val unavailable = allUnavailable.filter { it.groupId == group.id }
                        val defaults = db.groupDao().getDefaults(group.id)

                        firestoreGroupDao.uploadGroup(userId, group, members, unavailable, defaults)
                        totalWrites++
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync group: ${group.id}", e)
                        groupsFailed = true
                        if (e.isQuotaExhausted()) {
                            quotaExhausted = true
                            break
                        }
                    }
                }
                if (!groupsFailed) saveLastGroupSyncTimestamp(currentTime)
            } else {
                Log.d(TAG, "No group changes to sync")
            }

            // 5. Tournaments changed since their last successful upload. Keyed per record rather
            // than off the group watermark, because a fixture result changes the tournament
            // without touching its group.
            val tournamentUpload = uploadTournaments(userId) { _, _ -> }
            totalWrites += tournamentUpload.uploaded
            if (tournamentUpload.quotaExhausted) quotaExhausted = true
            if (tournamentUpload.budgetPaused) budgetPaused = true

            if (quotaExhausted || budgetPaused) {
                val message = if (quotaExhausted) {
                    "Cloud quota reached - $totalWrites synced, rest will retry later"
                } else {
                    "Synced $totalWrites. $remainingAfterPause match(es) left - continuing after the daily free quota resets."
                }
                Log.i(TAG, "Incremental sync stopped early: $message")
                _toastEvents.trySend(message)
                SyncResult.QuotaExceeded(totalWrites, message)
            } else {
                Log.d(TAG, "✅ Incremental sync complete: $totalWrites writes")
                StumpdAnalytics.syncSuccess("incremental", itemCount = totalWrites)
                if (totalWrites > 0) {
                    _toastEvents.trySend("Sync complete: $totalWrites items synced")
                } else {
                    _toastEvents.trySend("Sync complete: everything up to date")
                }
                SyncResult.Success(totalWrites)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync incremental changes", e)
            StumpdAnalytics.syncError("incremental", e)
            _toastEvents.trySend("Sync failed: ${e.message?.take(50) ?: "unknown error"}")
            SyncResult.Failure("Failed to sync incremental changes", e)
        }
    }

    // Timestamp tracking helpers
    /**
     * True when this failure is Firestore refusing work because the project's read/write quota
     * is spent, rather than a transient network or permission problem. Retrying soon cannot
     * succeed, and every further attempt is wasted, so callers stop instead of pressing on.
     */
    private fun Throwable.isQuotaExhausted(): Boolean {
        var cause: Throwable? = this
        while (cause != null) {
            if (cause is FirebaseFirestoreException &&
                cause.code == FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED
            ) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    /**
     * True when Firestore refused a query because a composite index has not been created.
     * Unlike a quota or network error this never resolves by itself - it needs an index added
     * to the project - so the affected query is skipped rather than retried forever.
     */
    private fun Throwable.isMissingIndex(): Boolean {
        var cause: Throwable? = this
        while (cause != null) {
            if (cause is FirebaseFirestoreException &&
                cause.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION
            ) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    /**
     * An index that exists but is still backfilling. Firestore reports it with the same
     * FAILED_PRECONDITION code as a missing index, so the only way to tell them apart is the
     * message - worth doing because this one clears on its own and needs no action.
     */
    private fun Throwable.isIndexBuilding(): Boolean {
        var cause: Throwable? = this
        while (cause != null) {
            if (cause.message?.contains("currently building", ignoreCase = true) == true) return true
            cause = cause.cause
        }
        return false
    }

    private fun getLastMatchSyncTimestamp(): Long {
        return syncPreferences().getLong(KEY_LAST_MATCH_SYNC, 0L)
    }

    private fun saveLastMatchSyncTimestamp(timestamp: Long) {
        syncPreferences().edit()
            .putLong(KEY_LAST_MATCH_SYNC, timestamp)
            .apply()
    }

    private fun getLastPlayerSyncTimestamp(): Long {
        return syncPreferences().getLong(KEY_LAST_PLAYER_SYNC, 0L)
    }

    private fun saveLastPlayerSyncTimestamp(timestamp: Long) {
        syncPreferences().edit()
            .putLong(KEY_LAST_PLAYER_SYNC, timestamp)
            .apply()
    }

    private fun getLastGroupSyncTimestamp(): Long {
        return syncPreferences().getLong(KEY_LAST_GROUP_SYNC, 0L)
    }

    private fun saveLastGroupSyncTimestamp(timestamp: Long) {
        syncPreferences().edit()
            .putLong(KEY_LAST_GROUP_SYNC, timestamp)
            .apply()
    }

    // ── Tournaments ─────────────────────────────────────────────────────────────────────

    /** What one pass over the tournaments did, for the caller to fold into its own totals. */
    private data class TournamentSyncOutcome(
        val uploaded: Int = 0,
        val errors: List<String> = emptyList(),
        val failed: Boolean = false,
        val quotaExhausted: Boolean = false,
        val budgetPaused: Boolean = false,
    )

    /**
     * Uploads every changed tournament of a group this device owns.
     *
     * Owner-only, for the same reason match uploads are: a non-owner's write is refused by the
     * Firestore rules, so their fixture results would live on their phone alone — their table
     * would advance while everybody else's stood still, permanently and invisibly.
     *
     * One document per tournament, so the write cost is one per tournament and the watermark can
     * be recorded the moment the cloud accepts it.
     */
    private suspend fun uploadTournaments(
        userId: String,
        onProgress: (done: Int, total: Int) -> Unit,
    ): TournamentSyncOutcome {
        val dao = db.tournamentDao()
        val errors = mutableListOf<String>()
        var uploaded = 0

        return try {
            val ownedGroupIds = db.groupDao().getAllGroups()
                .filter { it.isOwner }
                .map { it.id }
                .toSet()
            if (ownedGroupIds.isEmpty()) return TournamentSyncOutcome()

            val synced = db.syncProgressDao()
                .forCollection(SyncProgressEntity.COLLECTION_TOURNAMENTS)
                .associate { it.recordId to it.syncedUpdatedAt }

            val pending = dao.allTournaments()
                .filter { it.groupId in ownedGroupIds }
                .filter { (synced[it.tournamentId] ?: -1L) < it.updatedAt }
            if (pending.isEmpty()) return TournamentSyncOutcome()

            Log.d(TAG, "${pending.size} tournament(s) pending upload")
            var quotaExhausted = false
            var budgetPaused = false

            for ((index, tournament) in pending.withIndex()) {
                if (!quotaBudget.canAffordWrites(1)) {
                    budgetPaused = true
                    Log.i(TAG, "Daily budget spent - deferring ${pending.size - index} tournament(s)")
                    break
                }
                onProgress(index + 1, pending.size)
                try {
                    firestoreTournamentDao.uploadTournament(
                        ownerId = userId,
                        tournament = tournament,
                        teams = dao.teams(tournament.tournamentId),
                        squads = dao.squads(tournament.tournamentId),
                        fixtures = dao.fixtures(tournament.tournamentId),
                    )
                    quotaBudget.recordWrites(1)
                    uploaded++
                    // Recorded straight after the cloud accepted it, so a stop on the next one
                    // cannot lose this progress.
                    db.syncProgressDao().upsert(
                        SyncProgressEntity(
                            collection = SyncProgressEntity.COLLECTION_TOURNAMENTS,
                            recordId = tournament.tournamentId,
                            syncedUpdatedAt = tournament.updatedAt,
                        )
                    )
                } catch (e: Exception) {
                    errors.add("Tournament ${tournament.name}: ${e.message}")
                    Log.e(TAG, "Failed to sync tournament: ${tournament.tournamentId}", e)
                    if (e.isQuotaExhausted()) {
                        quotaExhausted = true
                        break
                    }
                }
            }

            TournamentSyncOutcome(
                uploaded = uploaded,
                errors = errors,
                failed = errors.isNotEmpty(),
                quotaExhausted = quotaExhausted,
                budgetPaused = budgetPaused,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync tournaments", e)
            TournamentSyncOutcome(
                uploaded = uploaded,
                errors = errors + "Failed to sync tournaments: ${e.message}",
                failed = true,
                quotaExhausted = e.isQuotaExhausted(),
            )
        }
    }

    /**
     * Downloads the tournaments of every group this device can see.
     *
     * Applied as **delete-then-insert scoped to the tournament**: the cloud copy is authoritative
     * for the whole aggregate, the same reasoning as `MatchDao.replaceFullMatch`. Patching it
     * field by field would leave a team or a fixture behind that the owner had removed.
     *
     * Last-write-wins on `updatedAt`, so a local copy that is newer than the cloud's — the owner's
     * own device, mid-sync — is left alone.
     */
    private suspend fun downloadTournaments(groupIds: List<String>): Int {
        val dao = db.tournamentDao()
        var applied = 0

        groupIds.forEach { groupId ->
            try {
                firestoreTournamentDao.downloadTournaments(groupId).forEach { cloud ->
                    val local = dao.tournament(cloud.tournament.tournamentId)
                    if (local != null && cloud.tournament.updatedAt <= local.updatedAt) {
                        return@forEach
                    }
                    dao.replaceTournament(
                        tournament = cloud.tournament,
                        teams = cloud.teams,
                        squads = cloud.squads,
                        fixtures = cloud.fixtures,
                    )
                    applied++
                }
            } catch (e: Exception) {
                // One group's tournaments failing must not stop the rest of the download.
                Log.w(TAG, "Couldn't download tournaments for group $groupId", e)
            }
        }
        return applied
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
     * Sync local data to Firestore, writing only items that are new or changed since the last
     * successful cloud sync (compares local [updatedAt] to Firestore [updatedAt] per match).
     *
     * Unlike a blind re-upload, already-synced matches are skipped to reduce Firestore quota use.
     */
    suspend fun syncAll(): SyncResult {
        val userId = _syncMetadata.value.userId ?: return SyncResult.Offline

        if (!networkMonitor.isCurrentlyOnline()) {
            Log.w(TAG, "Cannot sync - device is offline")
            _syncState.value = SyncState.Offline
            return SyncResult.Offline
        }

        _syncState.value = SyncState.Syncing(0, 7, "Preparing to sync...")
        _toastEvents.trySend("Syncing pending changes...")
        StumpdAnalytics.syncTriggered("full")

        return try {
            Log.d(TAG, "Starting pending cloud sync...")

            var totalSynced = 0
            var totalSkipped = 0
            val errors = mutableListOf<String>()
            val lastMatchSync = getLastMatchSyncTimestamp()
            val lastPlayerSync = getLastPlayerSyncTimestamp()
            val lastGroupSync = getLastGroupSyncTimestamp()

            // Captured before any work starts. Saving a post-run timestamp would mark records
            // that changed *during* the run as already synced, so they'd never upload.
            val runStartedAt = System.currentTimeMillis()

            // A collection's sync timestamp may only advance if that collection uploaded
            // cleanly. Advancing it after a failure is what silently strands local data.
            var matchesFailed = false
            var playersFailed = false
            var groupsFailed = false
            var quotaExhausted = false

            // Set when we deliberately stop to stay inside the day's free-tier allowance.
            // Not a failure: the remaining records keep their pending status and resume later.
            var budgetPaused = false
            var remainingAfterPause = 0

            // 0. Recalculate derived stats (maidens, wicket counts) from delivery data
            Log.d(TAG, "Step 0/7: Recalculating derived stats...")
            _syncState.value = SyncState.Syncing(0, 7, "Recalculating stats...", 0, 0)
            try {
                val fixed = matchRepository.recalculateDerivedStats()
                Log.d(TAG, "Recalculated stats: $fixed matches updated")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to recalculate derived stats", e)
            }
            _syncState.value = SyncState.Syncing(1, 7, "Stats recalculated", 0, 0)

            // 1. Sync matches that changed locally or are not yet in the cloud
            Log.d(TAG, "Step 1/7: Syncing matches...")
            _syncState.value = SyncState.Syncing(1, 7, "Uploading matches...", 0, 0)
            try {
                // Pending set comes from per-record progress rather than a single watermark, so
                // an interrupted run resumes exactly where it stopped. Costs no cloud reads:
                // the old fallback branch did one root-doc read per match just to decide this.
                val syncedUpdatedAt = db.syncProgressDao()
                    .forCollection(SyncProgressEntity.COLLECTION_MATCHES)
                    .associate { it.recordId to it.syncedUpdatedAt }

                val pendingEntities = db.matchDao()
                    .list(null, Constants.MAX_MATCHES_STORED)
                    .filter { (syncedUpdatedAt[it.id] ?: -1L) < it.updatedAt }
                    .sortedBy { it.matchDate } // oldest first, so progress is predictable
                Log.d(
                    TAG,
                    "${pendingEntities.size} match(es) pending upload; " +
                        "${quotaBudget.remainingWrites()} write(s) left in today's budget"
                )

                for ((index, entity) in pendingEntities.withIndex()) {
                    val match = matchRepository.getMatchWithStats(entity.id) ?: continue
                    val cost = estimatedWriteCost(match)

                    if (!quotaBudget.canAffordWrites(cost)) {
                        budgetPaused = true
                        remainingAfterPause = pendingEntities.size - index
                        Log.i(
                            TAG,
                            "Daily quota budget spent - pausing with $remainingAfterPause match(es) " +
                                "left; they resume after the quota resets"
                        )
                        break
                    }

                    _syncState.value = SyncState.Syncing(
                        1, 7,
                        "Uploading match ${index + 1}/${pendingEntities.size}...",
                        index + 1,
                        pendingEntities.size,
                    )
                    try {
                        when (uploadMatchWithCloudPolicy(userId, match)) {
                            // Only charge the budget for writes that actually happened. A match
                            // the policy skips (already current in the cloud) costs one read.
                            true -> {
                                totalSynced++
                                quotaBudget.recordWrites(cost)
                            }
                            false -> totalSkipped++
                        }
                        quotaBudget.recordReads(1) // the root-doc policy check
                        // Recorded immediately after the cloud accepted this match, so a crash
                        // or quota stop on the very next one cannot lose this progress. Also
                        // recorded when the policy skipped it, so we stop re-checking it daily.
                        db.syncProgressDao().upsert(
                            SyncProgressEntity(
                                collection = SyncProgressEntity.COLLECTION_MATCHES,
                                recordId = entity.id,
                                syncedUpdatedAt = entity.updatedAt,
                            )
                        )
                    } catch (e: Exception) {
                        errors.add("Match ${match.id}: ${e.message}")
                        Log.e(TAG, "Failed to sync match: ${match.id}", e)
                        matchesFailed = true
                        if (e.isQuotaExhausted()) {
                            quotaExhausted = true
                            Log.w(TAG, "Firestore quota exhausted after $totalSynced match(es) - stopping")
                            break
                        }
                    }
                }

                Log.d(TAG, "Matches: $totalSynced uploaded, $totalSkipped skipped")
            } catch (e: Exception) {
                errors.add("Failed to fetch matches: ${e.message}")
                Log.e(TAG, "Failed to fetch matches", e)
                matchesFailed = true
                if (e.isQuotaExhausted()) quotaExhausted = true
            }
            _syncState.value = SyncState.Syncing(2, 7, "Matches uploaded", 0, 0)

            // 2. Sync players changed since last sync
            Log.d(TAG, "Step 2/7: Syncing players...")
            _syncState.value = SyncState.Syncing(2, 7, "Uploading players...", 0, 0)
            try {
                val players = db.playerDao().list().filter { it.updatedAt > lastPlayerSync }
                if (players.isNotEmpty() && !quotaBudget.canAffordWrites(players.size)) {
                    // Leave the watermark alone so these retry once the quota resets. The upload
                    // is one atomic batch, so there is no partial state to checkpoint.
                    budgetPaused = true
                    playersFailed = true
                    Log.i(TAG, "Daily budget spent - deferring ${players.size} player upload(s)")
                } else if (players.isNotEmpty()) {
                    quotaBudget.recordWrites(players.size)
                    Log.d(TAG, "Syncing ${players.size} changed player(s)...")
                    _syncState.value = SyncState.Syncing(2, 7, "Uploading ${players.size} players...", 1, 1)
                    firestorePlayerDao.uploadPlayers(userId, players)
                    totalSynced += players.size
                    Log.d(TAG, "Players synced: ${players.size}")
                } else {
                    Log.d(TAG, "No player changes to sync")
                    totalSkipped++
                }
            } catch (e: Exception) {
                errors.add("Failed to sync players: ${e.message}")
                Log.e(TAG, "Failed to sync players", e)
                playersFailed = true
                if (e.isQuotaExhausted()) quotaExhausted = true
            }
            _syncState.value = SyncState.Syncing(3, 7, "Players uploaded", 0, 0)

            // 3. Sync owned groups changed since last sync
            Log.d(TAG, "Step 3/7: Syncing groups...")
            _syncState.value = SyncState.Syncing(3, 7, "Uploading groups...", 0, 0)
            try {
                val groups = db.groupDao().getAllGroups().filter { it.isOwner && it.updatedAt > lastGroupSync }
                Log.d(TAG, "Syncing ${groups.size} changed owned group(s)...")

                val allMembers = db.groupDao().getAllGroupMembers()
                val allUnavailable = db.groupDao().getAllGroupUnavailablePlayers()

                for ((index, group) in groups.withIndex()) {
                    // One group doc per upload, plus the existing-doc read inside uploadGroup.
                    if (!quotaBudget.canAffordWrites(1)) {
                        budgetPaused = true
                        groupsFailed = true
                        Log.i(TAG, "Daily budget spent - deferring ${groups.size - index} group(s)")
                        break
                    }
                    _syncState.value = SyncState.Syncing(3, 7, "Uploading group ${index + 1}/${groups.size}...", index + 1, groups.size)
                    try {
                        val members = allMembers.filter { it.groupId == group.id }
                        val unavailable = allUnavailable.filter { it.groupId == group.id }
                        val defaults = db.groupDao().getDefaults(group.id)

                        firestoreGroupDao.uploadGroup(userId, group, members, unavailable, defaults)
                        quotaBudget.recordWrites(1)
                        quotaBudget.recordReads(1)
                        totalSynced++
                    } catch (e: Exception) {
                        errors.add("Group ${group.name}: ${e.message}")
                        Log.e(TAG, "Failed to sync group: ${group.id}", e)
                        groupsFailed = true
                        if (e.isQuotaExhausted()) {
                            quotaExhausted = true
                            Log.w(TAG, "Firestore quota exhausted while uploading groups - stopping")
                            break
                        }
                    }
                }

                Log.d(TAG, "Groups synced: ${groups.size}")
            } catch (e: Exception) {
                errors.add("Failed to sync groups: ${e.message}")
                Log.e(TAG, "Failed to sync groups", e)
                groupsFailed = true
                if (e.isQuotaExhausted()) quotaExhausted = true
            }

            // 3b. Tournaments of owned groups. Part of the group step rather than a step of its
            // own, because a tournament lives inside its group document and is meaningless
            // without it — and because the failure handling is the same.
            _syncState.value = SyncState.Syncing(3, 7, "Uploading tournaments...", 0, 0)
            val tournamentUpload = uploadTournaments(userId) { done, total ->
                _syncState.value = SyncState.Syncing(
                    3, 7, "Uploading tournament $done/$total...", done, total,
                )
            }
            totalSynced += tournamentUpload.uploaded
            errors.addAll(tournamentUpload.errors)
            if (tournamentUpload.quotaExhausted) quotaExhausted = true
            if (tournamentUpload.budgetPaused) budgetPaused = true
            if (tournamentUpload.failed) groupsFailed = true

            _syncState.value = SyncState.Syncing(4, 7, "Groups uploaded", 0, 0)

            // 4. Sync in-progress matches (ongoing games)
            Log.d(TAG, "Step 4/7: Syncing in-progress matches...")
            _syncState.value = SyncState.Syncing(4, 7, "Uploading live match data...", 0, 1)
            try {
                val inProgressMatch = db.inProgressMatchDao().getLatest()
                if (inProgressMatch != null) {
                    _syncState.value = SyncState.Syncing(4, 7, "Uploading live match...", 1, 1)
                    Log.d(TAG, "Syncing in-progress match...")
                    firestoreInProgressMatchDao.uploadInProgressMatch(userId, inProgressMatch)
                    totalSynced++
                    Log.d(TAG, "In-progress match synced")
                }
            } catch (e: Exception) {
                errors.add("Failed to sync in-progress match: ${e.message}")
                Log.e(TAG, "Failed to sync in-progress match", e)
            }
            _syncState.value = SyncState.Syncing(5, 7, "Live match data uploaded", 0, 0)

            // 5. Sync user preferences (app settings)
            Log.d(TAG, "Step 5/7: Syncing preferences...")
            _syncState.value = SyncState.Syncing(5, 7, "Uploading preferences...", 0, 0)
            try {
                val preferences = db.userPreferencesDao().getAll()
                if (preferences.isNotEmpty()) {
                    _syncState.value = SyncState.Syncing(5, 7, "Uploading ${preferences.size} preferences...", 1, 1)
                    Log.d(TAG, "Syncing ${preferences.size} user preferences...")
                    firestoreUserPreferencesDao.uploadPreferences(userId, preferences)
                    totalSynced += preferences.size
                    Log.d(TAG, "User preferences synced: ${preferences.size}")
                }
            } catch (e: Exception) {
                errors.add("Failed to sync preferences: ${e.message}")
                Log.e(TAG, "Failed to sync preferences", e)
            }
            _syncState.value = SyncState.Syncing(6, 7, "Preferences uploaded", 0, 0)

            // 6. Sync group last teams configurations
            Log.d(TAG, "Step 6/7: Syncing group last teams...")
            _syncState.value = SyncState.Syncing(6, 7, "Uploading team configurations...", 0, 0)
            try {
                val groupLastTeams = db.groupDao().getAllGroupLastTeams()
                if (groupLastTeams.isNotEmpty()) {
                    _syncState.value = SyncState.Syncing(6, 7, "Uploading ${groupLastTeams.size} team configs...", 1, 1)
                    Log.d(TAG, "Syncing ${groupLastTeams.size} group last teams configs...")
                    firestoreGroupLastTeamsDao.uploadGroupLastTeams(userId, groupLastTeams)
                    totalSynced += groupLastTeams.size
                    Log.d(TAG, "Group last teams synced: ${groupLastTeams.size}")
                }
            } catch (e: Exception) {
                errors.add("Failed to sync group last teams: ${e.message}")
                Log.e(TAG, "Failed to sync group last teams", e)
            }
            _syncState.value = SyncState.Syncing(7, 7, "Finalizing...", 0, 0)

            // Update metadata and timestamps
            _syncMetadata.value = _syncMetadata.value.copy(
                lastSyncTimestamp = System.currentTimeMillis(),
                lastSyncSuccess = errors.isEmpty(),
                pendingUploads = 0
            )
            saveLastSyncTimestamp()

            // Only advance a collection's watermark when that collection actually uploaded
            // cleanly. Advancing it after a failure (quota, network, permission) would mark
            // the un-uploaded records as synced and they would never be retried.
            if (!matchesFailed) saveLastMatchSyncTimestamp(runStartedAt)
            if (!playersFailed) saveLastPlayerSyncTimestamp(runStartedAt)
            if (!groupsFailed) saveLastGroupSyncTimestamp(runStartedAt)
            if (matchesFailed || playersFailed || groupsFailed) {
                Log.w(
                    TAG,
                    "Kept sync watermark back for: " +
                        listOfNotNull(
                            "matches".takeIf { matchesFailed },
                            "players".takeIf { playersFailed },
                            "groups".takeIf { groupsFailed },
                        ).joinToString(", ") + " - they will retry next sync"
                )
            }

            if (quotaExhausted) {
                _syncState.value = SyncState.Error("Firestore quota exhausted - sync paused", null)
                _toastEvents.trySend("Cloud quota reached - $totalSynced synced, rest will retry later")
                Log.w(TAG, "Full sync stopped early on quota - $totalSynced items uploaded")
                return SyncResult.QuotaExceeded(totalSynced, "Firestore quota exhausted")
            }

            if (budgetPaused) {
                val message = "Synced $totalSynced. $remainingAfterPause match(es) left - " +
                    "continuing after the daily free quota resets."
                _syncState.value = SyncState.Success(totalSynced)
                _toastEvents.trySend(message)
                Log.i(TAG, "Full sync paused on daily budget - $message")
                return SyncResult.QuotaExceeded(totalSynced, message)
            }

            _syncState.value = if (errors.isEmpty()) {
                SyncState.Success(totalSynced)
            } else {
                val detail = errors.firstOrNull() ?: "Unknown"
                SyncState.Error("Partial sync - ${errors.size} errors: $detail", null)
            }

            Log.d(TAG, "Full sync completed - $totalSynced items synced, ${errors.size} errors")
            if (errors.isNotEmpty()) {
                errors.forEachIndexed { i, msg -> Log.e(TAG, "Sync error ${i + 1}: $msg") }
            }

            if (errors.isEmpty()) {
                _toastEvents.trySend("Sync complete: $totalSynced items synced")
                StumpdAnalytics.syncSuccess("full", itemCount = totalSynced, hadErrors = false)
                SyncResult.Success(totalSynced)
            } else {
                _toastEvents.trySend("Sync partially done: $totalSynced synced, ${errors.size} errors")
                StumpdAnalytics.syncSuccess("full", itemCount = totalSynced, hadErrors = true)
                SyncResult.PartialSuccess(totalSynced, errors.size, errors)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Full sync failed catastrophically", e)
            StumpdAnalytics.syncError("full", e)
            _syncState.value = SyncState.Error("Sync failed: ${e.message}", e)
            _toastEvents.trySend("Sync failed: ${e.message?.take(50) ?: "unknown error"}")
            SyncResult.Failure("Full sync failed", e)
        }
    }

    /**
     * Enqueues a WorkManager job to run [syncAll]. Survives process death; use from UI.
     */
    fun launchSyncAll() {
        syncScheduler.enqueueFullSync()
    }

    /**
     * Enqueues a WorkManager job to download all data from the cloud.
     * Survives process death; use from Composables/Activities.
     */
    fun launchDownloadAllFromCloud() {
        syncScheduler.enqueueDownloadFromCloud()
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
            StumpdAnalytics.syncTriggered("download")
            Log.d(TAG, "Downloading all data from cloud...")
            _syncState.value = SyncState.Syncing(0, 6, "Preparing to download...")
            _toastEvents.trySend("Downloading from cloud...")

            var totalDownloaded = 0

            // 1. Download all matches (fetch IDs first, then download each with progress)
            Log.d(TAG, "Step 1/6: Downloading matches...")
            _syncState.value = SyncState.Syncing(0, 6, "Fetching match list...", 0, 0)
            val matchIds = firestoreMatchDao.getMatchDocIds()
            Log.d(TAG, "Step 1/6: Found ${matchIds.size} matches in cloud")

            var downloadQuotaExhausted = false
            var skippedUpToDate = 0

            for ((index, matchId) in matchIds.withIndex()) {
                _syncState.value = SyncState.Syncing(0, 6, "Downloading match ${index + 1}/${matchIds.size}...", index + 1, matchIds.size)
                try {
                    val local = db.matchDao().getById(matchId)

                    // Check the cheap root document first. A full match download pulls stats,
                    // partnerships, fall of wickets and one document per delivery, so doing it
                    // for matches we already hold was by far the largest read cost - and the
                    // result was usually discarded as "local newer or equal".
                    val meta = firestoreMatchDao.getMatchRootMeta(matchId)
                    val cloudTs = meta.updatedAt ?: 0L
                    val localTs = local?.updatedAt ?: 0L
                    if (local != null && cloudTs <= localTs) {
                        skippedUpToDate++
                        continue
                    }

                    val bundle = firestoreMatchDao.downloadCompleteMatchWithSync(matchId) ?: continue
                    val effectiveTs = bundle.rootUpdatedAt ?: cloudTs
                    matchRepository.saveMatch(
                        bundle.match,
                        persistedUpdatedAt = effectiveTs.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    )
                    totalDownloaded++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to download/save match: $matchId", e)
                    if (e.isQuotaExhausted()) {
                        downloadQuotaExhausted = true
                        Log.w(TAG, "Quota exhausted after downloading $totalDownloaded match(es) - stopping")
                        break
                    }
                }
            }
            Log.d(TAG, "Matches: $totalDownloaded downloaded, $skippedUpToDate already up to date")
            _syncState.value = SyncState.Syncing(1, 6, "Matches downloaded", 0, 0)

            // 2. Download all players
            Log.d(TAG, "Step 2/6: Downloading players...")
            _syncState.value = SyncState.Syncing(1, 6, "Downloading players...", 0, 0)
            val players = firestorePlayerDao.downloadAllPlayers(userId)
            Log.d(TAG, "Step 2/6: Downloaded ${players.size} players from cloud")
            _syncState.value = SyncState.Syncing(1, 6, "Saving ${players.size} players...", 1, 1)

            if (players.isNotEmpty()) {
                try {
                    val toUpsert = players.filter { cloud ->
                        val local = db.playerDao().get(cloud.id)
                        local == null || cloud.updatedAt > local.updatedAt
                    }
                    if (toUpsert.isNotEmpty()) {
                        db.playerDao().upsert(toUpsert)
                        totalDownloaded += toUpsert.size
                    }
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
                    val existingLocal = db.groupDao().getGroupById(groupData.group.id)
                    val cloudTs = groupData.group.updatedAt
                    val localTs = existingLocal?.updatedAt ?: 0L
                    if (existingLocal != null && cloudTs <= localTs) {
                        return@forEachIndexed
                    }
                    // Claim code is NEVER in the Firestore download (only a hash is stored there).
                    // Always preserve the local plaintext claim code from Room DB.
                    // Invite code can be missing in Firestore after a bad upload or older docs — keep local copy.
                    val groupToSave = if (existingLocal?.claimCode != null) {
                        groupData.group.copy(
                            claimCode = existingLocal.claimCode,
                            inviteCode = groupData.group.inviteCode ?: existingLocal.inviteCode,
                        )
                    } else {
                        groupData.group.copy(
                            inviteCode = groupData.group.inviteCode ?: existingLocal?.inviteCode,
                        )
                    }
                    db.groupDao().upsertGroup(groupToSave)

                    // Clear existing members before inserting
                    db.groupDao().clearMembers(groupData.group.id)
                    groupData.members.forEach { member ->
                        db.groupDao().upsertMembers(listOf(member))
                    }

                    db.groupDao().clearUnavailablePlayers(groupData.group.id)
                    val memberIds = groupData.members.map { it.playerId }.toSet()
                    groupData.unavailable.filter { it.playerId in memberIds }.forEach { unavailable ->
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
            // 3b. Tournaments, after their groups — a tournament needs its group row to exist,
            // and its read-only view on a member's device is derived from the group's ownership.
            _syncState.value = SyncState.Syncing(2, 6, "Downloading tournaments...", 0, 0)
            totalDownloaded += downloadTournaments(db.groupDao().getAllGroups().map { it.id })

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

            if (downloadQuotaExhausted) {
                Log.w(TAG, "Cloud download stopped early on quota - $totalDownloaded items")
                _syncState.value = SyncState.Error("Firestore quota exhausted - download paused", null)
                _toastEvents.trySend("Cloud quota reached - $totalDownloaded downloaded, rest will retry later")
                SyncResult.QuotaExceeded(totalDownloaded, "Firestore quota exhausted")
            } else {
                Log.d(TAG, "Cloud download completed - $totalDownloaded items")
                _syncState.value = SyncState.Syncing(6, 6, "Download complete!", 0, 0)
                _syncState.value = SyncState.Success(totalDownloaded)
                _toastEvents.trySend("Download complete: $totalDownloaded items")
                StumpdAnalytics.syncSuccess("download", itemCount = totalDownloaded)
                SyncResult.Success(totalDownloaded)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to download from cloud", e)
            StumpdAnalytics.syncError("download", e)
            _syncState.value = SyncState.Error("Download failed: ${e.message}", e)
            _toastEvents.trySend("Download failed: ${e.message?.take(50) ?: "unknown error"}")
            SyncResult.Failure("Download failed", e)
        }
    }

    /**
     * Enable or disable auto-sync
     */
    fun setAutoSyncEnabled(enabled: Boolean) {
        syncPreferences().edit().putBoolean(KEY_AUTO_SYNC_ENABLED, enabled).apply()
        Log.d(TAG, "Auto-sync ${if (enabled) "enabled" else "disabled"}")
        if (enabled) {
            syncScheduler.schedulePeriodicIncrementalSync()
        } else {
            syncScheduler.cancelPeriodicIncrementalSync()
        }
    }

    /**
     * Check if auto-sync is enabled
     */
    fun isAutoSyncEnabled(): Boolean {
        return syncPreferences().getBoolean(KEY_AUTO_SYNC_ENABLED, true) // Default: enabled
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

    private suspend fun isDestinationGroupOwner(userId: String, groupId: String?): Boolean {
        if (groupId.isNullOrBlank()) return true
        return firestoreGroupDao.isGroupOwner(groupId, userId)
    }

    /**
     * Upload a match if policy allows (group-scoped matches: destination group owner only).
     * @return true if an upload was performed
     */
    private suspend fun uploadMatchWithCloudPolicy(userId: String, match: MatchHistory): Boolean {
        val localUpdatedAt = db.matchDao().getById(match.id)?.updatedAt ?: System.currentTimeMillis()
        val meta = firestoreMatchDao.getMatchRootMeta(match.id)
        val groupId = match.groupId ?: meta.groupId
        val isGroupOwner = isDestinationGroupOwner(userId, groupId)

        when (
            MatchCloudSyncPolicy.decideMatchUpload(
                userId = userId,
                localUpdatedAt = localUpdatedAt,
                cloudDocExists = meta.exists,
                cloudOwnerId = meta.ownerId,
                cloudUpdatedAt = meta.updatedAt ?: 0L,
                groupId = groupId,
                isDestinationGroupOwner = isGroupOwner,
            )
        ) {
            MatchUploadDecision.UploadAsOwner -> {
                firestoreMatchDao.uploadCompleteMatch(userId, match, localUpdatedAt)
                return true
            }
            MatchUploadDecision.SkipAlreadySynced -> {
                Log.d(TAG, "Skipped upload (already synced): ${match.id}")
                return false
            }
            MatchUploadDecision.SkipCloudNewer -> {
                mergeCloudMatchIntoLocal(match.id)
                Log.d(TAG, "Skipped upload (cloud newer): ${match.id}")
                return false
            }
            MatchUploadDecision.SkipNotAuthorized -> {
                Log.d(TAG, "Skipped upload (not group owner): ${match.id} group=$groupId")
                return false
            }
        }
    }

    private suspend fun mergeCloudMatchIntoLocal(matchId: String) {
        val bundle = firestoreMatchDao.downloadCompleteMatchWithSync(matchId) ?: return
        val local = db.matchDao().getById(matchId)
        val cloudTs = bundle.rootUpdatedAt ?: 0L
        val localTs = local?.updatedAt ?: 0L
        if (local == null || cloudTs > localTs) {
            matchRepository.saveMatch(
                bundle.match,
                persistedUpdatedAt = cloudTs.takeIf { it > 0L } ?: System.currentTimeMillis(),
            )
        }
    }

    /** Download matches for groups this device belongs to (read-only for members; owner receives cloud updates). */
    private suspend fun pullGroupMatchesFromCloud(userId: String, sinceMillis: Long): Int {
        val groupIds = db.groupDao().getAllGroups().map { it.id }
        if (groupIds.isEmpty()) return 0
        val matchIds = firestoreMatchDao.listMatchIdsInGroupsUpdatedAfter(groupIds, sinceMillis)
        if (matchIds.isEmpty()) return 0
        Log.d(TAG, "Pulling ${matchIds.size} group match(es) updated since $sinceMillis")
        var merged = 0
        matchIds.forEach { matchId ->
            try {
                val before = db.matchDao().getById(matchId)?.updatedAt ?: 0L
                mergeCloudMatchIntoLocal(matchId)
                val after = db.matchDao().getById(matchId)?.updatedAt ?: 0L
                if (after > before) merged++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pull match: $matchId", e)
            }
        }
        return merged
    }

    private fun getDeviceId(): String {
        val prefs = syncPreferences()
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)

        if (deviceId == null) {
            deviceId = java.util.UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }

        return deviceId
    }

    private fun saveLastSyncTimestamp() {
        syncPreferences().edit()
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()
    }

    private fun saveUserId(userId: String) {
        syncPreferences().edit().putString(KEY_USER_ID, userId).apply()
    }
}
