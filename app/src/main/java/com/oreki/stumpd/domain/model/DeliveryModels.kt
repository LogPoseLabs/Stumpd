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
