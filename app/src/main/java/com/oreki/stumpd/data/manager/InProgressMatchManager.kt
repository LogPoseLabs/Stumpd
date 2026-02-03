package com.oreki.stumpd.data.manager

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.oreki.stumpd.data.local.db.StumpdDb
import com.oreki.stumpd.data.local.entity.InProgressMatchEntity
import com.oreki.stumpd.data.models.MatchInProgress
import com.oreki.stumpd.data.util.GsonProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages saving and loading of in-progress matches
 * Uses Room database for robust persistence and data integrity
 */
class InProgressMatchManager(context: Context) {
    
    private val db = StumpdDb.get(context)
    private val dao = db.inProgressMatchDao()
    private val gson: Gson = GsonProvider.get()
    
    companion object {
        private const val TAG = "InProgressMatchManager"
    }
    
    /**
     * Save the current match state
     */
    suspend fun saveMatch(match: MatchInProgress) {
        return withContext(Dispatchers.IO) {
            try {
                val entity = match.toEntity()
                dao.upsert(entity)
                Log.d(TAG, "Match saved successfully to DB: ${match.matchId}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save match to DB", e)
            }
        }
    }
    
    /**
     * Load the latest in-progress match
     */
    suspend fun loadMatch(): MatchInProgress? {
        return withContext(Dispatchers.IO) {
            try {
                val entity = dao.getLatest()
                if (entity == null) {
                    Log.d(TAG, "No in-progress match found in DB")
                    return@withContext null
                }
                
                val match = entity.toDomain()
                Log.d(TAG, "Match loaded successfully from DB: ${match.matchId}")
                match
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load match from DB", e)
                null
            }
        }
    }
    
    /**
     * Check if there's an in-progress match
     */
    suspend fun hasInProgressMatch(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                dao.count() > 0
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check for in-progress match", e)
                false
            }
        }
    }
    
    /**
     * Clear the saved match (call when match is completed or abandoned)
     */
    suspend fun clearMatch() {
        return withContext(Dispatchers.IO) {
            try {
                dao.deleteAll()
                Log.d(TAG, "All in-progress matches cleared from DB")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear matches from DB", e)
            }
        }
    }
    
    /**
     * Clear a specific match by ID
     */
    suspend fun clearMatch(matchId: String) {
        return withContext(Dispatchers.IO) {
            try {
                dao.delete(matchId)
                Log.d(TAG, "Match cleared from DB: $matchId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear match from DB", e)
            }
        }
    }
    
    // Extension functions for converting between domain and entity models
    private fun MatchInProgress.toEntity(): InProgressMatchEntity {
        return InProgressMatchEntity(
            matchId = matchId,
            team1Name = team1Name,
            team2Name = team2Name,
            jokerName = jokerName,
            groupId = groupId,
            groupName = groupName,
            tossWinner = tossWinner,
            tossChoice = tossChoice,
            matchSettingsJson = matchSettingsJson,
            team1PlayerIds = gson.toJson(team1PlayerIds),
            team2PlayerIds = gson.toJson(team2PlayerIds),
            team1PlayerNames = gson.toJson(team1PlayerNames),
            team2PlayerNames = gson.toJson(team2PlayerNames),
            currentInnings = currentInnings,
            currentOver = currentOver,
            ballsInOver = ballsInOver,
            totalWickets = totalWickets,
            team1PlayersJson = team1PlayersJson,
            team2PlayersJson = team2PlayersJson,
            strikerIndex = strikerIndex,
            nonStrikerIndex = nonStrikerIndex,
            bowlerIndex = bowlerIndex,
            firstInningsRuns = firstInningsRuns,
            firstInningsWickets = firstInningsWickets,
            firstInningsOvers = firstInningsOvers,
            firstInningsBalls = firstInningsBalls,
            totalExtras = totalExtras,
            calculatedTotalRuns = calculatedTotalRuns,
            completedBattersInnings1Json = completedBattersInnings1Json,
            completedBattersInnings2Json = completedBattersInnings2Json,
            completedBowlersInnings1Json = completedBowlersInnings1Json,
            completedBowlersInnings2Json = completedBowlersInnings2Json,
            firstInningsBattingPlayersJson = firstInningsBattingPlayersJson,
            firstInningsBowlingPlayersJson = firstInningsBowlingPlayersJson,
            jokerOutInCurrentInnings = jokerOutInCurrentInnings,
            jokerBallsBowledInnings1 = jokerBallsBowledInnings1,
            jokerBallsBowledInnings2 = jokerBallsBowledInnings2,
            powerplayRunsInnings1 = powerplayRunsInnings1,
            powerplayRunsInnings2 = powerplayRunsInnings2,
            powerplayDoublingDoneInnings1 = powerplayDoublingDoneInnings1,
            powerplayDoublingDoneInnings2 = powerplayDoublingDoneInnings2,
            allDeliveriesJson = allDeliveriesJson,
            lastSavedAt = lastSavedAt,
            startedAt = startedAt
        )
    }
    
    private fun InProgressMatchEntity.toDomain(): MatchInProgress {
        return MatchInProgress(
            matchId = matchId,
            team1Name = team1Name,
            team2Name = team2Name,
            jokerName = jokerName,
            groupId = groupId,
            groupName = groupName,
            tossWinner = tossWinner,
            tossChoice = tossChoice,
            matchSettingsJson = matchSettingsJson,
            team1PlayerIds = gson.fromJson(team1PlayerIds, Array<String>::class.java).toList(),
            team2PlayerIds = gson.fromJson(team2PlayerIds, Array<String>::class.java).toList(),
            team1PlayerNames = gson.fromJson(team1PlayerNames, Array<String>::class.java).toList(),
            team2PlayerNames = gson.fromJson(team2PlayerNames, Array<String>::class.java).toList(),
            currentInnings = currentInnings,
            currentOver = currentOver,
            ballsInOver = ballsInOver,
            totalWickets = totalWickets,
            team1PlayersJson = team1PlayersJson,
            team2PlayersJson = team2PlayersJson,
            strikerIndex = strikerIndex,
            nonStrikerIndex = nonStrikerIndex,
            bowlerIndex = bowlerIndex,
            firstInningsRuns = firstInningsRuns,
            firstInningsWickets = firstInningsWickets,
            firstInningsOvers = firstInningsOvers,
            firstInningsBalls = firstInningsBalls,
            totalExtras = totalExtras,
            calculatedTotalRuns = calculatedTotalRuns,
            completedBattersInnings1Json = completedBattersInnings1Json,
            completedBattersInnings2Json = completedBattersInnings2Json,
            completedBowlersInnings1Json = completedBowlersInnings1Json,
            completedBowlersInnings2Json = completedBowlersInnings2Json,
            firstInningsBattingPlayersJson = firstInningsBattingPlayersJson,
            firstInningsBowlingPlayersJson = firstInningsBowlingPlayersJson,
            jokerOutInCurrentInnings = jokerOutInCurrentInnings,
            jokerBallsBowledInnings1 = jokerBallsBowledInnings1,
            jokerBallsBowledInnings2 = jokerBallsBowledInnings2,
            powerplayRunsInnings1 = powerplayRunsInnings1,
            powerplayRunsInnings2 = powerplayRunsInnings2,
            powerplayDoublingDoneInnings1 = powerplayDoublingDoneInnings1,
            powerplayDoublingDoneInnings2 = powerplayDoublingDoneInnings2,
            allDeliveriesJson = allDeliveriesJson,
            lastSavedAt = lastSavedAt,
            startedAt = startedAt
        )
    }
}

