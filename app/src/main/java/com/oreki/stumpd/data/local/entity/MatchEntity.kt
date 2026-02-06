package com.oreki.stumpd.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey val id: String,
    val team1Name: String,
    val team2Name: String,
    val jokerPlayerName: String?,
    val team1CaptainName: String?,
    val team2CaptainName: String?,
    val firstInningsRuns: Int,
    val firstInningsWickets: Int,
    val secondInningsRuns: Int,
    val secondInningsWickets: Int,
    val winnerTeam: String,
    val winningMargin: String,
    val matchDate: Long,
    val groupId: String?,
    val groupName: String?,
    val shortPitch: Boolean,
    val playerOfTheMatchId: String?,
    val playerOfTheMatchName: String?,
    val playerOfTheMatchTeam: String?,
    val playerOfTheMatchImpact: Double?,
    val playerOfTheMatchSummary: String?,
    val matchSettingsJson: String?,
    val allDeliveriesJson: String? // Ball-by-ball data
)
