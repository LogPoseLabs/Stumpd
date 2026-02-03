package com.oreki.stumpd.data.repository

import android.util.Log
import com.oreki.stumpd.MatchHistory
import com.oreki.stumpd.MatchPerformance
import com.oreki.stumpd.PlayerDetailedStats
import com.oreki.stumpd.PlayerMatchStats
import com.oreki.stumpd.data.local.db.StumpdDb
import com.oreki.stumpd.data.local.entity.PlayerEntity
import com.oreki.stumpd.data.local.entity.PlayerMatchStatsEntity
import com.oreki.stumpd.data.mappers.oversToBalls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Repository for managing player data and statistics
 * Handles all database operations related to players and their performance stats
 */
class PlayerRepository(private val db: StumpdDb) {
    
    private companion object {
        const val TAG = "PlayerRepository"
    }

    /**
     * Retrieves all players from the database
     * @return List of all player entities
     */
    suspend fun getAllPlayers(): List<PlayerEntity> = withContext(Dispatchers.IO) {
        try {
            db.playerDao().list()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all players", e)
            emptyList()
        }
    }

    /**
     * Adds a new player or updates an existing one
     * @param name The player's name
     * @param existingPlayerId Optional ID for updating an existing player
     * @return The created/updated player entity
     */
    suspend fun addOrUpdatePlayer(name: String, existingPlayerId: String? = null): PlayerEntity = withContext(Dispatchers.IO) {
        try {
            val id = existingPlayerId ?: UUID.randomUUID().toString()
            val playerEntity = PlayerEntity(id = id, name = name.trim(), isJoker = false)
            db.playerDao().upsert(listOf(playerEntity))
            
            // If updating an existing player, sync the name across all historical match stats
            if (existingPlayerId != null) {
                val updatedRows = db.matchDao().updatePlayerNameInStats(existingPlayerId, name.trim())
                Log.d(TAG, "Updated player name in $updatedRows historical match stats")
            }
            
            Log.d(TAG, "Added/updated player: $name (id: $id)")
            playerEntity
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add/update player: $name", e)
            throw e
        }
    }

    /**
     * Deletes a player from the database
     * Note: This will also cascade delete all related data (group memberships, match stats, etc.)
     * @param playerId The ID of the player to delete
     */
    suspend fun deletePlayer(playerId: String) = withContext(Dispatchers.IO) {
        try {
            // Remove from all group memberships
            db.groupDao().removePlayerFromAllGroups(playerId)
            
            // Remove from all unavailable lists
            db.groupDao().removePlayerFromUnavailableLists(playerId)
            
            // Delete the player entity (note: match stats remain for historical accuracy)
            db.playerDao().delete(playerId)
            
            Log.d(TAG, "Deleted player and removed from all groups: $playerId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete player: $playerId", e)
            throw e
        }
    }

    /**
     * Computes detailed statistics for players from match data
     * @param matches List of matches to analyze
     * @param playerId Optional player ID to filter for a specific player
     * @return List of detailed player statistics
     */
    suspend fun getPlayerDetailedStats(
        matches: List<MatchHistory>,
        playerId: String? = null
    ): List<PlayerDetailedStats> = withContext(Dispatchers.IO) {
        try {
            val playerStatsMap = mutableMapOf<String, PlayerDetailedStats>()

            matches.forEach { match ->
                val matchStats = getMatchStats(match)
                matchStats.forEach { statsEntity ->
                    processPlayerStats(statsEntity, match, playerStatsMap)
                }
                
                // Calculate extras from deliveries (wrapped in try-catch to not break entire stats)
                try {
                    calculateExtrasFromDeliveries(match, playerStatsMap)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to calculate extras for match ${match.id}", e)
                }
            }

            return@withContext if (playerId != null) {
                listOfNotNull(playerStatsMap[playerId])
            } else {
                playerStatsMap.values.toList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get player detailed stats", e)
            emptyList()
        }
    }
    
    /**
     * Calculates extras (wides, no-balls) from delivery data
     */
    private fun calculateExtrasFromDeliveries(
        match: MatchHistory,
        playerStatsMap: MutableMap<String, PlayerDetailedStats>
    ) {
        if (match.allDeliveries.isEmpty()) return
        
        match.allDeliveries.forEach { delivery ->
            try {
                // Safely handle null or blank bowler names (can be null from old JSON data)
                val bowlerName = delivery.bowlerName
                if (bowlerName == null || bowlerName.isBlank()) return@forEach
                
                val playerKey = bowlerName.lowercase().trim()
                playerStatsMap[playerKey]?.let { stats ->
                    when {
                        delivery.outcome.startsWith("Wd") -> stats.totalWides++
                        delivery.outcome.startsWith("Nb") -> stats.totalNoBalls++
                    }
                }
            } catch (e: Exception) {
                // Skip this delivery if there's any issue
                Log.w(TAG, "Skipping extras calculation for delivery: ${e.message}")
            }
        }
    }

    /**
     * Gets match stats from either the database or existing match data
     */
    private suspend fun getMatchStats(match: MatchHistory): List<PlayerMatchStatsEntity> {
        return if (match.firstInningsBatting.isEmpty()) {
            val dbStats = db.matchDao().statsForMatch(match.id)
            if (dbStats.isEmpty()) {
                Log.d(TAG, "No stats found in DB for match ${match.id} (${match.team1Name} vs ${match.team2Name})")
            }
            dbStats
        } else {
            convertMatchStatsToEntities(match)
        }
    }

    /**
     * Converts match stats from domain to entity format
     */
    private fun convertMatchStatsToEntities(match: MatchHistory): List<PlayerMatchStatsEntity> {
        val allStats = match.firstInningsBatting + 
                      match.firstInningsBowling + 
                      match.secondInningsBatting + 
                      match.secondInningsBowling
        
        return allStats.map { stat ->
            PlayerMatchStatsEntity(
                matchId = match.id,
                playerId = stat.id,
                name = stat.name,
                team = stat.team,
                runs = stat.runs,
                ballsFaced = stat.ballsFaced,
                dots = stat.dots,
                singles = stat.singles,
                twos = stat.twos,
                threes = stat.threes,
                fours = stat.fours,
                sixes = stat.sixes,
                wickets = stat.wickets,
                runsConceded = stat.runsConceded,
                oversBowled = stat.oversBowled,
                maidenOvers = stat.maidenOvers,
                isOut = stat.isOut,
                isRetired = stat.isRetired,
                isJoker = stat.isJoker,
                catches = stat.catches,
                runOuts = stat.runOuts,
                stumpings = stat.stumpings,
                dismissalType = stat.dismissalType,
                bowlerName = stat.bowlerName,
                fielderName = stat.fielderName
            )
        }
    }

    /**
     * Processes stats for a single player in a match
     */
    private fun processPlayerStats(
        statsEntity: PlayerMatchStatsEntity,
        match: MatchHistory,
        playerStatsMap: MutableMap<String, PlayerDetailedStats>
    ) {
        val playerKey = statsEntity.name.lowercase().trim()
        val detailedStats = playerStatsMap.getOrPut(playerKey) {
            PlayerDetailedStats(
                playerId = playerKey,
                name = statsEntity.name
            )
        }

        val existingPerformance = findExistingPerformance(detailedStats, match.id, statsEntity)

        if (existingPerformance == null) {
            addNewPerformance(detailedStats, statsEntity, match)
        } else {
            mergeExistingPerformance(detailedStats, statsEntity, existingPerformance)
        }
    }

    /**
     * Finds existing performance for a player in a match
     */
    private fun findExistingPerformance(
        stats: PlayerDetailedStats,
        matchId: String,
        entity: PlayerMatchStatsEntity
    ): MatchPerformance? {
        return stats.matchPerformances.find {
            if (entity.isJoker) {
                it.matchId == matchId && it.myTeam == entity.team
            } else {
                it.matchId == matchId
            }
        }
    }

    /**
     * Adds a new performance record for a player
     */
    private fun addNewPerformance(
        stats: PlayerDetailedStats,
        entity: PlayerMatchStatsEntity,
        match: MatchHistory
    ) {
        // Update career totals
        stats.totalRuns += entity.runs
        stats.totalBallsFaced += entity.ballsFaced
        stats.totalDots += entity.dots
        stats.totalSingles += entity.singles
        stats.totalTwos += entity.twos
        stats.totalThrees += entity.threes
        stats.totalFours += entity.fours
        stats.totalSixes += entity.sixes
        stats.totalWickets += entity.wickets
        stats.totalRunsConceded += entity.runsConceded
        stats.totalBallsBowled += entity.oversBowled.oversToBalls()
        stats.totalMaidenOvers += entity.maidenOvers
        stats.totalCatches += entity.catches
        stats.totalRunOuts += entity.runOuts
        stats.totalStumpings += entity.stumpings

        if (entity.isOut) stats.timesOut++ else stats.notOuts++
        
        // Track highest score
        if (entity.runs > stats.highestScore) {
            stats.highestScore = entity.runs
        }
        
        // Track best bowling
        if (entity.wickets > 0) {
            if (entity.wickets > stats.bestBowlingWickets || 
                (entity.wickets == stats.bestBowlingWickets && entity.runsConceded < stats.bestBowlingRuns)) {
                stats.bestBowlingWickets = entity.wickets
                stats.bestBowlingRuns = entity.runsConceded
            }
        }

        // Increment match count if this is the first performance for this match
        if (stats.matchPerformances.none { it.matchId == match.id }) {
            stats.totalMatches++
        }

        // Add the performance
        stats.matchPerformances.add(
            MatchPerformance(
                matchId = match.id,
                matchDate = match.matchDate,
                opposingTeam = if (entity.team == match.team1Name) match.team2Name else match.team1Name,
                myTeam = entity.team,
                runs = entity.runs,
                ballsFaced = entity.ballsFaced,
                fours = entity.fours,
                sixes = entity.sixes,
                isOut = entity.isOut,
                wickets = entity.wickets,
                runsConceded = entity.runsConceded,
                ballsBowled = entity.oversBowled.oversToBalls(),
                catches = entity.catches,
                runOuts = entity.runOuts,
                stumpings = entity.stumpings,
                isWinner = match.winnerTeam == entity.team,
                isJoker = entity.isJoker,
                isShortPitch = match.shortPitch,
                groupId = match.groupId
            )
        )
    }

    /**
     * Merges stats into an existing performance record
     */
    private fun mergeExistingPerformance(
        stats: PlayerDetailedStats,
        entity: PlayerMatchStatsEntity,
        existing: MatchPerformance
    ) {
        // Update career totals
        stats.totalRuns += entity.runs
        stats.totalBallsFaced += entity.ballsFaced
        stats.totalDots += entity.dots
        stats.totalSingles += entity.singles
        stats.totalTwos += entity.twos
        stats.totalThrees += entity.threes
        stats.totalFours += entity.fours
        stats.totalSixes += entity.sixes
        stats.totalWickets += entity.wickets
        stats.totalRunsConceded += entity.runsConceded
        stats.totalBallsBowled += entity.oversBowled.oversToBalls()
        stats.totalMaidenOvers += entity.maidenOvers
        stats.totalCatches += entity.catches
        stats.totalRunOuts += entity.runOuts
        stats.totalStumpings += entity.stumpings

        if (entity.isOut && !existing.isOut) stats.timesOut++
        
        // Update highest score
        val totalRunsForPlayer = existing.runs + entity.runs
        if (totalRunsForPlayer > stats.highestScore) {
            stats.highestScore = totalRunsForPlayer
        }
        
        // Update best bowling
        val totalWicketsForPlayer = existing.wickets + entity.wickets
        val totalRunsConcededForPlayer = existing.runsConceded + entity.runsConceded
        if (totalWicketsForPlayer > 0) {
            if (totalWicketsForPlayer > stats.bestBowlingWickets || 
                (totalWicketsForPlayer == stats.bestBowlingWickets && totalRunsConcededForPlayer < stats.bestBowlingRuns)) {
                stats.bestBowlingWickets = totalWicketsForPlayer
                stats.bestBowlingRuns = totalRunsConcededForPlayer
            }
        }

        // Update the existing performance
        val index = stats.matchPerformances.indexOf(existing)
        stats.matchPerformances[index] = existing.copy(
            runs = existing.runs + entity.runs,
            ballsFaced = existing.ballsFaced + entity.ballsFaced,
            fours = existing.fours + entity.fours,
            sixes = existing.sixes + entity.sixes,
            wickets = existing.wickets + entity.wickets,
            runsConceded = existing.runsConceded + entity.runsConceded,
            ballsBowled = existing.ballsBowled + entity.oversBowled.oversToBalls(),
            catches = existing.catches + entity.catches,
            runOuts = existing.runOuts + entity.runOuts,
            stumpings = existing.stumpings + entity.stumpings,
            isOut = existing.isOut || entity.isOut
        )
    }

}
