package com.oreki.stumpd.domain.tournament

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Filling in a bracket, and refusing a tournament that can't work.
 *
 * The progression rules are written to be run on every read rather than on the whistle, so the
 * tests check they are idempotent and that they hold back a semi-final until its group has actually
 * finished — a table that is still moving must not be allowed to seed anything.
 */
class ProgressionAndValidationTest {

    // ── Progression ─────────────────────────────────────────────────────────────────────

    @Test
    fun `a final fills in as soon as both semi-finals have winners`() {
        val fixtures = listOf(
            played(round = 1, slot = 0, winner = "a"),
            played(round = 1, slot = 1, winner = "b"),
            awaiting(round = 2, slot = 0, home = FixtureSource.WinnerOf(1, 0), away = FixtureSource.WinnerOf(1, 1)),
        )

        val resolved = resolveFixtureSlots(fixtures, emptyMap(), emptySet())

        val final = resolved.single { it.round == 2 }
        assertThat(final.homeTeamId).isEqualTo("a")
        assertThat(final.awayTeamId).isEqualTo("b")
        assertThat(final.status).isEqualTo(FixtureStatus.PENDING)
    }

    @Test
    fun `half a result fills in half a fixture and no more`() {
        val fixtures = listOf(
            played(round = 1, slot = 0, winner = "a"),
            awaiting(round = 1, slot = 1, home = null, away = null).copy(
                homeTeamId = "c", awayTeamId = "d", status = FixtureStatus.PENDING,
            ),
            awaiting(round = 2, slot = 0, home = FixtureSource.WinnerOf(1, 0), away = FixtureSource.WinnerOf(1, 1)),
        )

        val final = resolveFixtureSlots(fixtures, emptyMap(), emptySet()).single { it.round == 2 }

        assertThat(final.homeTeamId).isEqualTo("a")
        assertThat(final.awayTeamId).isNull()
        assertThat(final.status).isEqualTo(FixtureStatus.AWAITING_TEAMS)
    }

    @Test
    fun `a group's qualifiers wait for the whole group to finish`() {
        val standings = mapOf(
            1 to listOf(row("a", "A Team"), row("b", "B Team")),
            2 to listOf(row("c", "C Team"), row("d", "D Team")),
        )
        val semi = awaiting(
            round = 1, slot = 0,
            home = FixtureSource.PoolPosition(1, 1),
            away = FixtureSource.PoolPosition(2, 2),
        )

        // Group 1 is done, group 2 isn't: only half the fixture can be filled.
        val partly = resolveFixtureSlots(listOf(semi), standings, completedPools = setOf(1)).single()
        assertThat(partly.homeTeamId).isEqualTo("a")
        assertThat(partly.awayTeamId).isNull()

        val fully = resolveFixtureSlots(listOf(semi), standings, completedPools = setOf(1, 2)).single()
        assertThat(fully.homeTeamId to fully.awayTeamId).isEqualTo("a" to "d")
        assertThat(fully.status).isEqualTo(FixtureStatus.PENDING)
    }

    @Test
    fun `resolving twice changes nothing the second time`() {
        val fixtures = listOf(
            played(round = 1, slot = 0, winner = "a"),
            played(round = 1, slot = 1, winner = "b"),
            awaiting(round = 2, slot = 0, home = FixtureSource.WinnerOf(1, 0), away = FixtureSource.WinnerOf(1, 1)),
        )

        val once = resolveFixtureSlots(fixtures, emptyMap(), emptySet())
        val twice = resolveFixtureSlots(once, emptyMap(), emptySet())

        assertThat(twice).isEqualTo(once)
    }

    @Test
    fun `a knockout bye advances its team without a match being played`() {
        val bracket = listOf(
            pending(round = 1, slot = 0, home = "top", away = null),
            awaiting(round = 2, slot = 0, home = FixtureSource.WinnerOf(1, 0), away = FixtureSource.WinnerOf(1, 1)),
        )

        val advanced = autoAdvanceByes(bracket)
        val bye = advanced.first()
        assertThat(bye.status).isEqualTo(FixtureStatus.BYE)
        assertThat(bye.winnerTeamId).isEqualTo("top")

        // And the next round can see it.
        val resolved = resolveFixtureSlots(advanced, emptyMap(), emptySet())
        assertThat(resolved.single { it.round == 2 }.homeTeamId).isEqualTo("top")
    }

    @Test
    fun `a round-robin bye is just a round off, not an advance`() {
        val league = listOf(
            pending(round = 1, slot = 0, home = "solo", away = null).copy(stage = FixtureStage.LEAGUE),
        )

        assertThat(autoAdvanceByes(league).single().winnerTeamId).isNull()
    }

    @Test
    fun `a tournament is complete when everything is played or byed`() {
        assertThat(tournamentIsComplete(emptyList())).isFalse()
        assertThat(
            tournamentIsComplete(
                listOf(played(1, 0, "a"), pending(1, 1, "c", "d")),
            ),
        ).isFalse()
        assertThat(
            tournamentIsComplete(
                listOf(played(1, 0, "a"), pending(1, 1, "c", null).copy(status = FixtureStatus.BYE)),
            ),
        ).isTrue()
    }

    // ── Validation ──────────────────────────────────────────────────────────────────────

    @Test
    fun `a sensible setup is accepted`() {
        assertThat(validateSetup(TournamentFormat.SINGLE_ROUND_ROBIN, teamCount = 6, squadSize = 5))
            .isNull()
        assertThat(
            validateSetup(
                TournamentFormat.GROUPS_KNOCKOUT,
                teamCount = 6, squadSize = 5, poolCount = 2, advancePerPool = 2,
            ),
        ).isNull()
    }

    @Test
    fun `a tournament needs at least two teams and a squad worth the name`() {
        assertThat(validateSetup(TournamentFormat.KNOCKOUT, 1, 5)?.code).isEqualTo("TOO_FEW_TEAMS")
        assertThat(validateSetup(TournamentFormat.KNOCKOUT, 4, 1)?.code).isEqualTo("SQUAD_TOO_SMALL")
        assertThat(validateSetup(TournamentFormat.KNOCKOUT, 40, 5)?.code).isEqualTo("TOO_MANY_TEAMS")
    }

    @Test
    fun `groups have to be able to hold teams and to decide something`() {
        assertThat(
            validateSetup(TournamentFormat.GROUPS_KNOCKOUT, 4, 5, poolCount = 1, advancePerPool = 1)?.code,
        ).isEqualTo("TOO_FEW_POOLS")
        assertThat(
            validateSetup(TournamentFormat.GROUPS_KNOCKOUT, 3, 5, poolCount = 2, advancePerPool = 1)?.code,
        ).isEqualTo("POOLS_TOO_THIN")
        // Four teams, two groups, two through each: the groups decide nothing.
        assertThat(
            validateSetup(TournamentFormat.GROUPS_KNOCKOUT, 4, 5, poolCount = 2, advancePerPool = 2)?.code,
        ).isEqualTo("EVERYONE_QUALIFIES")
    }

    @Test
    fun `complete squads with captains pass`() {
        assertThat(validateTeams(listOf(draft("Warriors", "p1", "p2"), draft("Strikers", "p3", "p4")), 2))
            .isNull()
    }

    @Test
    fun `a team cannot be called TIE, because that is how a tie is recorded`() {
        assertThat(validateTeams(listOf(draft("tie", "p1", "p2")), 2)?.code)
            .isEqualTo("RESERVED_NAME")
    }

    @Test
    fun `two teams cannot share a name, since the result is stored as one`() {
        val teams = listOf(draft("Warriors", "p1", "p2"), draft("warriors", "p3", "p4"))

        assertThat(validateTeams(teams, 2)?.code).isEqualTo("DUPLICATE_TEAM_NAME")
    }

    @Test
    fun `a player cannot turn out for two teams`() {
        val teams = listOf(draft("Warriors", "p1", "p2"), draft("Strikers", "p2", "p3"))

        val problem = validateTeams(teams, 2)
        assertThat(problem?.code).isEqualTo("PLAYER_IN_TWO_SQUADS")
        assertThat(problem?.message).contains("Warriors")
        assertThat(problem?.message).contains("Strikers")
    }

    @Test
    fun `two players sharing a name is refused, because scoring would merge them`() {
        val teams = listOf(
            TeamDraft("Warriors", listOf("p1", "p2"), listOf("Kushal", "Gokul"), "p1"),
            TeamDraft("Strikers", listOf("p3", "p4"), listOf("kushal", "Ajith"), "p3"),
        )

        assertThat(validateTeams(teams, 2)?.code).isEqualTo("DUPLICATE_PLAYER_NAME")
    }

    @Test
    fun `an incomplete squad or an absent captain is not a save-time error`() {
        // The whole point of the split: saving one team while the rest of the tournament is
        // still a placeholder must succeed, or the very first team could never be saved. This is
        // the exact shape of the bug report — Team 1 filled and saved while Team 2 and Team 3
        // are still empty placeholders — pinned so it can't regress.
        assertThat(validateTeams(listOf(draft("Warriors", "p1")), 2)).isNull()
        assertThat(
            validateTeams(
                listOf(TeamDraft("Warriors", listOf("p1", "p2"), captainPlayerId = null)),
                2,
            ),
        ).isNull()
        assertThat(
            validateTeams(
                listOf(
                    TeamDraft("Warriors", listOf("p1", "p2"), captainPlayerId = "p1"),
                    TeamDraft("Team 2", emptyList(), captainPlayerId = null),
                    TeamDraft("Team 3", emptyList(), captainPlayerId = null),
                ),
                3,
            ),
        ).isNull()
    }

    @Test
    fun `a captain has to be drawn from their own squad, complete or not`() {
        assertThat(
            validateTeams(
                listOf(TeamDraft("Warriors", listOf("p1", "p2"), captainPlayerId = "pX")),
                2,
            )?.code,
        ).isEqualTo("CAPTAIN_NOT_IN_SQUAD")
    }

    @Test
    fun `a squad bigger than the tournament allows is refused even mid-setup`() {
        assertThat(validateTeams(listOf(draft("Warriors", "p1", "p2", "p3")), 2)?.code)
            .isEqualTo("SQUAD_TOO_BIG")
    }

    @Test
    fun `readiness to play requires every team full and captained`() {
        assertThat(
            validateReadyToPlay(listOf(draft("Warriors", "p1", "p2"), draft("Strikers", "p3", "p4")), 2),
        ).isNull()
        assertThat(validateReadyToPlay(listOf(draft("Warriors", "p1")), 2)?.code)
            .isEqualTo("TEAMS_INCOMPLETE")
        assertThat(
            validateReadyToPlay(
                listOf(TeamDraft("Warriors", listOf("p1", "p2"), captainPlayerId = null)),
                2,
            )?.code,
        ).isEqualTo("TEAMS_INCOMPLETE")
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────

    private fun draft(name: String, vararg players: String) =
        TeamDraft(name, players.toList(), players.toList().map { "Player $it" }, players.first())

    private fun row(teamId: String, name: String) =
        StandingsRow(teamId = teamId, teamName = name, poolOrdinal = 0)

    private fun played(round: Int, slot: Int, winner: String) = TournamentFixture(
        fixtureId = "f-$round-$slot", stage = FixtureStage.KNOCKOUT, poolOrdinal = 0,
        round = round, slot = slot, leg = 1,
        homeTeamId = winner, awayTeamId = "other", label = "Match",
        status = FixtureStatus.COMPLETED, matchId = "m-$round-$slot", winnerTeamId = winner,
    )

    private fun pending(round: Int, slot: Int, home: String?, away: String?) = TournamentFixture(
        fixtureId = "f-$round-$slot", stage = FixtureStage.KNOCKOUT, poolOrdinal = 0,
        round = round, slot = slot, leg = 1,
        homeTeamId = home, awayTeamId = away, label = "Match", status = FixtureStatus.PENDING,
    )

    private fun awaiting(
        round: Int,
        slot: Int,
        home: FixtureSource?,
        away: FixtureSource?,
    ) = TournamentFixture(
        fixtureId = "f-$round-$slot", stage = FixtureStage.KNOCKOUT, poolOrdinal = 0,
        round = round, slot = slot, leg = 1,
        homeTeamId = null, awayTeamId = null, homeSource = home, awaySource = away,
        label = "Match", status = FixtureStatus.AWAITING_TEAMS,
    )
}
