package com.oreki.stumpd

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

// Basic stored player for simple operations
data class StoredPlayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    var matchesPlayed: Int = 0,
    var totalRuns: Int = 0,
    var totalWickets: Int = 0,
    var lastPlayed: Long = System.currentTimeMillis(),
)

// Detailed player statistics for comprehensive analysis
data class PlayerDetailedStats(
    val playerId: String,
    val name: String,
    // Overall career stats
    var totalMatches: Int = 0,
    var totalRuns: Int = 0,
    var totalBallsFaced: Int = 0,
    var totalFours: Int = 0,
    var totalSixes: Int = 0,
    var totalWickets: Int = 0,
    var totalRunsConceded: Int = 0,
    var totalBallsBowled: Int = 0,
    var timesOut: Int = 0,
    var notOuts: Int = 0,
    val lastPlayed: Long = System.currentTimeMillis(),
    // Match-wise performance
    val matchPerformances: MutableList<MatchPerformance> = mutableListOf(),
) {
    val battingAverage: Double
        get() =
            if (timesOut > 0) {
                totalRuns.toDouble() / timesOut
            } else if (totalRuns > 0) {
                totalRuns.toDouble()
            } else {
                0.0
            }

    val strikeRate: Double
        get() = if (totalBallsFaced > 0) (totalRuns.toDouble() / totalBallsFaced) * 100 else 0.0

    val bowlingAverage: Double
        get() = if (totalWickets > 0) totalRunsConceded.toDouble() / totalWickets else 0.0

    val economyRate: Double
        get() = if (totalBallsBowled > 0) (totalRunsConceded.toDouble() / totalBallsBowled) * 6 else 0.0

    val oversBowled: Double
        get() = totalBallsBowled / 6.0 + (totalBallsBowled % 6) * 0.1
}

// Match performance data for individual matches
data class MatchPerformance(
    val matchId: String,
    val matchDate: Long,
    val opposingTeam: String,
    val myTeam: String,
    // Batting
    val runs: Int = 0,
    val ballsFaced: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val isOut: Boolean = false,
    // Bowling
    val wickets: Int = 0,
    val runsConceded: Int = 0,
    val ballsBowled: Int = 0,
    // Match context
    val isWinner: Boolean = false,
    val isJoker: Boolean = false,
    val groupId: String? = null,
    val isShortPitch: Boolean = false
)

// Basic player storage manager
class PlayerStorageManager(
    private val context: Context,
) {
    private val prefs = context.getSharedPreferences("cricket_players", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getAllPlayers(): List<StoredPlayer> =
        try {
            val playersJson = prefs.getString("players_json", null)
            if (playersJson.isNullOrEmpty()) {
                syncBasicPlayersFromMatches()
                val newPlayersJson = prefs.getString("players_json", null)
                if (newPlayersJson.isNullOrEmpty()) {
                    emptyList()
                } else {
                    val type = TypeToken.getParameterized(List::class.java, StoredPlayer::class.java).type
                    gson.fromJson(newPlayersJson, type)
                }
            } else {
                val type = TypeToken.getParameterized(List::class.java, StoredPlayer::class.java).type
                val players: List<StoredPlayer> = gson.fromJson(playersJson, type)

                // Auto-sync with latest match data to keep stats current
                syncBasicPlayersFromMatches()

                // Return the updated data
                val updatedJson = prefs.getString("players_json", playersJson)
                val updatedType = TypeToken.getParameterized(List::class.java, StoredPlayer::class.java).type
                gson.fromJson(updatedJson, updatedType)
            }
        } catch (e: Exception) {
            android.util.Log.e("PlayerStorage", "Error loading players", e)
            emptyList()
        }

    private fun syncBasicPlayersFromMatches() {
        val matchStorage = MatchStorageManager(context)
        val allMatches = matchStorage.getAllMatches()

        if (allMatches.isEmpty()) return

        val existingPlayers =
            try {
                val playersJson = prefs.getString("players_json", null)
                if (playersJson.isNullOrEmpty()) {
                    mutableMapOf<String, StoredPlayer>()
                } else {
                    val type = TypeToken.getParameterized(List::class.java, StoredPlayer::class.java).type
                    val playersList: List<StoredPlayer> = gson.fromJson(playersJson, type)
                    playersList.associateBy { it.name }.toMutableMap()
                }
            } catch (e: Exception) {
                mutableMapOf<String, StoredPlayer>()
            }

        data class PlayerStats(
            var matchesPlayed: Int = 0,
            var totalRuns: Int = 0,
            var totalWickets: Int = 0,
            var lastPlayed: Long = 0L,
        )
        val playerStats = mutableMapOf<String, PlayerStats>()

        allMatches.forEach { match ->
            val matchDate = match.matchDate

            // Collect all unique player names from this match
            val playersInMatch = mutableSetOf<String>()
            match.firstInningsBatting.forEach { playersInMatch.add(it.name) }
            match.firstInningsBowling.forEach { playersInMatch.add(it.name) }
            match.secondInningsBatting.forEach { playersInMatch.add(it.name) }
            match.secondInningsBowling.forEach { playersInMatch.add(it.name) }

            // For each player in this match, increment their match count
            playersInMatch.forEach { playerName ->
                val stats = playerStats.getOrPut(playerName) { PlayerStats() }
                stats.matchesPlayed++
                if (stats.lastPlayed < matchDate) stats.lastPlayed = matchDate
            }

            // Process batting stats
            (match.firstInningsBatting + match.secondInningsBatting).forEach { playerStat ->
                val stats = playerStats.getOrPut(playerStat.name) { PlayerStats() }
                stats.totalRuns += playerStat.runs
            }

            // Process bowling stats
            (match.firstInningsBowling + match.secondInningsBowling).forEach { playerStat ->
                val stats = playerStats.getOrPut(playerStat.name) { PlayerStats() }
                stats.totalWickets += playerStat.wickets
            }
        }

        // Update existing players or create new ones
        playerStats.forEach { (playerName, stats) ->
            val existingPlayer = existingPlayers[playerName]

            existingPlayers[playerName] =
                if (existingPlayer != null) {
                    existingPlayer.copy(
                        matchesPlayed = stats.matchesPlayed,
                        totalRuns = stats.totalRuns,
                        totalWickets = stats.totalWickets,
                        lastPlayed = stats.lastPlayed,
                    )
                } else {
                    StoredPlayer(
                        name = playerName,
                        matchesPlayed = stats.matchesPlayed,
                        totalRuns = stats.totalRuns,
                        totalWickets = stats.totalWickets,
                        lastPlayed = stats.lastPlayed,
                    )
                }
        }

        val playersJson = gson.toJson(existingPlayers.values.toList())
        prefs.edit().putString("players_json", playersJson).apply()

        android.util.Log.d("PlayerStorage", "Synced ${existingPlayers.size} basic players from match data")
    }

    fun toPlayer(sp: StoredPlayer): Player =
        Player(id = PlayerId(sp.id), name = sp.name)

    fun addOrUpdatePlayer(playerName: String): StoredPlayer {
        val players = getAllPlayers().toMutableList()
        val existing = players.find { it.name.equals(playerName, ignoreCase = true) }
        val updated =
            if (existing != null) existing.copy(name = playerName.trim(), lastPlayed = System.currentTimeMillis())
            else StoredPlayer(name = playerName.trim())
        if (existing != null) players[players.indexOfFirst { it.id == existing.id }] = updated else players.add(updated)
        prefs.edit().putString("players_json", gson.toJson(players)).apply()
        return updated
    }

    fun searchPlayers(query: String): List<StoredPlayer> =
        getAllPlayers()
            .filter { it.name.contains(query, ignoreCase = true) }
            .sortedByDescending { it.matchesPlayed }

    fun deletePlayer(playerId: String): Boolean =
        try {
            val players = getAllPlayers().toMutableList()
            val removed = players.removeAll { it.id == playerId }

            if (removed) {
                val playersJson = gson.toJson(players)
                prefs.edit().putString("players_json", playersJson).apply()
                android.util.Log.d("PlayerStorage", "Player deleted: $playerId")
            }

            removed
        } catch (e: Exception) {
            android.util.Log.e("PlayerStorage", "Error deleting player", e)
            false
        }

    fun forceSyncWithMatches() {
        syncBasicPlayersFromMatches()
    }
}

// Enhanced player storage manager for detailed statistics
class EnhancedPlayerStorageManager(
    private val context: Context,
) {
    private val prefs = context.getSharedPreferences("cricket_players_detailed", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun syncPlayerStatsFromMatches() {
        val matchStorage = MatchStorageManager(context)
        val allMatches = matchStorage.getAllMatches()

        val playersMap = mutableMapOf<String, PlayerDetailedStats>()

        allMatches.forEach { match ->
            // Process first innings batting (Team 1)
            match.firstInningsBatting.forEach { playerStat ->
                val playerId = playerStat.id
                val player =
                    playersMap.getOrPut(playerId.toString()) {
                        PlayerDetailedStats(
                            playerId = playerId.toString(),
                            name = playerStat.name,
                        )
                    }

                player.totalRuns += playerStat.runs
                player.totalBallsFaced += playerStat.ballsFaced
                player.totalFours += playerStat.fours
                player.totalSixes += playerStat.sixes
                if (playerStat.isOut) player.timesOut++ else player.notOuts++

                val existingPerf = player.matchPerformances.find { it.matchId == match.id }
                if (existingPerf == null) {
                    player.totalMatches++
                    player.matchPerformances.add(
                        MatchPerformance(
                            matchId = match.id,
                            matchDate = match.matchDate,
                            opposingTeam = match.team2Name,
                            myTeam = match.team1Name,
                            runs = playerStat.runs,
                            ballsFaced = playerStat.ballsFaced,
                            fours = playerStat.fours,
                            sixes = playerStat.sixes,
                            isOut = playerStat.isOut,
                            isWinner = match.winnerTeam == match.team1Name,
                            isJoker = playerStat.isJoker,
                        ),
                    )
                }
            }

            // Process first innings bowling (Team 2)
            match.firstInningsBowling.forEach { playerStat ->
                val playerId = playerStat.id
                val player =
                    playersMap.getOrPut(playerId.toString()) {
                        PlayerDetailedStats(
                            playerId = playerId.toString(),
                            name = playerStat.name,
                        )
                    }

                player.totalWickets += playerStat.wickets
                player.totalRunsConceded += playerStat.runsConceded
                player.totalBallsBowled += (playerStat.oversBowled * 6).toInt()

                val existingPerf = player.matchPerformances.find { it.matchId == match.id }
                if (existingPerf != null) {
                    val index = player.matchPerformances.indexOf(existingPerf)
                    player.matchPerformances[index] =
                        existingPerf.copy(
                            wickets = playerStat.wickets,
                            runsConceded = playerStat.runsConceded,
                            ballsBowled = (playerStat.oversBowled * 6).toInt(),
                        )
                } else {
                    player.totalMatches++
                    player.matchPerformances.add(
                        MatchPerformance(
                            matchId = match.id,
                            matchDate = match.matchDate,
                            opposingTeam = match.team1Name,
                            myTeam = match.team2Name,
                            wickets = playerStat.wickets,
                            runsConceded = playerStat.runsConceded,
                            ballsBowled = (playerStat.oversBowled * 6).toInt(),
                            isWinner = match.winnerTeam == match.team2Name,
                            isJoker = playerStat.isJoker,
                        ),
                    )
                }
            }

            // Process second innings batting (Team 2)
            match.secondInningsBatting.forEach { playerStat ->
                val playerId = playerStat.id
                val player =
                    playersMap.getOrPut(playerId.toString()) {
                        PlayerDetailedStats(
                            playerId = playerId.toString(),
                            name = playerStat.name,
                        )
                    }

                player.totalRuns += playerStat.runs
                player.totalBallsFaced += playerStat.ballsFaced
                player.totalFours += playerStat.fours
                player.totalSixes += playerStat.sixes
                if (playerStat.isOut) player.timesOut++ else player.notOuts++

                val existingPerf = player.matchPerformances.find { it.matchId == match.id }
                if (existingPerf == null) {
                    player.totalMatches++
                    player.matchPerformances.add(
                        MatchPerformance(
                            matchId = match.id,
                            matchDate = match.matchDate,
                            opposingTeam = match.team1Name,
                            myTeam = match.team2Name,
                            runs = playerStat.runs,
                            ballsFaced = playerStat.ballsFaced,
                            fours = playerStat.fours,
                            sixes = playerStat.sixes,
                            isOut = playerStat.isOut,
                            isWinner = match.winnerTeam == match.team2Name,
                            isJoker = playerStat.isJoker,
                        ),
                    )
                } else {
                    val index = player.matchPerformances.indexOf(existingPerf)
                    player.matchPerformances[index] =
                        existingPerf.copy(
                            runs = existingPerf.runs + playerStat.runs,
                            ballsFaced = existingPerf.ballsFaced + playerStat.ballsFaced,
                            fours = existingPerf.fours + playerStat.fours,
                            sixes = existingPerf.sixes + playerStat.sixes,
                            isOut = existingPerf.isOut || playerStat.isOut,
                        )
                }
            }

            // Process second innings bowling (Team 1)
            match.secondInningsBowling.forEach { playerStat ->
                val playerId = playerStat.id
                val player =
                    playersMap.getOrPut(playerId.toString()) {
                        PlayerDetailedStats(
                            playerId = playerId.toString(),
                            name = playerStat.name,
                        )
                    }

                player.totalWickets += playerStat.wickets
                player.totalRunsConceded += playerStat.runsConceded
                player.totalBallsBowled += (playerStat.oversBowled * 6).toInt()

                val existingPerf = player.matchPerformances.find { it.matchId == match.id }
                if (existingPerf != null) {
                    val index = player.matchPerformances.indexOf(existingPerf)
                    player.matchPerformances[index] =
                        existingPerf.copy(
                            wickets = existingPerf.wickets + playerStat.wickets,
                            runsConceded = existingPerf.runsConceded + playerStat.runsConceded,
                            ballsBowled = existingPerf.ballsBowled + (playerStat.oversBowled * 6).toInt(),
                        )
                } else {
                    player.totalMatches++
                    player.matchPerformances.add(
                        MatchPerformance(
                            matchId = match.id,
                            matchDate = match.matchDate,
                            opposingTeam = match.team2Name,
                            myTeam = match.team1Name,
                            wickets = playerStat.wickets,
                            runsConceded = playerStat.runsConceded,
                            ballsBowled = (playerStat.oversBowled * 6).toInt(),
                            isWinner = match.winnerTeam == match.team1Name,
                            isJoker = playerStat.isJoker,
                        ),
                    )
                }
            }
        }

        val playersJson = gson.toJson(playersMap.values.toList())
        prefs.edit().putString("detailed_players", playersJson).apply()

        android.util.Log.d("PlayerSync", "Synced ${playersMap.size} players from ${allMatches.size} matches")
    }

    fun getAllPlayersDetailed(): List<PlayerDetailedStats> =
        try {
            val playersJson = prefs.getString("detailed_players", null)
            if (playersJson.isNullOrEmpty()) {
                syncPlayerStatsFromMatches()
                val newPlayersJson = prefs.getString("detailed_players", null)
                if (newPlayersJson.isNullOrEmpty()) {
                    emptyList()
                } else {
                    val type = TypeToken.getParameterized(List::class.java, PlayerDetailedStats::class.java).type
                    gson.fromJson(newPlayersJson, type)
                }
            } else {
                val type = TypeToken.getParameterized(List::class.java, PlayerDetailedStats::class.java).type
                gson.fromJson(playersJson, type)
            }
        } catch (e: Exception) {
            android.util.Log.e("PlayerSync", "Error loading detailed players", e)
            emptyList()
        }

    fun getPlayerDetailed(playerName: String): PlayerDetailedStats? =
        getAllPlayersDetailed().find {
            it.name.equals(playerName, ignoreCase = true)
        }

    fun computeFromMatches(source: List<MatchHistory>): List<PlayerDetailedStats> {
        val playersMap = mutableMapOf<String, PlayerDetailedStats>()

        source.forEach { match ->
            // First innings batting (Team 1)
            match.firstInningsBatting.forEach { p ->
                val player = playersMap.getOrPut(p.name) {
                    PlayerDetailedStats(
                        playerId = p.name.replace(" ", "_").lowercase(),
                        name = p.name
                    )
                }
                player.totalRuns += p.runs
                player.totalBallsFaced += p.ballsFaced
                player.totalFours += p.fours
                player.totalSixes += p.sixes
                if (p.isOut) player.timesOut++ else player.notOuts++
                val existing = player.matchPerformances.find { it.matchId == match.id }
                if (existing == null) {
                    player.totalMatches++
                    player.matchPerformances.add(
                        playerPerformanceFromBat(match, p, myTeam = match.team1Name, opp = match.team2Name)
                    )
                } else {
                    // merge if same player has multiple entries (safety)
                    val idx = player.matchPerformances.indexOf(existing)
                    player.matchPerformances[idx] = existing.copy(
                        runs = existing.runs + p.runs,
                        ballsFaced = existing.ballsFaced + p.ballsFaced,
                        fours = existing.fours + p.fours,
                        sixes = existing.sixes + p.sixes,
                        isOut = existing.isOut || p.isOut
                    )
                }
            }

            // First innings bowling (Team 2)
            match.firstInningsBowling.forEach { p ->
                val player = playersMap.getOrPut(p.name) {
                    PlayerDetailedStats(
                        playerId = p.name.replace(" ", "_").lowercase(),
                        name = p.name
                    )
                }
                player.totalWickets += p.wickets
                player.totalRunsConceded += p.runsConceded
                player.totalBallsBowled += (p.oversBowled * 6).toInt()
                upsertBowlPerf(player, match, p, myTeam = match.team2Name, opp = match.team1Name)
            }

            // Second innings batting (Team 2)
            match.secondInningsBatting.forEach { p ->
                val player = playersMap.getOrPut(p.name) {
                    PlayerDetailedStats(
                        playerId = p.name.replace(" ", "_").lowercase(),
                        name = p.name
                    )
                }
                player.totalRuns += p.runs
                player.totalBallsFaced += p.ballsFaced
                player.totalFours += p.fours
                player.totalSixes += p.sixes
                if (p.isOut) player.timesOut++ else player.notOuts++
                val existing = player.matchPerformances.find { it.matchId == match.id }
                if (existing == null) {
                    player.totalMatches++
                    player.matchPerformances.add(
                        playerPerformanceFromBat(match, p, myTeam = match.team2Name, opp = match.team1Name)
                    )
                } else {
                    val idx = player.matchPerformances.indexOf(existing)
                    player.matchPerformances[idx] = existing.copy(
                        runs = existing.runs + p.runs,
                        ballsFaced = existing.ballsFaced + p.ballsFaced,
                        fours = existing.fours + p.fours,
                        sixes = existing.sixes + p.sixes,
                        isOut = existing.isOut || p.isOut
                    )
                }
            }

            // Second innings bowling (Team 1)
            match.secondInningsBowling.forEach { p ->
                val player = playersMap.getOrPut(p.name) {
                    PlayerDetailedStats(
                        playerId = p.name.replace(" ", "_").lowercase(),
                        name = p.name
                    )
                }
                player.totalWickets += p.wickets
                player.totalRunsConceded += p.runsConceded
                player.totalBallsBowled += (p.oversBowled * 6).toInt()
                upsertBowlPerf(player, match, p, myTeam = match.team1Name, opp = match.team2Name)
            }
        }

        return playersMap.values.toList()
    }

    // Helpers inside EnhancedPlayerStorageManager
    private fun playerPerformanceFromBat(
        match: MatchHistory,
        p: PlayerMatchStats,
        myTeam: String,
        opp: String
    ): MatchPerformance {
        return MatchPerformance(
            matchId = match.id,
            matchDate = match.matchDate,
            opposingTeam = opp,
            myTeam = myTeam,
            runs = p.runs,
            ballsFaced = p.ballsFaced,
            fours = p.fours,
            sixes = p.sixes,
            isOut = p.isOut,
            wickets = 0,
            runsConceded = 0,
            ballsBowled = 0,
            isWinner = match.winnerTeam == myTeam,
            isJoker = p.isJoker,
            isShortPitch = match.shortPitch,
            groupId = match.groupId
        )
    }

    private fun upsertBowlPerf(
        player: PlayerDetailedStats,
        match: MatchHistory,
        p: PlayerMatchStats,
        myTeam: String,
        opp: String
    ) {
        val existing = player.matchPerformances.find { it.matchId == match.id }
        if (existing != null) {
            val idx = player.matchPerformances.indexOf(existing)
            player.matchPerformances[idx] = existing.copy(
                wickets = existing.wickets + p.wickets,
                runsConceded = existing.runsConceded + p.runsConceded,
                ballsBowled = existing.ballsBowled + (p.oversBowled * 6).toInt(),
                isWinner = match.winnerTeam == myTeam || existing.isWinner,
                isJoker = existing.isJoker || p.isJoker
            )
        } else {
            player.totalMatches++
            player.matchPerformances.add(
                MatchPerformance(
                    matchId = match.id,
                    matchDate = match.matchDate,
                    opposingTeam = opp,
                    myTeam = myTeam,
                    wickets = p.wickets,
                    runsConceded = p.runsConceded,
                    ballsBowled = (p.oversBowled * 6).toInt(),
                    isWinner = match.winnerTeam == myTeam,
                    isJoker = p.isJoker
                )
            )
        }
    }

}

// Helper function to format date
fun formatDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60 * 1000 -> "Just now"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} min ago"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} hours ago"
        diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)} days ago"
        else -> "${diff / (7 * 24 * 60 * 60 * 1000)} weeks ago"
    }
}
