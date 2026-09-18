package com.oreki.stumpd.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.oreki.stumpd.data.local.entity.TournamentEntity
import com.oreki.stumpd.data.local.entity.TournamentFixtureEntity
import com.oreki.stumpd.data.local.entity.TournamentSquadPlayerEntity
import com.oreki.stumpd.data.local.entity.TournamentTeamEntity

/**
 * Reads and writes for a tournament and its parts.
 *
 * [replaceTournament] is the one to reach for when writing a tournament back: teams, squads and
 * fixtures are replaced wholesale rather than upserted, because generating fixtures or editing a
 * squad *removes* rows, and an insert-only merge would leave the old ones behind. The same
 * reasoning as `MatchDao.replaceFullMatch`.
 */
@Dao
interface TournamentDao {

    // ── Tournaments ─────────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTournament(tournament: TournamentEntity)

    @Query("SELECT * FROM tournaments WHERE tournamentId = :tournamentId")
    suspend fun tournament(tournamentId: String): TournamentEntity?

    @Query("SELECT * FROM tournaments WHERE groupId = :groupId ORDER BY createdAt DESC")
    suspend fun tournamentsForGroup(groupId: String): List<TournamentEntity>

    @Query("SELECT * FROM tournaments ORDER BY createdAt DESC")
    suspend fun allTournaments(): List<TournamentEntity>

    @Query("DELETE FROM tournaments WHERE tournamentId = :tournamentId")
    suspend fun deleteTournament(tournamentId: String)

    /** Drives sync: a tournament is uploaded when its own timestamp has moved on. */
    @Query("UPDATE tournaments SET updatedAt = :updatedAt WHERE tournamentId = :tournamentId")
    suspend fun touch(tournamentId: String, updatedAt: Long)

    @Query("UPDATE tournaments SET status = :status, updatedAt = :updatedAt WHERE tournamentId = :tournamentId")
    suspend fun setStatus(tournamentId: String, status: String, updatedAt: Long)

    // ── Teams and squads ────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTeams(teams: List<TournamentTeamEntity>)

    @Query("SELECT * FROM tournament_teams WHERE tournamentId = :tournamentId ORDER BY seed")
    suspend fun teams(tournamentId: String): List<TournamentTeamEntity>

    @Query("DELETE FROM tournament_teams WHERE tournamentId = :tournamentId")
    suspend fun deleteTeams(tournamentId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSquadPlayers(players: List<TournamentSquadPlayerEntity>)

    @Query("SELECT * FROM tournament_squad_players WHERE tournamentId = :tournamentId")
    suspend fun squads(tournamentId: String): List<TournamentSquadPlayerEntity>

    @Query("DELETE FROM tournament_squad_players WHERE tournamentId = :tournamentId")
    suspend fun deleteSquads(tournamentId: String)

    /** Which tournaments a player is already committed to — one squad per tournament is the rule. */
    @Query(
        "SELECT tournamentId FROM tournament_squad_players " +
            "WHERE playerId = :playerId AND tournamentId = :tournamentId"
    )
    suspend fun squadsContaining(tournamentId: String, playerId: String): List<String>

    // ── Fixtures ────────────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFixtures(fixtures: List<TournamentFixtureEntity>)

    @Query(
        "SELECT * FROM tournament_fixtures WHERE tournamentId = :tournamentId " +
            "ORDER BY stage, round, slot, leg"
    )
    suspend fun fixtures(tournamentId: String): List<TournamentFixtureEntity>

    @Query("SELECT * FROM tournament_fixtures WHERE fixtureId = :fixtureId")
    suspend fun fixture(fixtureId: String): TournamentFixtureEntity?

    @Query("DELETE FROM tournament_fixtures WHERE tournamentId = :tournamentId")
    suspend fun deleteFixtures(tournamentId: String)

    /** True once any fixture has been played: after that, regenerating would orphan a match. */
    @Query(
        "SELECT COUNT(*) FROM tournament_fixtures " +
            "WHERE tournamentId = :tournamentId AND matchId IS NOT NULL"
    )
    suspend fun playedFixtureCount(tournamentId: String): Int

    // ── Whole-aggregate writes ──────────────────────────────────────────────────────────

    /**
     * Writes a tournament and everything under it as the complete truth for its id.
     *
     * Delete-then-insert, because a squad edit or a regenerated schedule removes rows and an
     * upsert would leave the old ones in place.
     */
    @Transaction
    suspend fun replaceTournament(
        tournament: TournamentEntity,
        teams: List<TournamentTeamEntity>,
        squads: List<TournamentSquadPlayerEntity>,
        fixtures: List<TournamentFixtureEntity>,
    ) {
        deleteFixtures(tournament.tournamentId)
        deleteSquads(tournament.tournamentId)
        deleteTeams(tournament.tournamentId)
        upsertTournament(tournament)
        upsertTeams(teams)
        upsertSquadPlayers(squads)
        upsertFixtures(fixtures)
    }

    /** Removes a tournament and its children. Nothing here has a foreign key to cascade. */
    @Transaction
    suspend fun deleteTournamentGraph(tournamentId: String) {
        deleteFixtures(tournamentId)
        deleteSquads(tournamentId)
        deleteTeams(tournamentId)
        deleteTournament(tournamentId)
    }

    // ── Projections the standings need ──────────────────────────────────────────────────

    /**
     * Raw bowling spells for a set of matches.
     *
     * Deliberately raw: the overs figure is cricket notation, so it cannot be summed in SQL — the
     * conversion to balls happens in Kotlin, where `oversToBalls` gets it right.
     */
    @Query(
        "SELECT matchId, team, oversBowled FROM player_match_stats " +
            "WHERE role = 'BOWL' AND matchId IN (:matchIds)"
    )
    suspend fun bowlingSpells(matchIds: List<String>): List<BowlingSpellRow>
}

/** One bowler's spell in one match, for net-run-rate arithmetic. */
data class BowlingSpellRow(
    val matchId: String,
    val team: String,
    val oversBowled: Double,
)
