package com.oreki.stumpd.domain.model

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
