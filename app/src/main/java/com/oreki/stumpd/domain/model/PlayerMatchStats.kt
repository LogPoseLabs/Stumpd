package com.oreki.stumpd.domain.model

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
    val role: String = "", // "BAT" or "BOWL" (empty = legacy)
    val catches: Int = 0,
    val runOuts: Int = 0,
    val stumpings: Int = 0,
    val dismissalType: String? = null,
    val bowlerName: String? = null,
    val fielderName: String? = null,
    val battingPosition: Int = 0,
    val bowlingPosition: Int = 0,
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
