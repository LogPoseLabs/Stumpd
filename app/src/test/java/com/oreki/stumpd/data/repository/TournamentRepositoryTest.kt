package com.oreki.stumpd.data.repository

import com.google.common.truth.Truth.assertThat
import com.oreki.stumpd.data.local.dao.BowlingSpellRow
import com.oreki.stumpd.data.local.dao.TournamentDao
import com.oreki.stumpd.data.local.db.StumpdDb
import com.oreki.stumpd.data.local.entity.TournamentEntity
import com.oreki.stumpd.data.local.entity.TournamentFixtureEntity
import com.oreki.stumpd.data.local.entity.TournamentSquadPlayerEntity
import com.oreki.stumpd.data.local.entity.TournamentTeamEntity
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.model.PlayerMatchStats
import com.oreki.stumpd.domain.tournament.FixtureStatus
import com.oreki.stumpd.domain.tournament.TeamDraft
import com.oreki.stumpd.domain.tournament.TournamentFormat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The layer where the fixture arithmetic meets real rows.
 *
 * Two things are worth protecting here above all. A team's name is part of the primary key of its
 * players' stats rows, so renaming a side that has already played would orphan that match's
 * scorecard — which renders as empty innings with no error — hence the lock. And fixture ids are
 * deterministic, so regenerating a schedule after a match has been filed against one would hand
 * that id to a different pairing.
 */
@RunWith(RobolectricTestRunner::class)
class TournamentRepositoryTest {

    private val db: StumpdDb = mockk(relaxed = true)
    private val dao: TournamentDao = mockk(relaxed = true)
    private val matchRepository: MatchRepository = mockk(relaxed = true)
    private lateinit var repository: TournamentRepository

    private val tournament = TournamentEntity(
        tournamentId = "t1",
        groupId = "g1",
        name = "Sunday Cup",
        format = TournamentFormat.SINGLE_ROUND_ROBIN.name,
        teamCount = 2,
        squadSize = 2,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private val teams = listOf(
        TournamentTeamEntity(teamId = "t1:t1", tournamentId = "t1", name = "Warriors", seed = 1, captainPlayerId = "p1", updatedAt = 1L),
        TournamentTeamEntity(teamId = "t1:t2", tournamentId = "t1", name = "Strikers", seed = 2, captainPlayerId = "p3", updatedAt = 1L),
    )

    @Before
    fun setup() {
        every { db.tournamentDao() } returns dao
        coEvery { dao.tournament("t1") } returns tournament
        coEvery { dao.teams("t1") } returns teams
        coEvery { dao.squads("t1") } returns emptyList()
        coEvery { dao.fixtures("t1") } returns emptyList()
        coEvery { dao.playedFixtureCount("t1") } returns 0
        coEvery { matchRepository.getAllMatchesWithStats() } returns emptyList()
        repository = TournamentRepository(db, matchRepository, UnconfinedTestDispatcher())
    }

    private fun draft(name: String, captain: String, vararg players: String) =
        TeamDraft(
            name = name,
            playerIds = players.toList(),
            playerNames = players.map { "Player $it" },
            captainPlayerId = captain,
        )

    // ── Creating ────────────────────────────────────────────────────────────────────────

    @Test
    fun `creating a tournament mints placeholder teams to be named`() = runTest {
        val teamsSaved = slot<List<TournamentTeamEntity>>()
        coEvery { dao.replaceTournament(any(), capture(teamsSaved), any(), any()) } returns Unit

        val result = repository.create(
            groupId = "g1", name = "Sunday Cup",
            format = TournamentFormat.SINGLE_ROUND_ROBIN, teamCount = 4, squadSize = 5,
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(teamsSaved.captured).hasSize(4)
        assertThat(teamsSaved.captured.map { it.seed }).containsExactly(1, 2, 3, 4).inOrder()
        assertThat(teamsSaved.captured.map { it.name }).containsExactly("Team 1", "Team 2", "Team 3", "Team 4")
        // Deterministic ids, so a re-upload overwrites rather than duplicating.
        assertThat(teamsSaved.captured.map { it.teamId }.distinct()).hasSize(4)
    }

    @Test
    fun `an impossible setup is refused with a sentence, not a crash`() = runTest {
        val tooFew = repository.create("g1", "Cup", TournamentFormat.KNOCKOUT, teamCount = 1, squadSize = 5)
        assertThat(tooFew.isFailure).isTrue()
        assertThat((tooFew.exceptionOrNull() as TournamentException).problem.code)
            .isEqualTo("TOO_FEW_TEAMS")

        val nameless = repository.create("g1", "  ", TournamentFormat.KNOCKOUT, teamCount = 4, squadSize = 5)
        assertThat((nameless.exceptionOrNull() as TournamentException).problem.code).isEqualTo("NO_NAME")
    }

    // ── Squads ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `saving squads records the captain's name alongside the id`() = runTest {
        val saved = slot<List<TournamentTeamEntity>>()
        coEvery { dao.upsertTeams(capture(saved)) } returns Unit

        val result = repository.saveTeams(
            "t1",
            listOf(draft("Warriors", "p1", "p1", "p2"), draft("Strikers", "p3", "p3", "p4")),
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(saved.captured.single { it.name == "Warriors" }.captainName).isEqualTo("Player p1")
    }

    @Test
    fun `a player cannot be in two squads`() = runTest {
        val result = repository.saveTeams(
            "t1",
            listOf(draft("Warriors", "p1", "p1", "p2"), draft("Strikers", "p2", "p2", "p4")),
        )

        assertThat((result.exceptionOrNull() as TournamentException).problem.code)
            .isEqualTo("PLAYER_IN_TWO_SQUADS")
    }

    @Test
    fun `a team that has played cannot be renamed`() = runTest {
        coEvery { dao.playedFixtureCount("t1") } returns 1

        val result = repository.saveTeams(
            "t1",
            listOf(draft("Renamed", "p1", "p1", "p2"), draft("Strikers", "p3", "p3", "p4")),
        )

        val problem = (result.exceptionOrNull() as TournamentException).problem
        assertThat(problem.code).isEqualTo("NAME_LOCKED")
        assertThat(problem.message).contains("Warriors")
    }

    @Test
    fun `squads can still be edited after a match, as long as the names stand`() = runTest {
        coEvery { dao.playedFixtureCount("t1") } returns 1

        val result = repository.saveTeams(
            "t1",
            listOf(draft("Warriors", "p1", "p1", "p9"), draft("Strikers", "p3", "p3", "p4")),
        )

        assertThat(result.isSuccess).isTrue()
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────────────

    @Test
    fun `generating fixtures produces a schedule and activates the tournament`() = runTest {
        coEvery { dao.squads("t1") } returns listOf(
            TournamentSquadPlayerEntity(tournamentId = "t1", teamId = "t1:t1", playerId = "p1", battingOrder = 1),
            TournamentSquadPlayerEntity(tournamentId = "t1", teamId = "t1:t1", playerId = "p2", battingOrder = 2),
            TournamentSquadPlayerEntity(tournamentId = "t1", teamId = "t1:t2", playerId = "p3", battingOrder = 1),
            TournamentSquadPlayerEntity(tournamentId = "t1", teamId = "t1:t2", playerId = "p4", battingOrder = 2),
        )
        val fixtures = slot<List<TournamentFixtureEntity>>()
        coEvery { dao.upsertFixtures(capture(fixtures)) } returns Unit

        val result = repository.generateFixtures("t1")

        assertThat(result.getOrNull()).isEqualTo(1) // two teams, one match
        assertThat(fixtures.captured.single().label).isEqualTo("Match 1")
        assertThat(fixtures.captured.single().status).isEqualTo(FixtureStatus.PENDING.name)
        // Deterministic id: stage, round, slot and leg.
        assertThat(fixtures.captured.single().fixtureId).isEqualTo("t1:LEAGUE:1:0:1")
    }

    @Test
    fun `fixtures cannot be regenerated once a match has been filed against one`() = runTest {
        coEvery { dao.playedFixtureCount("t1") } returns 1

        val result = repository.generateFixtures("t1")

        assertThat((result.exceptionOrNull() as TournamentException).problem.code)
            .isEqualTo("FIXTURES_LOCKED")
    }

    @Test
    fun `a schedule needs every team named and captained first`() = runTest {
        coEvery { dao.teams("t1") } returns teams.map { it.copy(captainPlayerId = null) }

        val result = repository.generateFixtures("t1")

        assertThat((result.exceptionOrNull() as TournamentException).problem.code)
            .isEqualTo("TEAMS_INCOMPLETE")
    }

    // ── The table ───────────────────────────────────────────────────────────────────────

    @Test
    fun `a played fixture moves the table, with net run rate from the balls bowled`() = runTest {
        val fixture = TournamentFixtureEntity(
            fixtureId = "t1:LEAGUE:1:0:1", tournamentId = "t1", stage = "LEAGUE",
            round = 1, slot = 0, leg = 1,
            homeTeamId = "t1:t1", awayTeamId = "t1:t2",
            label = "Match 1", status = FixtureStatus.COMPLETED.name,
            matchId = "m1", updatedAt = 1L,
        )
        coEvery { dao.fixtures("t1") } returns listOf(fixture)
        coEvery { matchRepository.getAllMatchesWithStats() } returns listOf(
            MatchHistory(
                id = "m1",
                team1Name = "Warriors", team2Name = "Strikers",
                firstInningsRuns = 40, firstInningsWickets = 1,
                secondInningsRuns = 25, secondInningsWickets = 3,
                winnerTeam = "Warriors", winningMargin = "15 runs",
                team1Id = "t1:t1", team2Id = "t1:t2",
                firstInningsBatting = listOf(
                    PlayerMatchStats(id = "p1", name = "Player p1", team = "Warriors", role = "BAT", runs = 40, ballsFaced = 30),
                ),
            )
        )
        coEvery { dao.bowlingSpells(any()) } returns listOf(
            // Each side bowled its full five overs at the other.
            BowlingSpellRow(matchId = "m1", team = "Strikers", oversBowled = 5.0),
            BowlingSpellRow(matchId = "m1", team = "Warriors", oversBowled = 5.0),
        )

        val table = repository.standings("t1").getValue(0)

        val warriors = table.single { it.teamId == "t1:t1" }
        val strikers = table.single { it.teamId == "t1:t2" }
        assertThat(warriors.won to warriors.points).isEqualTo(1 to 2)
        assertThat(strikers.lost to strikers.points).isEqualTo(1 to 0)
        // 40 off 5 overs is 8 an over, conceding 25 off 5 is 5. Net +3.
        assertThat(warriors.netRunRate).isWithin(0.01).of(3.0)
        assertThat(strikers.netRunRate).isWithin(0.01).of(-3.0)
        assertThat(table.first().teamId).isEqualTo("t1:t1")
    }

    @Test
    fun `an unplayed tournament has an empty table rather than no table`() = runTest {
        val table = repository.standings("t1").getValue(0)

        assertThat(table.map { it.teamName }).containsExactly("Warriors", "Strikers")
        table.forEach { assertThat(it.played).isEqualTo(0) }
    }

    @Test
    fun `filing a match against a fixture completes it`() = runTest {
        val fixture = TournamentFixtureEntity(
            fixtureId = "t1:LEAGUE:1:0:1", tournamentId = "t1", stage = "LEAGUE",
            round = 1, slot = 0, leg = 1,
            homeTeamId = "t1:t1", awayTeamId = "t1:t2",
            label = "Match 1", status = FixtureStatus.PENDING.name, updatedAt = 1L,
        )
        coEvery { dao.fixture("t1:LEAGUE:1:0:1") } returns fixture
        val saved = slot<List<TournamentFixtureEntity>>()
        coEvery { dao.upsertFixtures(capture(saved)) } returns Unit

        val result = repository.recordFixtureResult("t1:LEAGUE:1:0:1", "m1")

        assertThat(result.isSuccess).isTrue()
        assertThat(saved.captured.single().matchId).isEqualTo("m1")
        assertThat(saved.captured.single().status).isEqualTo(FixtureStatus.COMPLETED.name)
    }

    @Test
    fun `a tournament bookkeeping failure never surfaces as an exception`() = runTest {
        // The caller runs this after the match is already saved: a failure here must never be
        // allowed to look like the match failed.
        coEvery { dao.fixture(any()) } throws IllegalStateException("db gone")

        val result = repository.recordFixtureResult("whatever", "m1")

        assertThat(result.isFailure).isTrue()
    }

    // ── Stats ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `matchesFor returns only this tournament's own matches`() = runTest {
        fun match(id: String, tournamentId: String?) = MatchHistory(
            id = id, team1Name = "Warriors", team2Name = "Strikers",
            firstInningsRuns = 40, firstInningsWickets = 1,
            secondInningsRuns = 25, secondInningsWickets = 3,
            winnerTeam = "Warriors", winningMargin = "15 runs",
            tournamentId = tournamentId,
        )
        coEvery { matchRepository.getAllMatchesWithStats() } returns listOf(
            match("m1", "t1"), match("m2", "t2"), match("m3", null),
        )

        val matches = repository.matchesFor("t1")

        assertThat(matches.map { it.id }).containsExactly("m1")
    }

    @Test
    fun `squads are read back in batting order`() = runTest {
        coEvery { dao.squads("t1") } returns listOf(
            TournamentSquadPlayerEntity(tournamentId = "t1", teamId = "t1:t1", playerId = "p2", battingOrder = 2),
            TournamentSquadPlayerEntity(tournamentId = "t1", teamId = "t1:t1", playerId = "p1", battingOrder = 1),
        )

        val bundle = repository.bundle("t1")

        assertThat(bundle?.squads?.getValue("t1:t1")).containsExactly("p1", "p2").inOrder()
    }
}
