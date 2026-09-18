package com.oreki.stumpd.domain.model

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

data class DeliverySnapshot(
    val strikerIndex: Int?,
    val nonStrikerIndex: Int?,
    val bowlerIndex: Int?,
    val battingTeamPlayers: List<Player>,
    val bowlingTeamPlayers: List<Player>,
    val totalWickets: Int,
    val currentOver: Int,
    val ballsInOver: Int,
    /** Runs conceded by the current bowler in this incomplete over (maiden / over-end logic). */
    val runsConcededInCurrentOver: Int,
    val totalExtras: Int,
    val calculatedTotalRuns: Int,
    val previousBowlerName: String?,
    val midOverReplacementDueToJoker: Boolean,
    val jokerBallsBowledInnings1: Int,
    val jokerBallsBowledInnings2: Int,
    val jokerOutInCurrentInnings: Boolean,
    val currentBowlerSpell: Int,
    val powerplayDoublingDoneInnings1: Boolean,
    val powerplayDoublingDoneInnings2: Boolean,
    val isNoBallRunOut: Boolean,
    val completedBattersInnings1: List<Player>,
    val completedBattersInnings2: List<Player>,
    val completedBowlersInnings1: List<Player>,
    val completedBowlersInnings2: List<Player>,
    // Partnership / FOW (so undo restores scorecard partnerships correctly)
    val currentPartnershipRuns: Int,
    val currentPartnershipBalls: Int,
    val currentPartnershipBatsman1Runs: Int,
    val currentPartnershipBatsman2Runs: Int,
    val currentPartnershipBatsman1Balls: Int,
    val currentPartnershipBatsman2Balls: Int,
    val currentPartnershipBatsman1Name: String?,
    val currentPartnershipBatsman2Name: String?,
    val partnerships: List<Partnership>,
    val fallOfWickets: List<FallOfWicket>,
    // ── Super over ──────────────────────────────────────────────────────────────────────
    // Defaulted, and they must stay that way: this snapshot is persisted as JSON in
    // `in_progress_matches.deliveryHistoryJson`, so an undefaulted field would break the undo
    // stack of every match currently in flight.
    /** Which innings this snapshot belongs to — undo refuses to cross an innings boundary. */
    val currentInnings: Int = 1,
    /** The target the side in progress must beat, or null while it is setting one. */
    val runsToChase: Int? = null,
    /** Finished batters and bowlers per super-over innings, keyed 3, 4, 5 … */
    val completedBattersSuperOver: Map<Int, List<Player>> = emptyMap(),
    val completedBowlersSuperOver: Map<Int, List<Player>> = emptyMap()
)
