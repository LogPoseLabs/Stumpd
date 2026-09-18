package com.oreki.stumpd.domain.tournament

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The table, and the two places it could quietly lie.
 *
 * First, who won: the result is stored as a team *name*, so the comparison has to be exact — a side
 * called "Tie Breakers" must not be scored as a tie — and it must not be second-guessed by
 * comparing the innings totals, or a super over (which leaves them level by definition) would come
 * out as a draw. Second, net run rate: the stored overs figure is cricket notation, where 2.3 means
 * fifteen balls and not thirteen.
 */
class StandingsTest {

    private val teams = listOf(
        TeamRef("warriors", seed = 1, name = "Warriors"),
        TeamRef("strikers", seed = 2, name = "Strikers"),
    )

    // ── Over notation ───────────────────────────────────────────────────────────────────

    @Test
    fun `cricket over notation converts to balls, not to a tenth of an over`() {
        assertThat(oversNotationToBalls(2.3)).isEqualTo(15)
        assertThat(oversNotationToBalls(1.0)).isEqualTo(6)
        assertThat(oversNotationToBalls(0.5)).isEqualTo(5)
        assertThat(oversNotationToBalls(5.0)).isEqualTo(30)
        assertThat(oversNotationToBalls(0.0)).isEqualTo(0)
    }

    // ── Who won ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a winner is matched by name, case-insensitively, and mapped to an id`() {
        assertThat(outcome("Warriors")).isEqualTo(MatchOutcome.WIN to "warriors")
        assertThat(outcome("strikers")).isEqualTo(MatchOutcome.WIN to "strikers")
    }

    @Test
    fun `the literal TIE is a tie`() {
        assertThat(outcome("TIE")).isEqualTo(MatchOutcome.TIE to null)
    }

    @Test
    fun `a team called Tie Breakers wins its matches`() {
        // The existing captain-stats code uses `contains("Tie")` and would call this a draw.
        val result = resolveTournamentOutcome(
            winnerTeam = "Tie Breakers",
            teamAId = "tb", teamAName = "Tie Breakers",
            teamBId = "strikers", teamBName = "Strikers",
        )

        assertThat(result).isEqualTo(MatchOutcome.WIN to "tb")
    }

    @Test
    fun `a blank or unrecognised winner is a no result, not a guess`() {
        assertThat(outcome("")).isEqualTo(MatchOutcome.NO_RESULT to null)
        assertThat(outcome("Some Other Side")).isEqualTo(MatchOutcome.NO_RESULT to null)
    }

    // ── The table ───────────────────────────────────────────────────────────────────────

    @Test
    fun `a win is two points and a loss is none`() {
        val table = computeStandings(
            teams = teams,
            results = listOf(result(winner = "warriors", aRuns = 40, bRuns = 32)),
            allottedBallsPerInnings = 30,
        )

        val warriors = table.single { it.teamId == "warriors" }
        val strikers = table.single { it.teamId == "strikers" }
        assertThat(warriors.played to warriors.won).isEqualTo(1 to 1)
        assertThat(warriors.points).isEqualTo(2)
        assertThat(strikers.lost to strikers.points).isEqualTo(1 to 0)
        assertThat(table.first().teamId).isEqualTo("warriors")
    }

    @Test
    fun `a tie is a point each and counts as played for both`() {
        val table = computeStandings(
            teams = teams,
            results = listOf(result(winner = null, outcome = MatchOutcome.TIE, aRuns = 30, bRuns = 30)),
            allottedBallsPerInnings = 30,
        )

        table.forEach {
            assertThat(it.tied).isEqualTo(1)
            assertThat(it.points).isEqualTo(1)
            assertThat(it.won).isEqualTo(0)
            assertThat(it.lost).isEqualTo(0)
        }
    }

    @Test
    fun `a super-over win is a win, even though the scores finished level`() {
        // This is the case a totals comparison would get wrong: level scores, but a winner.
        val (outcome, winnerId) = resolveTournamentOutcome(
            winnerTeam = "Warriors",
            teamAId = "warriors", teamAName = "Warriors",
            teamBId = "strikers", teamBName = "Strikers",
        )
        val table = computeStandings(
            teams = teams,
            results = listOf(result(winner = winnerId, outcome = outcome, aRuns = 30, bRuns = 30)),
            allottedBallsPerInnings = 30,
        )

        assertThat(table.single { it.teamId == "warriors" }.won).isEqualTo(1)
        assertThat(table.single { it.teamId == "strikers" }.lost).isEqualTo(1)
        assertThat(table.none { it.tied > 0 }).isTrue()
    }

    @Test
    fun `an unplayed fixture contributes nothing`() {
        val table = computeStandings(teams, results = emptyList(), allottedBallsPerInnings = 30)

        assertThat(table).hasSize(2)
        table.forEach {
            assertThat(it.played).isEqualTo(0)
            assertThat(it.points).isEqualTo(0)
            assertThat(it.netRunRate).isNull()
        }
    }

    @Test
    fun `a no result counts as played but never moves net run rate`() {
        // A real save always resolves to a name or "TIE", so this path is only reachable through
        // corrupted data — but a no result must still behave like the real thing: it happened for
        // the table, and never happened for the rate. Paired with a real match so a regression
        // (crediting the no-result's runs) would show up as a wrong number, not just a null one.
        val table = computeStandings(
            teams = teams,
            results = listOf(
                result(winner = null, outcome = MatchOutcome.NO_RESULT, aRuns = 90, bRuns = 10),
                result(winner = "warriors", aRuns = 60, aBalls = 30, bRuns = 30, bBalls = 30),
            ),
            allottedBallsPerInnings = 30,
        )

        table.forEach { assertThat(it.played).isEqualTo(2) }
        assertThat(table.single { it.teamId == "warriors" }.noResult).isEqualTo(1)
        // Same +6 as the single-match case: the no result's 90/10 contributed nothing.
        assertThat(table.single { it.teamId == "warriors" }.netRunRate).isWithin(0.001).of(6.0)
        assertThat(table.single { it.teamId == "strikers" }.netRunRate).isWithin(0.001).of(-6.0)
    }

    // ── Net run rate ────────────────────────────────────────────────────────────────────

    @Test
    fun `net run rate is runs per over for, less runs per over against`() {
        val table = computeStandings(
            teams = teams,
            results = listOf(
                result(winner = "warriors", aRuns = 60, aBalls = 30, bRuns = 30, bBalls = 30),
            ),
            allottedBallsPerInnings = 30,
        )

        // 60 off 5 overs is 12 an over; conceding 30 off 5 is 6. Net +6.
        assertThat(table.single { it.teamId == "warriors" }.netRunRate).isWithin(0.001).of(6.0)
        assertThat(table.single { it.teamId == "strikers" }.netRunRate).isWithin(0.001).of(-6.0)
    }

    @Test
    fun `a side bowled out is charged the full quota of overs`() {
        // Dismissed for 20 in two overs. Charged at two overs its rate would be a flattering 10;
        // the rule — and this code — charges the full five.
        val table = computeStandings(
            teams = teams,
            results = listOf(
                result(
                    winner = "strikers",
                    aRuns = 20, aBalls = 12, aAllOut = true,
                    bRuns = 21, bBalls = 18,
                ),
            ),
            allottedBallsPerInnings = 30,
        )

        assertThat(table.single { it.teamId == "warriors" }.netRunRate)
            .isWithin(0.001).of((20 * 6.0 / 30) - (21 * 6.0 / 18))
    }

    @Test
    fun `equal points are separated by net run rate`() {
        val third = TeamRef("chasers", seed = 3, name = "Chasers")
        val table = computeStandings(
            teams = teams + third,
            results = listOf(
                // Warriors win narrowly; Strikers win by a mile. Same points, better rate.
                result(winner = "warriors", aId = "warriors", bId = "chasers", aRuns = 31, bRuns = 30),
                result(winner = "strikers", aId = "strikers", bId = "chasers", aRuns = 60, bRuns = 20),
            ),
            allottedBallsPerInnings = 30,
        )

        assertThat(table.map { it.teamId }.take(2)).containsExactly("strikers", "warriors").inOrder()
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────

    private fun outcome(winner: String) = resolveTournamentOutcome(
        winnerTeam = winner,
        teamAId = "warriors", teamAName = "Warriors",
        teamBId = "strikers", teamBName = "Strikers",
    )

    private fun result(
        winner: String?,
        outcome: MatchOutcome = if (winner == null) MatchOutcome.NO_RESULT else MatchOutcome.WIN,
        aId: String = "warriors",
        bId: String = "strikers",
        aRuns: Int = 0,
        aBalls: Int = 30,
        aAllOut: Boolean = false,
        bRuns: Int = 0,
        bBalls: Int = 30,
        bAllOut: Boolean = false,
    ) = TournamentMatchResult(
        matchId = "m-$aId-$bId",
        teamAId = aId,
        teamBId = bId,
        outcome = outcome,
        winnerTeamId = winner,
        teamARuns = aRuns,
        teamABalls = aBalls,
        teamAAllOut = aAllOut,
        teamBRuns = bRuns,
        teamBBalls = bBalls,
        teamBAllOut = bAllOut,
    )
}
