package com.oreki.stumpd.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "in_progress_matches")
data class InProgressMatchEntity(
    @PrimaryKey val matchId: String,
    val team1Name: String,
    val team2Name: String,
    val jokerName: String,
    val groupId: String?,
    val groupName: String?,
    val tossWinner: String?,
    val tossChoice: String?,
    val matchSettingsJson: String,
    
    // Player info
    val team1PlayerIds: String, // JSON array
    val team2PlayerIds: String, // JSON array
    val team1PlayerNames: String, // JSON array
    val team2PlayerNames: String, // JSON array
    
    // Current match state
    val currentInnings: Int,
    val currentOver: Int,
    val ballsInOver: Int,
    val totalWickets: Int,
    
    // Team players state (serialized as JSON)
    val team1PlayersJson: String,
    val team2PlayersJson: String,
    
    // Current roles
    val strikerIndex: Int?,
    val nonStrikerIndex: Int?,
    val bowlerIndex: Int?,
    
    // Innings data
    val firstInningsRuns: Int,
    val firstInningsWickets: Int,
    val firstInningsOvers: Int,
    val firstInningsBalls: Int,
    
    // Extras
    val totalExtras: Int,
    val calculatedTotalRuns: Int,
    
    // Completed players lists (serialized as JSON)
    val completedBattersInnings1Json: String?,
    val completedBattersInnings2Json: String?,
    val completedBowlersInnings1Json: String?,
    val completedBowlersInnings2Json: String?,
    
    // First innings stats (serialized as JSON)
    val firstInningsBattingPlayersJson: String?,
    val firstInningsBowlingPlayersJson: String?,
    
    // Joker tracking
    val jokerOutInCurrentInnings: Boolean,
    val jokerBallsBowledInnings1: Int,
    val jokerBallsBowledInnings2: Int,
    
    // Powerplay tracking
    val powerplayRunsInnings1: Int = 0,
    val powerplayRunsInnings2: Int = 0,
    val powerplayDoublingDoneInnings1: Boolean = false,
    val powerplayDoublingDoneInnings2: Boolean = false,
    
    // Deliveries (ball-by-ball data, serialized as JSON)
    val allDeliveriesJson: String?,
    
    // Timestamps
    val lastSavedAt: Long,
    val startedAt: Long
)
