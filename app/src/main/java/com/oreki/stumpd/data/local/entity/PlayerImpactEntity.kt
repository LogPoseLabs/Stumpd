package com.oreki.stumpd.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "player_impacts",
    indices = [Index("matchId"), Index("playerId")]
)
data class PlayerImpactEntity(
    @PrimaryKey(autoGenerate = true) val pk: Long = 0,
    val matchId: String,
    val playerId: String,
    val name: String,
    val team: String,
    val impact: Double,
    val summary: String,
    val isJoker: Boolean,
    val runs: Int,
    val balls: Int,
    val dots: Int = 0,
    val singles: Int = 0,
    val twos: Int = 0,
    val threes: Int = 0,
    val fours: Int,
    val sixes: Int,
    val wickets: Int,
    val runsConceded: Int,
    val oversBowled: Double
)
