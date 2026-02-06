package com.oreki.stumpd.domain.model

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
