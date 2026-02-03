package com.oreki.stumpd.data.models

import com.oreki.stumpd.Player

/**
 * Represents a match currently in progress that can be saved and resumed
 */
data class MatchInProgress(
    // Match basic info
    val matchId: String,
    val team1Name: String,
    val team2Name: String,
    val jokerName: String,
    val groupId: String?,
    val groupName: String?,
    val tossWinner: String?,
    val tossChoice: String?,
    val matchSettingsJson: String,
    
    // Player info
    val team1PlayerIds: List<String>,
    val team2PlayerIds: List<String>,
    val team1PlayerNames: List<String>,
    val team2PlayerNames: List<String>,
    
    // Current match state
    val currentInnings: Int = 1,
    val currentOver: Int = 0,
    val ballsInOver: Int = 0,
    val totalWickets: Int = 0,
    
    // Team 1 players state (serialized as JSON)
    val team1PlayersJson: String,
    // Team 2 players state (serialized as JSON)
    val team2PlayersJson: String,
    
    // Current roles
    val strikerIndex: Int? = null,
    val nonStrikerIndex: Int? = null,
    val bowlerIndex: Int? = null,
    
    // Innings data
    val firstInningsRuns: Int = 0,
    val firstInningsWickets: Int = 0,
    val firstInningsOvers: Int = 0,
    val firstInningsBalls: Int = 0,
    
    // Bowling team players (for second innings)
    val bowlingTeamPlayersJson: String? = null,
    
    // Extras
    val totalExtras: Int = 0,
    val calculatedTotalRuns: Int = 0,
    val wides: Int = 0,
    val noBalls: Int = 0,
    val byes: Int = 0,
    val legByes: Int = 0,
    
    // Completed players lists (serialized as JSON)
    val completedBattersInnings1Json: String? = null,
    val completedBattersInnings2Json: String? = null,
    val completedBowlersInnings1Json: String? = null,
    val completedBowlersInnings2Json: String? = null,
    
    // First innings stats (serialized as JSON)
    val firstInningsBattingPlayersJson: String? = null,
    val firstInningsBowlingPlayersJson: String? = null,
    
    // Joker tracking
    val jokerOutInCurrentInnings: Boolean = false,
    val jokerBallsBowledInnings1: Int = 0,
    val jokerBallsBowledInnings2: Int = 0,
    
    // Powerplay tracking
    val powerplayRunsInnings1: Int = 0,
    val powerplayRunsInnings2: Int = 0,
    val powerplayDoublingDoneInnings1: Boolean = false,
    val powerplayDoublingDoneInnings2: Boolean = false,
    
    // Deliveries (ball-by-ball data, serialized as JSON)
    val allDeliveriesJson: String? = null,
    
    // Timestamps
    val lastSavedAt: Long = System.currentTimeMillis(),
    val startedAt: Long = System.currentTimeMillis()
)

