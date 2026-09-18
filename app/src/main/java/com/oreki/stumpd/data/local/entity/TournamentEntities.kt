package com.oreki.stumpd.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A tournament and its parts.
 *
 * Everything is scoped to `tournamentId`: teams are created with a tournament and die with it, which
 * is what lets a team's name be locked once it has played. That one rule closes the largest hazard
 * in the feature — a team's name is part of the primary key of `player_match_stats`, so renaming a
 * side after a match would orphan that match's stats and the scorecard would render empty innings
 * with no error at all.
 *
 * All four tables live under one group, so the Firestore rules that already cover a group document
 * and its subcollections cover these too: owner writes, member reads, enforced server-side, with no
 * new rules to deploy.
 */

@Entity(
    tableName = "tournaments",
    indices = [Index("groupId")],
)
data class TournamentEntity(
    @PrimaryKey val tournamentId: String,
    val groupId: String,
    val name: String,
    /** A `TournamentFormat` name. Stored as text and parsed defensively. */
    val format: String,
    val teamCount: Int,
    val squadSize: Int,
    val poolCount: Int = 0,
    val advancePerPool: Int = 0,
    val pointsWin: Int = 2,
    val pointsTie: Int = 1,
    val pointsLoss: Int = 0,
    val pointsNoResult: Int = 1,
    /** `DRAFT` until fixtures exist, then `ACTIVE`, then `COMPLETE`. */
    val status: String = "DRAFT",
    /**
     * The rules every fixture is played under, snapshotted from the group's defaults when the
     * tournament is created — so a mid-season change to the group can't make the table's net run
     * rates incomparable.
     */
    val matchSettingsJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "tournament_teams",
    indices = [Index("tournamentId"), Index(value = ["tournamentId", "seed"], unique = true)],
)
data class TournamentTeamEntity(
    /** Deterministic — `"$tournamentId:t$seed"` — so a re-upload overwrites rather than duplicates. */
    @PrimaryKey val teamId: String,
    val tournamentId: String,
    val name: String,
    val shortName: String? = null,
    /**
     * The captain, by id.
     *
     * Carried explicitly because the rest of the app recovers captaincy by *parsing* the team name
     * for "'s Team" — which is exactly why a custom team name loses its captain today.
     */
    val captainPlayerId: String? = null,
    val captainName: String? = null,
    val seed: Int,
    /** 0 when the format has no pools; otherwise 1..poolCount. */
    val poolOrdinal: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "tournament_squad_players",
    primaryKeys = ["teamId", "playerId"],
    indices = [Index("tournamentId"), Index("playerId")],
)
data class TournamentSquadPlayerEntity(
    val tournamentId: String,
    val teamId: String,
    val playerId: String,
    val battingOrder: Int = 0,
)

@Entity(
    tableName = "tournament_fixtures",
    indices = [Index("tournamentId"), Index("matchId")],
)
data class TournamentFixtureEntity(
    /** Deterministic — `"$tournamentId:$stage:$round:$slot:$leg"`. */
    @PrimaryKey val fixtureId: String,
    val tournamentId: String,
    val stage: String,
    val poolOrdinal: Int = 0,
    val round: Int,
    val slot: Int,
    val leg: Int = 1,
    val homeTeamId: String? = null,
    val awayTeamId: String? = null,
    /** An encoded `FixtureSource` — "winner of round 2 slot 0", "second in pool 1". */
    val homeSourceRef: String? = null,
    val awaySourceRef: String? = null,
    val label: String,
    val status: String,
    val matchId: String? = null,
    /** A cache. The table and the bracket are always re-derived from the match rows on read. */
    val winnerTeamId: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)
