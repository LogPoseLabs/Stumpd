package com.oreki.stumpd.domain.model

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
