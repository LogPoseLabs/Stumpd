package com.oreki.stumpd.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "fall_of_wickets",
    primaryKeys = ["matchId", "innings", "wicketNumber"],
    indices = [Index("matchId")]
)
data class FallOfWicketEntity(
    val matchId: String,
    val innings: Int, // 1 or 2
    val wicketNumber: Int, // 1st wicket, 2nd wicket, etc.
    val batsmanName: String,
    val runs: Int, // Team score when wicket fell
    val overs: Double, // Overs when wicket fell
    val dismissalType: String? = null,
    val bowlerName: String? = null,
    val fielderName: String? = null
)
