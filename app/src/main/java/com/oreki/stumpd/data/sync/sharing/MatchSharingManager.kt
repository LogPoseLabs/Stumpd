package com.oreki.stumpd.data.sync.sharing

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.oreki.stumpd.data.sync.FirebaseConfig
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

/**
 * Match Sharing System
 * 
 * Allows users to share matches with spectators via 6-digit codes:
 * - Scorer generates a share code
 * - Spectators enter code to watch live
 * - Read-only for spectators
 * - Real-time score updates
 */
class MatchSharingManager(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    
    companion object {
        private const val TAG = "MatchSharingManager"
        private const val COLLECTION_SHARED_MATCHES = "shared_matches"
        private const val CODE_LENGTH = 6
    }
    
    /**
     * Generate a unique 6-digit share code for a match
     */
    private fun generateShareCode(): String {
        return Random.nextInt(100000, 999999).toString()
    }
    
    /**
     * Share a match - generates a code and stores match reference
     * Returns the share code
     */
    suspend fun shareMatch(
        ownerId: String,
        matchId: String,
        ownerName: String? = null,
        expiryHours: Int = 48
    ): Result<String> {
        return try {
            // Generate unique code
            var code = generateShareCode()
            var attempts = 0
            
            // Ensure code is unique
            while (attempts < 5) {
                val existing = firestore
                    .collection(COLLECTION_SHARED_MATCHES)
                    .document(code)
                    .get()
                    .await()
                
                if (!existing.exists()) break
                code = generateShareCode()
                attempts++
            }
            
            if (attempts >= 5) {
                return Result.failure(Exception("Failed to generate unique code"))
            }
            
            // Store share data
            val shareData = mapOf(
                "code" to code,
                "ownerId" to ownerId,
                "matchId" to matchId,
                "ownerName" to (ownerName ?: "Unknown"),
                "createdAt" to System.currentTimeMillis(),
                "expiresAt" to System.currentTimeMillis() + (expiryHours * 3600000L),
                "viewCount" to 0,
                "isActive" to true
            )
            
            firestore
                .collection(COLLECTION_SHARED_MATCHES)
                .document(code)
                .set(shareData, SetOptions.merge())
                .await()
            
            Result.success(code)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Join a shared match using a code
     * Returns the ownerId and matchId if successful
     */
    suspend fun joinSharedMatch(code: String): Result<SharedMatchInfo> {
        return try {
            val doc = firestore
                .collection(COLLECTION_SHARED_MATCHES)
                .document(code.uppercase())
                .get()
                .await()
            
            if (!doc.exists()) {
                return Result.failure(Exception("Invalid code"))
            }
            
            val ownerId = doc.getString("ownerId")
            val matchId = doc.getString("matchId")
            val ownerName = doc.getString("ownerName")
            val expiresAt = doc.getLong("expiresAt") ?: 0L
            val isActive = doc.getBoolean("isActive") ?: true
            
            if (!isActive) {
                return Result.failure(Exception("This share has been disabled"))
            }
            
            if (System.currentTimeMillis() > expiresAt) {
                return Result.failure(Exception("This share code has expired"))
            }
            
            if (ownerId == null || matchId == null) {
                return Result.failure(Exception("Invalid share data"))
            }
            
            // Increment view count
            firestore
                .collection(COLLECTION_SHARED_MATCHES)
                .document(code.uppercase())
                .update("viewCount", com.google.firebase.firestore.FieldValue.increment(1))
                .await()
            
            Result.success(SharedMatchInfo(ownerId, matchId, ownerName ?: "Unknown", code))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Revoke a share code (make it inactive)
     */
    suspend fun revokeShareCode(code: String): Result<Unit> {
        return try {
            firestore
                .collection(COLLECTION_SHARED_MATCHES)
                .document(code.uppercase())
                .update("isActive", false)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Revoke share by matchId (cleanup when match ends)
     */
    suspend fun revokeShare(matchId: String): Result<Unit> {
        return try {
            // Find all shares for this match
            val querySnapshot = firestore
                .collection(COLLECTION_SHARED_MATCHES)
                .whereEqualTo("matchId", matchId)
                .get()
                .await()
            
            // Delete all matching shares
            querySnapshot.documents.forEach { doc ->
                doc.reference.delete().await()
            }
            
            android.util.Log.d(TAG, "Revoked ${querySnapshot.size()} share(s) for match $matchId")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to revoke share for match $matchId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Clean up expired shares (run periodically or on app start)
     * Returns number of shares deleted
     */
    suspend fun cleanupExpiredShares(): Result<Int> {
        return try {
            val currentTime = System.currentTimeMillis()
            
            // Find all expired shares
            val querySnapshot = firestore
                .collection(COLLECTION_SHARED_MATCHES)
                .whereLessThan("expiresAt", currentTime)
                .get()
                .await()
            
            // Delete them
            var count = 0
            querySnapshot.documents.forEach { doc ->
                try {
                    doc.reference.delete().await()
                    count++
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to delete expired share ${doc.id}", e)
                }
            }
            
            android.util.Log.d(TAG, "Cleaned up $count expired share(s)")
            Result.success(count)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to cleanup expired shares", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get all active shares for a user's matches
     */
    suspend fun getActiveShares(ownerId: String): Result<List<ShareInfo>> {
        return try {
            val querySnapshot = firestore
                .collection(COLLECTION_SHARED_MATCHES)
                .whereEqualTo("ownerId", ownerId)
                .whereEqualTo("isActive", true)
                .get()
                .await()
            
            val shares = querySnapshot.documents.mapNotNull { doc ->
                try {
                    ShareInfo(
                        code = doc.getString("code") ?: return@mapNotNull null,
                        matchId = doc.getString("matchId") ?: return@mapNotNull null,
                        createdAt = doc.getLong("createdAt") ?: 0L,
                        expiresAt = doc.getLong("expiresAt") ?: 0L,
                        viewCount = doc.getLong("viewCount")?.toInt() ?: 0
                    )
                } catch (e: Exception) {
                    null
                }
            }
            
            Result.success(shares)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete an in-progress match from Firestore
     * This removes it from the live matches list
     * Only the owner should be able to call this (enforced by Firestore rules)
     */
    suspend fun deleteInProgressMatch(matchId: String): Result<Unit> {
        return try {
            // Delete from in_progress_matches collection
            firestore
                .collection(FirebaseConfig.COLLECTION_IN_PROGRESS_MATCHES)
                .document(matchId)
                .delete()
                .await()
            
            // Also revoke any share codes for this match
            revokeShare(matchId)
            
            android.util.Log.d(TAG, "Deleted in-progress match: $matchId")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to delete in-progress match: $matchId", e)
            Result.failure(e)
        }
    }
    
    /**
     * List all active shared matches (for spectators to browse)
     * Returns list of live matches from the global in_progress_matches collection
     */
    suspend fun listActiveSharedMatches(): List<com.oreki.stumpd.SharedMatchInfo> {
        return try {
            // Query the global in_progress_matches collection directly
            val querySnapshot = firestore
                .collection(FirebaseConfig.COLLECTION_IN_PROGRESS_MATCHES)
                .get()
                .await()
            
            querySnapshot.documents.mapNotNull { doc ->
                try {
                    val matchId = doc.getString("matchId") ?: doc.id
                    val ownerId = doc.getString("ownerId") ?: return@mapNotNull null
                    val team1Name = doc.getString("team1Name") ?: "Team 1"
                    val team2Name = doc.getString("team2Name") ?: "Team 2"
                    val currentInnings = doc.getLong("currentInnings")?.toInt() ?: 1
                    val currentOver = doc.getLong("currentOver")?.toInt() ?: 0
                    val ballsInOver = doc.getLong("ballsInOver")?.toInt() ?: 0
                    val totalWickets = doc.getLong("totalWickets")?.toInt() ?: 0
                    val calculatedTotalRuns = doc.getLong("calculatedTotalRuns")?.toInt() ?: 0
                    val firstInningsRuns = doc.getLong("firstInningsRuns")?.toInt() ?: 0
                    val firstInningsWickets = doc.getLong("firstInningsWickets")?.toInt() ?: 0
                    
                    val overs = "$currentOver.$ballsInOver"
                    val currentScore = "$calculatedTotalRuns/$totalWickets"
                    val firstInningsScore = "$firstInningsRuns/$firstInningsWickets"
                    
                    com.oreki.stumpd.SharedMatchInfo(
                        shareCode = matchId.takeLast(6).uppercase(), // Use last 6 chars of matchId as pseudo-code
                        matchId = matchId,
                        ownerId = ownerId,
                        team1Name = team1Name,
                        team2Name = team2Name,
                        team1Score = if (currentInnings == 1) currentScore else firstInningsScore,
                        team2Score = if (currentInnings == 2) currentScore else null,
                        status = "Innings $currentInnings • $overs overs",
                        ownerName = null
                    )
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error parsing in-progress match", e)
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error listing in-progress matches", e)
            emptyList()
        }
    }
}

/**
 * Information about a shared match
 */
data class SharedMatchInfo(
    val ownerId: String,
    val matchId: String,
    val ownerName: String,
    val code: String
)

/**
 * Information about a share code
 */
data class ShareInfo(
    val code: String,
    val matchId: String,
    val createdAt: Long,
    val expiresAt: Long,
    val viewCount: Int
)
