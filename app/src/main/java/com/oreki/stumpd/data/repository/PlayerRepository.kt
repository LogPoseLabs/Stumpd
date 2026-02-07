package com.oreki.stumpd.data.repository

import com.oreki.stumpd.data.manager.*
import com.oreki.stumpd.domain.model.*
import android.util.Log
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
                // Safely handle blank bowler names
                val bowlerName = delivery.bowlerName
                if (bowlerName.isBlank()) return@forEach
                
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
        fun toEntity(stat: PlayerMatchStats, matchId: String, role: String): PlayerMatchStatsEntity {
            return PlayerMatchStatsEntity(
                matchId = matchId,
                playerId = stat.id,
                name = stat.name,
                team = stat.team,
                role = role,
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
                fielderName = stat.fielderName,
                battingPosition = stat.battingPosition,
                bowlingPosition = stat.bowlingPosition
            )
        }

        val entities = mutableListOf<PlayerMatchStatsEntity>()
        match.firstInningsBatting.forEach { entities.add(toEntity(it, match.id, "BAT")) }
        match.secondInningsBatting.forEach { entities.add(toEntity(it, match.id, "BAT")) }
        match.firstInningsBowling.forEach { entities.add(toEntity(it, match.id, "BOWL")) }
        match.secondInningsBowling.forEach { entities.add(toEntity(it, match.id, "BOWL")) }
        return entities
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
     * Adds a new performance record for a player.
     * Role-aware: only counts batting stats from BAT entities and bowling stats from BOWL entities
     * to prevent double-counting when Player objects accumulate stats across innings.
     */
    private fun addNewPerformance(
        stats: PlayerDetailedStats,
        entity: PlayerMatchStatsEntity,
        match: MatchHistory
    ) {
        val isBat = entity.role == "BAT"
        val isBowl = entity.role == "BOWL"

        // Update career totals — only count stats relevant to the entity's role
        if (isBat) {
            stats.totalRuns += entity.runs
            stats.totalBallsFaced += entity.ballsFaced
            stats.totalDots += entity.dots
            stats.totalSingles += entity.singles
            stats.totalTwos += entity.twos
            stats.totalThrees += entity.threes
            stats.totalFours += entity.fours
            stats.totalSixes += entity.sixes
            if (entity.isOut) stats.timesOut++ else stats.notOuts++
            if (entity.runs > stats.highestScore) {
                stats.highestScore = entity.runs
            }
        }
        if (isBowl) {
            stats.totalWickets += entity.wickets
            stats.totalRunsConceded += entity.runsConceded
            stats.totalBallsBowled += entity.oversBowled.oversToBalls()
            stats.totalMaidenOvers += entity.maidenOvers
            if (entity.wickets > 0) {
                if (entity.wickets > stats.bestBowlingWickets ||
                    (entity.wickets == stats.bestBowlingWickets && entity.runsConceded < stats.bestBowlingRuns)) {
                    stats.bestBowlingWickets = entity.wickets
                    stats.bestBowlingRuns = entity.runsConceded
                }
            }
        }
        // Fielding stats — count from whichever role has them (typically BOWL = fielding team)
        stats.totalCatches += entity.catches
        stats.totalRunOuts += entity.runOuts
        stats.totalStumpings += entity.stumpings

        // Increment match count if this is the first performance for this match
        if (stats.matchPerformances.none { it.matchId == match.id }) {
            stats.totalMatches++
        }

        // Add the performance — store only role-appropriate stats
        stats.matchPerformances.add(
            MatchPerformance(
                matchId = match.id,
                matchDate = match.matchDate,
                opposingTeam = if (entity.team == match.team1Name) match.team2Name else match.team1Name,
                myTeam = entity.team,
                runs = if (isBat) entity.runs else 0,
                ballsFaced = if (isBat) entity.ballsFaced else 0,
                fours = if (isBat) entity.fours else 0,
                sixes = if (isBat) entity.sixes else 0,
                isOut = if (isBat) entity.isOut else false,
                wickets = if (isBowl) entity.wickets else 0,
                runsConceded = if (isBowl) entity.runsConceded else 0,
                ballsBowled = if (isBowl) entity.oversBowled.oversToBalls() else 0,
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
     * Merges stats into an existing performance record.
     * Role-aware: only merges stats appropriate to the entity's role.
     */
    private fun mergeExistingPerformance(
        stats: PlayerDetailedStats,
        entity: PlayerMatchStatsEntity,
        existing: MatchPerformance
    ) {
        val isBat = entity.role == "BAT"
        val isBowl = entity.role == "BOWL"

        // Update career totals — only count stats relevant to this role
        if (isBat) {
            stats.totalRuns += entity.runs
            stats.totalBallsFaced += entity.ballsFaced
            stats.totalDots += entity.dots
            stats.totalSingles += entity.singles
            stats.totalTwos += entity.twos
            stats.totalThrees += entity.threes
            stats.totalFours += entity.fours
            stats.totalSixes += entity.sixes
            if (entity.isOut && !existing.isOut) stats.timesOut++
            val newRuns = existing.runs + entity.runs
            if (newRuns > stats.highestScore) {
                stats.highestScore = newRuns
            }
        }
        if (isBowl) {
            stats.totalWickets += entity.wickets
            stats.totalRunsConceded += entity.runsConceded
            stats.totalBallsBowled += entity.oversBowled.oversToBalls()
            stats.totalMaidenOvers += entity.maidenOvers
            val totalWicketsForPlayer = existing.wickets + entity.wickets
            val totalRunsConcededForPlayer = existing.runsConceded + entity.runsConceded
            if (totalWicketsForPlayer > 0) {
                if (totalWicketsForPlayer > stats.bestBowlingWickets ||
                    (totalWicketsForPlayer == stats.bestBowlingWickets && totalRunsConcededForPlayer < stats.bestBowlingRuns)) {
                    stats.bestBowlingWickets = totalWicketsForPlayer
                    stats.bestBowlingRuns = totalRunsConcededForPlayer
                }
            }
        }
        // Fielding: only add if existing doesn't already have them (avoid doubling from corrupted data)
        if (existing.catches == 0) stats.totalCatches += entity.catches
        if (existing.runOuts == 0) stats.totalRunOuts += entity.runOuts
        if (existing.stumpings == 0) stats.totalStumpings += entity.stumpings

        // Update the existing performance — merge only role-appropriate stats
        val index = stats.matchPerformances.indexOf(existing)
        stats.matchPerformances[index] = existing.copy(
            runs = existing.runs + (if (isBat) entity.runs else 0),
            ballsFaced = existing.ballsFaced + (if (isBat) entity.ballsFaced else 0),
            fours = existing.fours + (if (isBat) entity.fours else 0),
            sixes = existing.sixes + (if (isBat) entity.sixes else 0),
            wickets = existing.wickets + (if (isBowl) entity.wickets else 0),
            runsConceded = existing.runsConceded + (if (isBowl) entity.runsConceded else 0),
            ballsBowled = existing.ballsBowled + (if (isBowl) entity.oversBowled.oversToBalls() else 0),
            catches = if (existing.catches == 0) entity.catches else existing.catches,
            runOuts = if (existing.runOuts == 0) entity.runOuts else existing.runOuts,
            stumpings = if (existing.stumpings == 0) entity.stumpings else existing.stumpings,
            isOut = existing.isOut || (isBat && entity.isOut)
        )
    }

}
