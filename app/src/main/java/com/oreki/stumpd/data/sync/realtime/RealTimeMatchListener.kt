package com.oreki.stumpd.data.sync.realtime

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.data.local.entity.InProgressMatchEntity
import com.oreki.stumpd.data.sync.FirebaseConfig
import com.oreki.stumpd.data.sync.firebase.FirestoreMatchDao
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Real-time match updates using Firestore listeners
 * 
 * DATA IS GLOBAL - All users can listen to all matches
 * 
 * Enables live score updates across devices:
 * - Score a run on Device 1 → Updates on Device 2 in 1-2 seconds
 * - See wickets, partnerships, deliveries in real-time
 * - Perfect for multi-device scoring or spectator mode
 */
class RealTimeMatchListener(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val matchDao: FirestoreMatchDao = FirestoreMatchDao()
) {
    
    companion object {
        private const val TAG = "RealTimeMatchListener"
    }
    
    /**
     * Listen to a specific IN-PROGRESS match for real-time updates (GLOBAL)
     * Returns a Flow that emits InProgressMatchEntity whenever it changes
     * 
     * For spectator mode - listens to live matches
     * @param userId Legacy parameter - ignored (kept for compatibility)
     */
    fun listenToInProgressMatch(userId: String, matchId: String): Flow<InProgressMatchEntity?> = callbackFlow {
        Log.d(TAG, "Starting real-time listener for in-progress match: $matchId")
        
        val docRef = firestore
            .collection(FirebaseConfig.COLLECTION_IN_PROGRESS_MATCHES)
            .document(matchId)
        
        // Listen to match document changes
        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Listen failed for in-progress match: $matchId", error)
                trySend(null)
                return@addSnapshotListener
            }
            
            if (snapshot != null && snapshot.exists()) {
                Log.d(TAG, "In-progress match updated: $matchId")
                try {
                    // Manually parse Firestore document to InProgressMatchEntity
                    val data = snapshot.data ?: run {
                        Log.e(TAG, "No data in snapshot")
                        trySend(null)
                        return@addSnapshotListener
                    }
                    
                    val matchEntity = InProgressMatchEntity(
                        matchId = data["matchId"] as? String ?: matchId,
                        team1Name = data["team1Name"] as? String ?: "",
                        team2Name = data["team2Name"] as? String ?: "",
                        jokerName = data["jokerName"] as? String ?: "",
                        groupId = data["groupId"] as? String?,
                        groupName = data["groupName"] as? String?,
                        tossWinner = data["tossWinner"] as? String?,
                        tossChoice = data["tossChoice"] as? String?,
                        matchSettingsJson = data["matchSettingsJson"] as? String ?: "{}",
                        team1PlayerIds = data["team1PlayerIds"] as? String ?: "[]",
                        team2PlayerIds = data["team2PlayerIds"] as? String ?: "[]",
                        team1PlayerNames = data["team1PlayerNames"] as? String ?: "[]",
                        team2PlayerNames = data["team2PlayerNames"] as? String ?: "[]",
                        currentInnings = (data["currentInnings"] as? Long)?.toInt() ?: 1,
                        currentOver = (data["currentOver"] as? Long)?.toInt() ?: 0,
                        ballsInOver = (data["ballsInOver"] as? Long)?.toInt() ?: 0,
                        totalWickets = (data["totalWickets"] as? Long)?.toInt() ?: 0,
                        team1PlayersJson = data["team1PlayersJson"] as? String ?: "[]",
                        team2PlayersJson = data["team2PlayersJson"] as? String ?: "[]",
                        strikerIndex = (data["strikerIndex"] as? Long)?.toInt(),
                        nonStrikerIndex = (data["nonStrikerIndex"] as? Long)?.toInt(),
                        bowlerIndex = (data["bowlerIndex"] as? Long)?.toInt(),
                        firstInningsRuns = (data["firstInningsRuns"] as? Long)?.toInt() ?: 0,
                        firstInningsWickets = (data["firstInningsWickets"] as? Long)?.toInt() ?: 0,
                        firstInningsOvers = (data["firstInningsOvers"] as? Long)?.toInt() ?: 0,
                        firstInningsBalls = (data["firstInningsBalls"] as? Long)?.toInt() ?: 0,
                        totalExtras = (data["totalExtras"] as? Long)?.toInt() ?: 0,
                        calculatedTotalRuns = (data["calculatedTotalRuns"] as? Long)?.toInt() ?: 0,
                        completedBattersInnings1Json = data["completedBattersInnings1Json"] as? String?,
                        completedBattersInnings2Json = data["completedBattersInnings2Json"] as? String?,
                        completedBowlersInnings1Json = data["completedBowlersInnings1Json"] as? String?,
                        completedBowlersInnings2Json = data["completedBowlersInnings2Json"] as? String?,
                        firstInningsBattingPlayersJson = data["firstInningsBattingPlayersJson"] as? String?,
                        firstInningsBowlingPlayersJson = data["firstInningsBowlingPlayersJson"] as? String?,
                        jokerOutInCurrentInnings = data["jokerOutInCurrentInnings"] as? Boolean ?: false,
                        jokerBallsBowledInnings1 = (data["jokerBallsBowledInnings1"] as? Long)?.toInt() ?: 0,
                        jokerBallsBowledInnings2 = (data["jokerBallsBowledInnings2"] as? Long)?.toInt() ?: 0,
                        powerplayRunsInnings1 = (data["powerplayRunsInnings1"] as? Long)?.toInt() ?: 0,
                        powerplayRunsInnings2 = (data["powerplayRunsInnings2"] as? Long)?.toInt() ?: 0,
                        powerplayDoublingDoneInnings1 = data["powerplayDoublingDoneInnings1"] as? Boolean ?: false,
                        powerplayDoublingDoneInnings2 = data["powerplayDoublingDoneInnings2"] as? Boolean ?: false,
                        allDeliveriesJson = data["allDeliveriesJson"] as? String?,
                        lastSavedAt = data["lastSavedAt"] as? Long ?: System.currentTimeMillis(),
                        startedAt = data["startedAt"] as? Long ?: System.currentTimeMillis()
                    )
                    
                    trySend(matchEntity)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse in-progress match", e)
                    trySend(null)
                }
            } else {
                Log.d(TAG, "In-progress match not found: $matchId")
                trySend(null)
            }
        }
        
        // Clean up listener when Flow is cancelled
        awaitClose {
            Log.d(TAG, "Stopping real-time listener for in-progress match: $matchId")
            registration.remove()
        }
    }
    
    /**
     * Listen to a specific COMPLETED match for real-time updates (GLOBAL)
     * Returns a Flow that emits MatchHistory whenever it changes
     * 
     * For viewing match history
     * @param userId Legacy parameter - ignored (kept for compatibility)
     */
    fun listenToMatch(userId: String, matchId: String): Flow<MatchHistory?> = callbackFlow {
        Log.d(TAG, "Starting real-time listener for match: $matchId")
        
        val docRef = firestore
            .collection(FirebaseConfig.COLLECTION_MATCHES)
            .document(matchId)
        
        // Listen to match document changes
        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Listen failed for match: $matchId", error)
                trySend(null)
                return@addSnapshotListener
            }
            
            if (snapshot != null && snapshot.exists()) {
                Log.d(TAG, "Match updated: $matchId")
                // Fetch complete match with all subcollections
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val completeMatch = matchDao.downloadCompleteMatch(matchId)
                        trySend(completeMatch)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch complete match", e)
                        trySend(null)
                    }
                }
            } else {
                Log.d(TAG, "Match not found: $matchId")
                trySend(null)
            }
        }
        
        // Clean up listener when Flow is cancelled
        awaitClose {
            Log.d(TAG, "Stopping real-time listener for match: $matchId")
            registration.remove()
        }
    }
    
    /**
     * Listen to all matches (GLOBAL - returns all matches from all users)
     * Returns a Flow that emits list of match IDs whenever they change
     * @param userId Legacy parameter - ignored (kept for compatibility)
     */
    fun listenToAllMatches(userId: String): Flow<List<String>> = callbackFlow {
        Log.d(TAG, "Starting real-time listener for all matches")
        
        val collectionRef = firestore
            .collection(FirebaseConfig.COLLECTION_MATCHES)
        
        val registration = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Listen failed for matches collection", error)
                trySend(emptyList())
                return@addSnapshotListener
            }
            
            if (snapshot != null) {
                val matchIds = snapshot.documents.mapNotNull { it.id }
                Log.d(TAG, "Matches updated: ${matchIds.size} matches")
                trySend(matchIds)
            } else {
                trySend(emptyList())
            }
        }
        
        awaitClose {
            Log.d(TAG, "Stopping real-time listener for all matches")
            registration.remove()
        }
    }
    
    /**
     * Listen to in-progress matches for live scoring (GLOBAL)
     * Perfect for spectator mode or multiple scorers
     * @param userId Legacy parameter - ignored (kept for compatibility)
     */
    fun listenToInProgressMatches(userId: String): Flow<List<String>> = callbackFlow {
        Log.d(TAG, "Starting real-time listener for in-progress matches")
        
        val collectionRef = firestore
            .collection(FirebaseConfig.COLLECTION_IN_PROGRESS_MATCHES)
        
        val registration = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Listen failed for in-progress matches", error)
                trySend(emptyList())
                return@addSnapshotListener
            }
            
            if (snapshot != null) {
                val matchIds = snapshot.documents.mapNotNull { it.getString("matchId") }
                Log.d(TAG, "In-progress matches updated: ${matchIds.size} matches")
                trySend(matchIds)
            } else {
                trySend(emptyList())
            }
        }
        
        awaitClose {
            Log.d(TAG, "Stopping real-time listener for in-progress matches")
            registration.remove()
        }
    }
}
