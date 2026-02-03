package com.oreki.stumpd

data class PlayerId(val value: String = java.util.UUID.randomUUID().toString())

data class Player(
    val id: PlayerId = PlayerId(),
    val name: String = "",
    var runs: Int = 0,
    var ballsFaced: Int = 0,
    var dots: Int = 0,        // 0 runs
    var singles: Int = 0,     // 1 run
    var twos: Int = 0,        // 2 runs
    var threes: Int = 0,      // 3 runs
    var fours: Int = 0,
    var sixes: Int = 0,
    var isOut: Boolean = false,
    var isRetired: Boolean = false, // New: for retired/substituted players
    var wickets: Int = 0,
    var runsConceded: Int = 0,
    var ballsBowled: Int = 0,
    var maidenOvers: Int = 0,
    val isJoker: Boolean = false,
    // Fielding stats
    var catches: Int = 0,
    var runOuts: Int = 0,
    var stumpings: Int = 0,
    // Dismissal info
    var dismissalType: WicketType? = null,
    var bowlerName: String? = null,
    var fielderName: String? = null,
) {
    val strikeRate: Double get() = if (ballsFaced > 0) (runs.toDouble() / ballsFaced) * 100 else 0.0
    val oversBowled: Double get() = (ballsBowled / 6) + (ballsBowled % 6) * 0.1
    val economy: Double get() = if (ballsBowled > 0) (runsConceded.toDouble() / ballsBowled) * 6 else 0.0
    val totalFieldingContributions: Int get() = catches + runOuts + stumpings
    
    fun getDismissalText(): String {
        if (isRetired) return "retd"
        if (!isOut || dismissalType == null) return "not out"
        
        return when (dismissalType) {
            WicketType.CAUGHT -> {
                // Check if caught & bowled (same person)
                if (fielderName != null && bowlerName != null && fielderName == bowlerName) {
                    "c & b $bowlerName"
                } else {
                    val fielder = fielderName?.let { "c $it " } ?: ""
                    val bowler = bowlerName?.let { "b $it" } ?: ""
                    "$fielder$bowler".trim()
                }
            }
            WicketType.BOWLED -> bowlerName?.let { "b $it" } ?: "bowled"
            WicketType.LBW -> bowlerName?.let { "lbw b $it" } ?: "lbw"
            WicketType.STUMPED -> {
                val fielder = fielderName?.let { "st $it " } ?: ""
                val bowler = bowlerName?.let { "b $it" } ?: ""
                "$fielder$bowler".trim()
            }
            WicketType.RUN_OUT -> fielderName?.let { "run out ($it)" } ?: "run out"
            WicketType.HIT_WICKET -> bowlerName?.let { "hit wicket b $it" } ?: "hit wicket"
            WicketType.BOUNDARY_OUT -> "boundary out"
            else -> "out"
        }
    }
    
    fun toMatchStats(teamName: String): PlayerMatchStats = PlayerMatchStats(
        id = id.value,
        name = name, runs = runs, ballsFaced = ballsFaced, 
        dots = dots, singles = singles, twos = twos, threes = threes,
        fours = fours, sixes = sixes,
        wickets = wickets, runsConceded = runsConceded, oversBowled = oversBowled,
        maidenOvers = maidenOvers,
        isOut = isOut, isRetired = isRetired, isJoker = isJoker, team = teamName,
        catches = catches, runOuts = runOuts, stumpings = stumpings,
        dismissalType = dismissalType?.name,
        bowlerName = bowlerName,
        fielderName = fielderName
    )
}

// White/Red-ball and short/long pitch
enum class BallFormat { WHITE_BALL, RED_BALL }

data class GroupDefaultSettings(
    val matchSettings: MatchSettings,
    val groundName: String = "",
    val format: String = BallFormat.WHITE_BALL.toString(),
    // If true -> short pitch; if false -> long pitch
    val shortPitch: Boolean = false,
)

data class PlayerGroup(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    // Store player ids (permanent membership)
    val playerIds: List<String> = emptyList(),
    // Store unavailable player ids (temporary availability toggle)
    val unavailablePlayerIds: List<String> = emptyList(),
    val defaults: GroupDefaultSettings,
)

// Enhanced Team data class
data class Team(
    val name: String,
    val players: MutableList<Player> = mutableListOf(), // Changed from List to MutableList
) {
    val regularPlayersCount: Int
        get() = players.count { !it.isJoker }
}

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
    BOUNDARY_OUT
}

data class RunOutInput(
    val runsCompleted: Int,
    val end: RunOutEnd,
    val whoOut: String
)

enum class RunOutEnd { STRIKER_END, NON_STRIKER_END }

data class DeliveryUI(
    val inning: Int,
    val over: Int,
    val ballInOver: Int,      // 1..6
    val outcome: String,      // "0","1","4","W","Wd+1","Nb+2", etc.
    val highlight: Boolean = false, // e.g., boundary/wicket for tint
    val strikerName: String = "",
    val nonStrikerName: String = "",
    val bowlerName: String = "",
    val runs: Int = 0  // Total runs scored on this delivery (including wides/no-balls)
)


// Enhanced MatchHistory with proper innings separation
data class MatchHistory(
    val id: String =
        java.util.UUID
            .randomUUID()
            .toString(),
    val team1Name: String,
    val team2Name: String,
    val jokerPlayerName: String? = null,
    val team1CaptainName: String? = null,
    val team2CaptainName: String? = null,
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
    val matchSettings: MatchSettings? = null,
    val groupId: String? = null,
    val groupName: String? = null,
    val shortPitch: Boolean = false,
    // New fields for partnerships and fall of wickets
    val firstInningsPartnerships: List<Partnership> = emptyList(),
    val secondInningsPartnerships: List<Partnership> = emptyList(),
    val firstInningsFallOfWickets: List<FallOfWicket> = emptyList(),
    val secondInningsFallOfWickets: List<FallOfWicket> = emptyList(),
    // NEW: Player of the Match (optional)
    val playerOfTheMatchId: String? = null,
    val playerOfTheMatchName: String? = null,
    val playerOfTheMatchTeam: String? = null,
    val playerOfTheMatchImpact: Double? = null,
    val playerOfTheMatchSummary: String? = null,
    // NEW: all players' impacts
    val playerImpacts: List<PlayerImpact> = emptyList(),
    // Ball-by-ball deliveries
    val allDeliveries: List<DeliveryUI> = emptyList()
)

// Individual player performance in a match
data class PlayerMatchStats(
    val id: String,
    val name: String,
    val runs: Int = 0,
    val ballsFaced: Int = 0,
    val dots: Int = 0,
    val singles: Int = 0,
    val twos: Int = 0,
    val threes: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val wickets: Int = 0,
    val runsConceded: Int = 0,
    val oversBowled: Double = 0.0,
    val maidenOvers: Int = 0,
    val isOut: Boolean = false,
    val isRetired: Boolean = false, // New: for retired/substituted players
    val isJoker: Boolean = false,
    val team: String = "",
    val catches: Int = 0,
    val runOuts: Int = 0,
    val stumpings: Int = 0,
    val dismissalType: String? = null,
    val bowlerName: String? = null,
    val fielderName: String? = null,
) {
    val strikeRate: Double
        get() = if (ballsFaced > 0) (runs.toDouble() / ballsFaced) * 100 else 0.0

    val economy: Double
        get() = if (oversBowled > 0) runsConceded / oversBowled else 0.0
    
    val totalFieldingContributions: Int
        get() = catches + runOuts + stumpings
    
    fun getDismissalText(): String {
        if (isRetired) return "retd"
        if (!isOut || dismissalType == null) return "not out"
        
        return when (dismissalType) {
            "CAUGHT" -> {
                // Check if caught & bowled (same person)
                if (fielderName != null && bowlerName != null && fielderName == bowlerName) {
                    "c & b $bowlerName"
                } else {
                    val fielder = fielderName?.let { "c $it " } ?: ""
                    val bowler = bowlerName?.let { "b $it" } ?: ""
                    "$fielder$bowler".trim()
                }
            }
            "BOWLED" -> bowlerName?.let { "b $it" } ?: "bowled"
            "LBW" -> bowlerName?.let { "lbw b $it" } ?: "lbw"
            "STUMPED" -> {
                val fielder = fielderName?.let { "st $it " } ?: ""
                val bowler = bowlerName?.let { "b $it" } ?: ""
                "$fielder$bowler".trim()
            }
            "RUN_OUT" -> fielderName?.let { "run out ($it)" } ?: "run out"
            "HIT_WICKET" -> bowlerName?.let { "hit wicket b $it" } ?: "hit wicket"
            "BOUNDARY_OUT" -> "boundary out"
            else -> "out"
        }
    }
}

// Type alias for backward compatibility - actual class moved to data.storage package
@Deprecated(
    message = "Use com.oreki.stumpd.data.storage.MatchStorageManager instead",
    replaceWith = ReplaceWith("com.oreki.stumpd.data.storage.MatchStorageManager")
)
typealias MatchStorageManager = com.oreki.stumpd.data.storage.MatchStorageManager

data class DeliverySnapshot(
    val strikerIndex: Int?,
    val nonStrikerIndex: Int?,
    val bowlerIndex: Int?,
    val battingTeamPlayers: List<Player>,
    val bowlingTeamPlayers: List<Player>,
    val totalWickets: Int,
    val currentOver: Int,
    val ballsInOver: Int,
    val totalExtras: Int,
    val calculatedTotalRuns: Int,
    val previousBowlerName: String?,
    val midOverReplacementDueToJoker: Boolean,
    val jokerBallsBowledInnings1: Int,
    val jokerBallsBowledInnings2: Int,
    val completedBattersInnings1: List<Player>,
    val completedBattersInnings2: List<Player>,
    val completedBowlersInnings1: List<Player>,
    val completedBowlersInnings2: List<Player>
)

enum class NoBallSubOutcome { NONE, RUN_OUT, BOUNDARY_OUT }

data class NoBallBoundaryOutInput(
    val outBatterName: String? = null, // default striker if null
)

data class PlayerImpact(
    val id: String,
    val name: String,
    val team: String,
    val impact: Double,
    val summary: String,
    val isJoker: Boolean = false,
    val runs: Int = 0,
    val balls: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val wickets: Int = 0,
    val runsConceded: Int = 0,
    val oversBowled: Double = 0.0
)

data class Partnership(
    val batsman1Name: String,
    val batsman2Name: String,
    val runs: Int,
    val balls: Int,
    val batsman1Runs: Int = 0,
    val batsman2Runs: Int = 0,
    val isActive: Boolean = true // false when partnership ends (wicket)
)

data class FallOfWicket(
    val batsmanName: String,
    val runs: Int, // Team score when wicket fell
    val overs: Double, // Overs when wicket fell
    val wicketNumber: Int, // 1st wicket, 2nd wicket, etc.
    val dismissalType: String? = null,
    val bowlerName: String? = null,
    val fielderName: String? = null
)
