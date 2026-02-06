package com.oreki.stumpd.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "partnerships",
    primaryKeys = ["matchId", "innings", "partnershipNumber"],
    indices = [Index("matchId")]
)
data class PartnershipEntity(
    val matchId: String,
    val innings: Int, // 1 or 2
    val partnershipNumber: Int, // Sequential number (1st partnership, 2nd, etc.)
    val batsman1Name: String,
    val batsman2Name: String,
    val runs: Int,
    val balls: Int,
    val batsman1Runs: Int,
    val batsman2Runs: Int,
    val isActive: Boolean
)
