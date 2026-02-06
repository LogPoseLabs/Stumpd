package com.oreki.stumpd.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "player_match_stats",
    primaryKeys = ["matchId", "playerId", "team", "role"],
    indices = [Index("matchId"), Index("playerId"), Index("team")]
)
data class PlayerMatchStatsEntity(
    val matchId: String,
    val playerId: String,
    val name: String,
    val team: String,
    val role: String, // "BAT" or "BOWL"
    val runs: Int,
    val ballsFaced: Int,
    val dots: Int = 0,
    val singles: Int = 0,
    val twos: Int = 0,
    val threes: Int = 0,
    val fours: Int,
    val sixes: Int,
    val wickets: Int,
    val runsConceded: Int,
    val oversBowled: Double,
    val maidenOvers: Int = 0,
    val isOut: Boolean,
    val isRetired: Boolean = false,
    val isJoker: Boolean,
    val catches: Int = 0,
    val runOuts: Int = 0,
    val stumpings: Int = 0,
    val dismissalType: String? = null,
    val bowlerName: String? = null,
    val fielderName: String? = null,
    val battingPosition: Int = 0,
    val bowlingPosition: Int = 0
)
