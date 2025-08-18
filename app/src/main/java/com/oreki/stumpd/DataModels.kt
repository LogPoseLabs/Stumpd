package com.oreki.stumpd

import com.google.gson.Gson

data class Player(
    val name: String,
    var runs: Int = 0,
    var ballsFaced: Int = 0,
    var fours: Int = 0,
    var sixes: Int = 0,
    var isOut: Boolean = false,
    var wickets: Int = 0,
    var runsConceded: Int = 0,
    var ballsBowled: Int = 0,
    val isJoker: Boolean = false,
) {
    val strikeRate: Double
        get() = if (ballsFaced > 0) (runs.toDouble() / ballsFaced) * 100 else 0.0

    // Real-time overs calculation
    val oversBowled: Double
        get() = (ballsBowled / 6) + (ballsBowled % 6) * 0.1

    val economy: Double
        get() = if (ballsBowled > 0) (runsConceded.toDouble() / ballsBowled) * 6 else 0.0

    // Convert to PlayerMatchStats for saving
    fun toMatchStats(teamName: String): PlayerMatchStats =
        PlayerMatchStats(
            name = name,
            runs = runs,
            ballsFaced = ballsFaced,
            fours = fours,
            sixes = sixes,
            wickets = wickets,
            runsConceded = runsConceded,
            oversBowled = oversBowled,
            isOut = isOut,
            isJoker = isJoker,
            team = teamName,
        )
}

// Enhanced Team data class
data class Team(
    val name: String,
    val players: MutableList<Player> = mutableListOf(), // Changed from List to MutableList
) {
    val regularPlayersCount: Int
        get() = players.count { !it.isJoker }
}

// Match setup with joker support
data class MatchSetup(
    val team1: Team,
    val team2: Team,
    val jokerPlayer: Player? = null, // Single joker for both teams
    val matchType: MatchType = MatchType.T20,
    val overs: Int = 20,
)

enum class MatchType {
    T20,
    ODI,
    TEST,
}

// Ball-by-ball tracking
data class Ball(
    val ballNumber: Int,
    val runs: Int,
    val extras: ExtraType? = null,
    val extraRuns: Int = 0,
    val isWicket: Boolean = false,
    val wicketType: WicketType? = null,
    val batsman: Player,
    val bowler: Player,
    val timestamp: Long = System.currentTimeMillis(),
)

enum class ExtraType(val displayName: String) {
    NO_BALL("No Ball"),
    OFF_SIDE_WIDE("Off Side Wide"),
    LEG_SIDE_WIDE("Leg Side Wide"),
    BYE("Bye"),
    LEG_BYE("Leg Bye")
}

enum class WicketType {
    BOWLED,
    CAUGHT,
    LBW,
    RUN_OUT,
    STUMPED,
    HIT_WICKET,
}

// Match state tracking
data class MatchState(
    val battingTeam: Team,
    val bowlingTeam: Team,
    val jokerPlayer: Player? = null,
    var currentBatsman1: Player? = null,
    var currentBatsman2: Player? = null,
    var striker: Player? = null,
    var currentBowler: Player? = null,
    var totalRuns: Int = 0,
    var totalWickets: Int = 0,
    var currentOver: Int = 0,
    var ballsInOver: Int = 0,
    var balls: MutableList<Ball> = mutableListOf(),
    val maxOvers: Int = 20,
) {
    val currentScore: String
        get() = "$totalRuns/$totalWickets"

    val oversCompleted: String
        get() = "$currentOver.$ballsInOver"

    val runRate: Double
        get() =
            if (currentOver == 0 && ballsInOver == 0) {
                0.0
            } else {
                totalRuns.toDouble() / ((currentOver * 6 + ballsInOver) / 6.0)
            }
}

// Innings management
data class Innings(
    val battingTeam: Team,
    val bowlingTeam: Team,
    val jokerPlayer: Player? = null,
    var totalRuns: Int = 0,
    var totalWickets: Int = 0,
    var balls: MutableList<Ball> = mutableListOf(),
    val maxOvers: Int = 20,
    var isCompleted: Boolean = false,
) {
    val currentOver: Int
        get() =
            balls.count { ball ->
                ball.extras == null ||
                    ball.extras == ExtraType.BYE ||
                    ball.extras == ExtraType.LEG_BYE
            } / 6

    val ballsInCurrentOver: Int
        get() =
            balls.count { ball ->
                ball.extras == null ||
                    ball.extras == ExtraType.BYE ||
                    ball.extras == ExtraType.LEG_BYE
            } % 6

    val runRate: Double
        get() =
            if (balls.isEmpty()) {
                0.0
            } else {
                totalRuns.toDouble() / (
                    balls.count { ball ->
                        ball.extras == null ||
                            ball.extras == ExtraType.BYE ||
                            ball.extras == ExtraType.LEG_BYE
                    } / 6.0
                )
            }
}

// Complete match structure
data class CricketMatch(
    val team1: Team,
    val team2: Team,
    val jokerPlayer: Player? = null,
    val matchType: MatchType = MatchType.T20,
    val maxOvers: Int = 20,
    var currentInnings: Int = 1, // 1 for first innings, 2 for second
    var firstInnings: Innings? = null,
    var secondInnings: Innings? = null,
    var tossWinner: Team? = null,
    var tossDecision: TossDecision? = null,
) {
    val isMatchCompleted: Boolean
        get() =
            secondInnings?.isCompleted == true ||
                (secondInnings != null && secondInnings!!.totalRuns > (firstInnings?.totalRuns ?: 0))
}

enum class TossDecision {
    BAT_FIRST,
    BOWL_FIRST,
}

// Enhanced MatchHistory with proper innings separation
data class MatchHistory(
    val id: String =
        java.util.UUID
            .randomUUID()
            .toString(),
    val team1Name: String,
    val team2Name: String,
    val jokerPlayerName: String? = null,
    val firstInningsRuns: Int,
    val firstInningsWickets: Int,
    val secondInningsRuns: Int,
    val secondInningsWickets: Int,
    val winnerTeam: String,
    val winningMargin: String,
    val matchDate: Long = System.currentTimeMillis(),
    // Separate batting and bowling stats by innings
    val firstInningsBatting: List<PlayerMatchStats> = emptyList(), // Team1 batting in 1st innings
    val firstInningsBowling: List<PlayerMatchStats> = emptyList(), // Team2 bowling in 1st innings
    val secondInningsBatting: List<PlayerMatchStats> = emptyList(), // Team2 batting in 2nd innings
    val secondInningsBowling: List<PlayerMatchStats> = emptyList(), // Team1 bowling in 2nd innings
    // Keep for backward compatibility
    val team1Players: List<PlayerMatchStats> = emptyList(),
    val team2Players: List<PlayerMatchStats> = emptyList(),
    val topBatsman: PlayerMatchStats? = null,
    val topBowler: PlayerMatchStats? = null,
    val matchSettings: MatchSettings? = null
)

// Individual player performance in a match
data class PlayerMatchStats(
    val name: String,
    val runs: Int = 0,
    val ballsFaced: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val wickets: Int = 0,
    val runsConceded: Int = 0,
    val oversBowled: Double = 0.0,
    val isOut: Boolean = false,
    val isJoker: Boolean = false,
    val team: String = "",
) {
    val strikeRate: Double
        get() = if (ballsFaced > 0) (runs.toDouble() / ballsFaced) * 100 else 0.0

    val economy: Double
        get() = if (oversBowled > 0) runsConceded / oversBowled else 0.0
}

// Simple storage using SharedPreferences (works immediately)
// Enhanced MatchStorageManager with better persistence
class MatchStorageManager(
    private val context: android.content.Context,
) {
    private val prefs = context.getSharedPreferences("cricket_matches_v2", android.content.Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveMatch(match: MatchHistory) {
        try {
            val matches = getAllMatches().toMutableList()
            matches.add(0, match) // Add to beginning

            // Keep only last 100 matches to prevent storage bloat
            if (matches.size > 100) {
                matches.subList(100, matches.size).clear()
            }

            // Convert to JSON string
            val matchesJson = gson.toJson(matches)

            prefs
                .edit()
                .putString("matches_json", matchesJson)
                .putLong("last_updated", System.currentTimeMillis())
                .apply()

            // Debug log
            android.util.Log.d("MatchStorage", "Saved match: ${match.team1Name} vs ${match.team2Name}")
            android.util.Log.d("MatchStorage", "Total matches saved: ${matches.size}")
        } catch (e: Exception) {
            android.util.Log.e("MatchStorage", "Error saving match", e)
        }
    }

    fun getAllMatches(): List<MatchHistory> =
        try {
            val matchesJson = prefs.getString("matches_json", null)
            if (matchesJson.isNullOrEmpty()) {
                android.util.Log.d("MatchStorage", "No matches found in storage")
                emptyList()
            } else {
                val matches = gson.fromJson(matchesJson, Array<MatchHistory>::class.java).toList()
                android.util.Log.d("MatchStorage", "Loaded ${matches.size} matches from storage")
                matches
            }
        } catch (e: Exception) {
            android.util.Log.e("MatchStorage", "Error loading matches", e)
            emptyList()
        }

    fun deleteMatch(matchId: String) {
        try {
            val matches = getAllMatches().filter { it.id != matchId }
            val matchesJson = gson.toJson(matches)

            prefs
                .edit()
                .putString("matches_json", matchesJson)
                .putLong("last_updated", System.currentTimeMillis())
                .apply()

            android.util.Log.d("MatchStorage", "Deleted match with ID: $matchId")
            android.util.Log.d("MatchStorage", "Remaining matches: ${matches.size}")
        } catch (e: Exception) {
            android.util.Log.e("MatchStorage", "Error deleting match", e)
        }
    }

    fun clearAllMatches() {
        prefs.edit().clear().apply()
        android.util.Log.d("MatchStorage", "Cleared all matches")
    }

    // Debug function to check what's stored
    fun debugStorage(): String {
        val matchesJson = prefs.getString("matches_json", "No data")
        val lastUpdated = prefs.getLong("last_updated", 0)
        return "Storage Debug:\nJSON: $matchesJson\nLast Updated: $lastUpdated"
    }
    // Add these to your MatchStorageManager class

    fun exportMatches(fileName: String = "stumpd_backup_${System.currentTimeMillis()}.json"): String? {
        return try {
            val matches = getAllMatches()
            val gson = Gson()
            val json = gson.toJson(matches)

            val path = context.getExternalFilesDir(null)
            if (path == null) {
                android.util.Log.e("Export", "External storage not available")
                return null
            }

            val file = java.io.File(path, fileName)
            file.writeText(json)

            android.util.Log.d("Export", "Exported ${matches.size} matches to ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("Export", "Failed to export matches", e)
            null
        }
    }

    fun importMatches(filePath: String): Boolean {
        return try {
            val file = java.io.File(filePath)
            if (!file.exists()) {
                android.util.Log.e("Import", "File does not exist: $filePath")
                return false
            }

            val json = file.readText()
            val gson = Gson()
            val type =
                com.google.gson.reflect.TypeToken
                    .getParameterized(
                        List::class.java,
                        MatchHistory::class.java,
                    ).type

            val matches: List<MatchHistory> = gson.fromJson(json, type)

            // Clear existing matches first (optional)
            // clearAllMatches()

            // Import all matches
            matches.forEach { match ->
                saveMatch(match)
            }

            android.util.Log.d("Import", "Imported ${matches.size} matches")
            true
        } catch (e: Exception) {
            android.util.Log.e("Import", "Failed to import matches", e)
            false
        }
    }

    fun shareBackup(): String? {
        val backupPath = exportMatches()
        if (backupPath != null) {
            // You can add sharing functionality here
            return backupPath
        }
        return null
    }
}
