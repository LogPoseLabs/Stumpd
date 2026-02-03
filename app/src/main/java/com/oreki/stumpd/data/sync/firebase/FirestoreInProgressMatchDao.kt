package com.oreki.stumpd.data.sync.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.oreki.stumpd.data.local.entity.InProgressMatchEntity
import com.oreki.stumpd.data.sync.FirebaseConfig
import kotlinx.coroutines.tasks.await

/**
 * Firebase Firestore data access layer for in-progress matches
 * Syncs ongoing matches that haven't been completed yet
 * 
 * DATA IS GLOBAL - All users can see all in-progress matches for live spectating
 * ownerId field tracks who is scoring the match
 */
class FirestoreInProgressMatchDao(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    
    /**
     * Upload an in-progress match to Firestore (GLOBAL)
     * This allows live spectating and resuming matches
     * @param ownerId The user who is scoring this match
     */
    suspend fun uploadInProgressMatch(ownerId: String, match: InProgressMatchEntity) {
        val docRef = firestore
            .collection(FirebaseConfig.COLLECTION_IN_PROGRESS_MATCHES)
            .document(match.matchId)
        
        val data = mapOf(
            "matchId" to match.matchId,
            "team1Name" to match.team1Name,
            "team2Name" to match.team2Name,
            "jokerName" to match.jokerName,
            "groupId" to match.groupId,
            "groupName" to match.groupName,
            "tossWinner" to match.tossWinner,
            "tossChoice" to match.tossChoice,
            "matchSettingsJson" to match.matchSettingsJson,
            
            // Player info
            "team1PlayerIds" to match.team1PlayerIds,
            "team2PlayerIds" to match.team2PlayerIds,
            "team1PlayerNames" to match.team1PlayerNames,
            "team2PlayerNames" to match.team2PlayerNames,
            
            // Current match state
            "currentInnings" to match.currentInnings,
            "currentOver" to match.currentOver,
            "ballsInOver" to match.ballsInOver,
            "totalWickets" to match.totalWickets,
            
            // Team players state (serialized as JSON)
            "team1PlayersJson" to match.team1PlayersJson,
            "team2PlayersJson" to match.team2PlayersJson,
            
            // Current roles
            "strikerIndex" to match.strikerIndex,
            "nonStrikerIndex" to match.nonStrikerIndex,
            "bowlerIndex" to match.bowlerIndex,
            
            // Innings data
            "firstInningsRuns" to match.firstInningsRuns,
            "firstInningsWickets" to match.firstInningsWickets,
            "firstInningsOvers" to match.firstInningsOvers,
            "firstInningsBalls" to match.firstInningsBalls,
            
            // Extras
            "totalExtras" to match.totalExtras,
            "calculatedTotalRuns" to match.calculatedTotalRuns,
            
            // Completed players lists
            "completedBattersInnings1Json" to match.completedBattersInnings1Json,
            "completedBattersInnings2Json" to match.completedBattersInnings2Json,
            "completedBowlersInnings1Json" to match.completedBowlersInnings1Json,
            "completedBowlersInnings2Json" to match.completedBowlersInnings2Json,
            
            // First innings stats
            "firstInningsBattingPlayersJson" to match.firstInningsBattingPlayersJson,
            "firstInningsBowlingPlayersJson" to match.firstInningsBowlingPlayersJson,
            
            // Joker tracking
            "jokerOutInCurrentInnings" to match.jokerOutInCurrentInnings,
            "jokerBallsBowledInnings1" to match.jokerBallsBowledInnings1,
            "jokerBallsBowledInnings2" to match.jokerBallsBowledInnings2,
            
            // Powerplay tracking
            "powerplayRunsInnings1" to match.powerplayRunsInnings1,
            "powerplayRunsInnings2" to match.powerplayRunsInnings2,
            "powerplayDoublingDoneInnings1" to match.powerplayDoublingDoneInnings1,
            "powerplayDoublingDoneInnings2" to match.powerplayDoublingDoneInnings2,
            
            // Deliveries
            "allDeliveriesJson" to match.allDeliveriesJson,
            
            // Timestamps
            "lastSavedAt" to match.lastSavedAt,
            "startedAt" to match.startedAt,
            FirebaseConfig.FIELD_OWNER_ID to ownerId, // Track who is scoring
            FirebaseConfig.FIELD_UPDATED_AT to System.currentTimeMillis()
        )
        
        docRef.set(data, SetOptions.merge()).await()
    }
    
    /**
     * Download the latest in-progress match (GLOBAL)
     */
    suspend fun downloadLatestInProgressMatch(): InProgressMatchEntity? {
        val querySnapshot = firestore
            .collection(FirebaseConfig.COLLECTION_IN_PROGRESS_MATCHES)
            .orderBy("lastSavedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()
        
        return querySnapshot.documents.firstOrNull()?.let { firestoreToInProgressMatch(it) }
    }
    
    /**
     * Download the latest in-progress match - legacy method for compatibility
     */
    suspend fun downloadLatestInProgressMatch(userId: String): InProgressMatchEntity? {
        return downloadLatestInProgressMatch()
    }
    
    /**
     * Download all in-progress matches (GLOBAL - returns all live matches)
     */
    suspend fun downloadAllInProgressMatches(): List<InProgressMatchEntity> {
        val querySnapshot = firestore
            .collection(FirebaseConfig.COLLECTION_IN_PROGRESS_MATCHES)
            .get()
            .await()
        
        return querySnapshot.documents.mapNotNull { doc ->
            try {
                firestoreToInProgressMatch(doc)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Download all in-progress matches - legacy method for compatibility
     */
    suspend fun downloadAllInProgressMatches(userId: String): List<InProgressMatchEntity> {
        return downloadAllInProgressMatches()
    }
    
    /**
     * Delete an in-progress match from Firestore
     */
    suspend fun deleteInProgressMatch(matchId: String) {
        firestore
            .collection(FirebaseConfig.COLLECTION_IN_PROGRESS_MATCHES)
            .document(matchId)
            .delete()
            .await()
    }
    
    /**
     * Delete an in-progress match - legacy method for compatibility
     */
    suspend fun deleteInProgressMatch(userId: String, matchId: String) {
        deleteInProgressMatch(matchId)
    }
    
    private fun firestoreToInProgressMatch(doc: DocumentSnapshot): InProgressMatchEntity {
        return InProgressMatchEntity(
            matchId = doc.getString("matchId") ?: doc.id,
            team1Name = doc.getString("team1Name") ?: "",
            team2Name = doc.getString("team2Name") ?: "",
            jokerName = doc.getString("jokerName") ?: "",
            groupId = doc.getString("groupId"),
            groupName = doc.getString("groupName"),
            tossWinner = doc.getString("tossWinner"),
            tossChoice = doc.getString("tossChoice"),
            matchSettingsJson = doc.getString("matchSettingsJson") ?: "",
            
            team1PlayerIds = doc.getString("team1PlayerIds") ?: "",
            team2PlayerIds = doc.getString("team2PlayerIds") ?: "",
            team1PlayerNames = doc.getString("team1PlayerNames") ?: "",
            team2PlayerNames = doc.getString("team2PlayerNames") ?: "",
            
            currentInnings = doc.getLong("currentInnings")?.toInt() ?: 1,
            currentOver = doc.getLong("currentOver")?.toInt() ?: 0,
            ballsInOver = doc.getLong("ballsInOver")?.toInt() ?: 0,
            totalWickets = doc.getLong("totalWickets")?.toInt() ?: 0,
            
            team1PlayersJson = doc.getString("team1PlayersJson") ?: "",
            team2PlayersJson = doc.getString("team2PlayersJson") ?: "",
            
            strikerIndex = doc.getLong("strikerIndex")?.toInt(),
            nonStrikerIndex = doc.getLong("nonStrikerIndex")?.toInt(),
            bowlerIndex = doc.getLong("bowlerIndex")?.toInt(),
            
            firstInningsRuns = doc.getLong("firstInningsRuns")?.toInt() ?: 0,
            firstInningsWickets = doc.getLong("firstInningsWickets")?.toInt() ?: 0,
            firstInningsOvers = doc.getLong("firstInningsOvers")?.toInt() ?: 0,
            firstInningsBalls = doc.getLong("firstInningsBalls")?.toInt() ?: 0,
            
            totalExtras = doc.getLong("totalExtras")?.toInt() ?: 0,
            calculatedTotalRuns = doc.getLong("calculatedTotalRuns")?.toInt() ?: 0,
            
            completedBattersInnings1Json = doc.getString("completedBattersInnings1Json"),
            completedBattersInnings2Json = doc.getString("completedBattersInnings2Json"),
            completedBowlersInnings1Json = doc.getString("completedBowlersInnings1Json"),
            completedBowlersInnings2Json = doc.getString("completedBowlersInnings2Json"),
            
            firstInningsBattingPlayersJson = doc.getString("firstInningsBattingPlayersJson"),
            firstInningsBowlingPlayersJson = doc.getString("firstInningsBowlingPlayersJson"),
            
            jokerOutInCurrentInnings = doc.getBoolean("jokerOutInCurrentInnings") ?: false,
            jokerBallsBowledInnings1 = doc.getLong("jokerBallsBowledInnings1")?.toInt() ?: 0,
            jokerBallsBowledInnings2 = doc.getLong("jokerBallsBowledInnings2")?.toInt() ?: 0,
            
            powerplayRunsInnings1 = doc.getLong("powerplayRunsInnings1")?.toInt() ?: 0,
            powerplayRunsInnings2 = doc.getLong("powerplayRunsInnings2")?.toInt() ?: 0,
            powerplayDoublingDoneInnings1 = doc.getBoolean("powerplayDoublingDoneInnings1") ?: false,
            powerplayDoublingDoneInnings2 = doc.getBoolean("powerplayDoublingDoneInnings2") ?: false,
            
            allDeliveriesJson = doc.getString("allDeliveriesJson"),
            
            lastSavedAt = doc.getLong("lastSavedAt") ?: System.currentTimeMillis(),
            startedAt = doc.getLong("startedAt") ?: System.currentTimeMillis()
        )
    }
}
