package com.oreki.stumpd.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The index must be declared here as well as created in the migration: Room validates indices as
 * strictly as columns, so an index the migration creates but the entity doesn't know about fails
 * validation and rolls the whole migration back.
 */
@Entity(
    tableName = "matches",
    indices = [Index("tournamentId")],
)
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
    val allDeliveriesJson: String?, // Ball-by-ball data
    /** Who won the super over, or `"TIE"`; null — the usual case — means none was played. */
    val superOverWinner: String? = null,
    /** One record per super-over innings: who batted, and what they made. */
    val superOversJson: String? = null,
    /** The tournament and fixture this match settled, when it was played as one. */
    val tournamentId: String? = null,
    val tournamentFixtureId: String? = null,
    /**
     * The two sides as tournament teams.
     *
     * Ordered like [team1Name] and [team2Name] — which mean "batted first" and "bowled first", not
     * home and away — so they follow the toss, not the fixture.
     */
    val team1Id: String? = null,
    val team2Id: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
