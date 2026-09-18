package com.oreki.stumpd.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One player's impact score for one match.
 *
 * Keyed by `(matchId, playerId)` rather than a generated row id: the previous `autoGenerate` key
 * meant every re-save of a match — adopting it into a group, merging one in, downloading it from
 * the cloud, or correcting it — *appended* a fresh set of impact rows instead of replacing them,
 * and `impactsForMatch` returned all of them. The composite key also matches the Firestore
 * document id for these rows (`impacts/{playerId}`), which it previously disagreed with.
 */
@Entity(
    tableName = "player_impacts",
    primaryKeys = ["matchId", "playerId"],
    indices = [Index("matchId"), Index("playerId")]
)
data class PlayerImpactEntity(
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
