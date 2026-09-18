package com.oreki.stumpd.domain.match

import com.google.common.truth.Truth.assertThat
import com.oreki.stumpd.domain.model.DeliveryUI
import com.oreki.stumpd.domain.model.FallOfWicket
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.model.MatchSettings
import com.oreki.stumpd.domain.model.Partnership
import com.oreki.stumpd.domain.model.PlayerImpact
import com.oreki.stumpd.domain.model.PlayerMatchStats
import org.junit.Test

/**
 * Handing one player's match to someone else.
 *
 * A player's identity appears in eleven different places in a saved match, and missing any one of
 * them leaves the scorecard half-corrected — so the single test that matters most here is the one
 * that walks all of them. The figures must come out *identical*: a substitution changes who owns
 * a performance, never the performance.
 */
class SubstitutePlayerOpTest {

    private val settings = MatchSettings(totalOvers = 5, maxPlayersPerTeam = 5)

    private fun match() = MatchHistory(
        id = "m1",
        team1Name = "Strikers",
        team2Name = "Chasers",
        jokerPlayerName = "Kushal",
        team1CaptainName = "Kushal",
        team2CaptainName = "Muttu",
        firstInningsRuns = 30,
        firstInningsWickets = 1,
        secondInningsRuns = 20,
        secondInningsWickets = 0,
        winnerTeam = "Strikers",
        winningMargin = "10 runs",
        firstInningsBatting = listOf(
            PlayerMatchStats(
                id = "kushal", name = "Kushal", team = "Strikers", role = "BAT",
                runs = 18, ballsFaced = 12, isOut = true, dismissalType = "CAUGHT",
                bowlerName = "Muttu", fielderName = "Madhu",
            ),
            PlayerMatchStats(id = "gokul", name = "Gokul", team = "Strikers", role = "BAT", runs = 12, ballsFaced = 10),
        ),
        firstInningsBowling = listOf(
            PlayerMatchStats(id = "muttu", name = "Muttu", team = "Chasers", role = "BOWL", wickets = 1, runsConceded = 18, oversBowled = 2.0),
            PlayerMatchStats(id = "madhu", name = "Madhu", team = "Chasers", role = "BOWL", runsConceded = 12, oversBowled = 3.0, catches = 1),
        ),
        secondInningsBatting = listOf(
            PlayerMatchStats(id = "muttu", name = "Muttu", team = "Chasers", role = "BAT", runs = 20, ballsFaced = 18),
        ),
        secondInningsBowling = listOf(
            PlayerMatchStats(id = "kushal", name = "Kushal", team = "Strikers", role = "BOWL", wickets = 0, runsConceded = 20, oversBowled = 5.0),
        ),
        firstInningsPartnerships = listOf(
            Partnership("Kushal", "Gokul", runs = 18, balls = 14, batsman1Runs = 12, batsman2Runs = 6, isActive = false),
        ),
        firstInningsFallOfWickets = listOf(
            FallOfWicket(
                batsmanName = "Kushal", runs = 18, overs = 2.2, wicketNumber = 1,
                dismissalType = "CAUGHT", bowlerName = "Muttu", fielderName = "Madhu",
            ),
        ),
        playerOfTheMatchId = "kushal",
        playerOfTheMatchName = "Kushal",
        playerOfTheMatchTeam = "Strikers",
        playerImpacts = listOf(
            PlayerImpact(id = "kushal", name = "Kushal", team = "Strikers", impact = 40.0, summary = "18(12)"),
        ),
        allDeliveries = listOf(
            DeliveryUI(1, 1, 1, "4", runs = 4, strikerName = "Kushal", nonStrikerName = "Gokul", bowlerName = "Muttu"),
            DeliveryUI(1, 1, 2, "1 + RO (Kushal @ S)", runs = 1, strikerName = "Kushal", nonStrikerName = "Gokul", bowlerName = "Muttu"),
        ),
        matchSettings = settings,
    )

    private fun substitute(from: String = "Kushal", toId: String = "p-prasanna", to: String = "Prasanna") =
        MatchCorrection.SubstitutePlayer(fromName = from, toPlayerId = toId, toName = to)

    private fun applied(match: MatchHistory, correction: MatchCorrection): MatchHistory {
        val outcome = MatchCorrectionEngine.apply(match, listOf(correction), settings)
        assertThat(outcome).isInstanceOf(CorrectionOutcome.Applied::class.java)
        return (outcome as CorrectionOutcome.Applied).after
    }

    private fun rejected(match: MatchHistory, correction: MatchCorrection): List<String> {
        val outcome = MatchCorrectionEngine.apply(match, listOf(correction), settings)
        assertThat(outcome).isInstanceOf(CorrectionOutcome.Rejected::class.java)
        return (outcome as CorrectionOutcome.Rejected).errors.map { it.code }
    }

    @Test
    fun `every place the old name appeared now names the substitute`() {
        val after = applied(match(), substitute())

        // Batting row: id and name.
        val batting = after.firstInningsBatting.single { it.name == "Prasanna" }
        assertThat(batting.id).isEqualTo("p-prasanna")
        assertThat(batting.runs).isEqualTo(18)
        // Bowling row in the other innings.
        assertThat(after.secondInningsBowling.single().name).isEqualTo("Prasanna")
        assertThat(after.secondInningsBowling.single().id).isEqualTo("p-prasanna")
        // Partnership, fall of wickets, joker, captain, award.
        assertThat(after.firstInningsPartnerships.single().batsman1Name).isEqualTo("Prasanna")
        assertThat(after.firstInningsFallOfWickets.single().batsmanName).isEqualTo("Prasanna")
        assertThat(after.jokerPlayerName).isEqualTo("Prasanna")
        assertThat(after.team1CaptainName).isEqualTo("Prasanna")
        // The delivery trail: the three name fields *and* the name inside the outcome string.
        assertThat(after.allDeliveries.map { it.strikerName }).containsExactly("Prasanna", "Prasanna")
        assertThat(after.allDeliveries.map { it.outcome })
            .containsExactly("4", "1 + RO (Prasanna @ S)").inOrder()

        assertThat(after.playerImpacts.map { it.name }).doesNotContain("Kushal")
    }

    @Test
    fun `the old name is gone from the match entirely`() {
        val after = applied(match(), substitute())

        val remaining = com.oreki.stumpd.data.sync.MatchGroupAdoption.collectPlayerNames(after)
        assertThat(remaining).doesNotContain("Kushal")
        assertThat(remaining).contains("Prasanna")
    }

    @Test
    fun `nothing numeric changes — a substitution moves ownership, not performance`() {
        val before = match()
        val after = applied(before, substitute())

        assertThat(after.firstInningsRuns).isEqualTo(before.firstInningsRuns)
        assertThat(after.firstInningsWickets).isEqualTo(before.firstInningsWickets)
        assertThat(after.secondInningsRuns).isEqualTo(before.secondInningsRuns)
        assertThat(after.winnerTeam).isEqualTo(before.winnerTeam)
        assertThat(after.winningMargin).isEqualTo(before.winningMargin)

        val beforeFigures = before.firstInningsBatting.map { it.runs to it.ballsFaced }
        val afterFigures = after.firstInningsBatting.map { it.runs to it.ballsFaced }
        assertThat(afterFigures).isEqualTo(beforeFigures)

        // Impacts are re-derived, so the score attaches to the new name at the same value.
        val impact = after.playerImpacts.single { it.name == "Prasanna" }
        assertThat(impact.runs).isEqualTo(18)
    }

    @Test
    fun `the dismissal credits still point at the right bowler and fielder`() {
        val after = applied(match(), substitute())

        val batting = after.firstInningsBatting.single { it.name == "Prasanna" }
        assertThat(batting.bowlerName).isEqualTo("Muttu")
        assertThat(batting.fielderName).isEqualTo("Madhu")
        assertThat(after.firstInningsFallOfWickets.single().bowlerName).isEqualTo("Muttu")
    }

    @Test
    fun `the award is re-derived and can never still name the substituted player`() {
        val after = applied(match(), substitute())

        // The stored award is recomputed from the figures rather than renamed in place, so it
        // lands on whoever the formula actually ranks first — and the old name cannot survive.
        val top = after.playerImpacts.first()
        assertThat(after.playerOfTheMatchName).isEqualTo(top.name)
        assertThat(after.playerOfTheMatchId).isEqualTo(top.id)
        assertThat(after.playerOfTheMatchName).isNotEqualTo("Kushal")
    }

    @Test
    fun `when the substituted player is the best performer, the award carries their new name`() {
        val topScorer = match().let { m ->
            m.copy(
                firstInningsBatting = m.firstInningsBatting.map {
                    if (it.name == "Kushal") it.copy(runs = 40, ballsFaced = 14, fours = 4, sixes = 2) else it
                },
            )
        }

        val after = applied(topScorer, substitute())

        assertThat(after.playerOfTheMatchName).isEqualTo("Prasanna")
        assertThat(after.playerOfTheMatchId).isEqualTo("p-prasanna")
    }

    @Test
    fun `substituting for someone who already played is refused`() {
        // Merging two scorecard lines would mean inventing how their spells combined.
        assertThat(rejected(match(), substitute(to = "Gokul", toId = "gokul")))
            .containsExactly("WOULD_MERGE_PLAYERS")
    }

    @Test
    fun `substituting a player who isn't in the match is refused`() {
        assertThat(rejected(match(), substitute(from = "Nobody")))
            .containsExactly("PLAYER_NOT_IN_MATCH")
    }

    @Test
    fun `substituting a player for themselves is refused rather than silently doing nothing`() {
        assertThat(rejected(match(), substitute(to = "kushal ", toId = "kushal")))
            .containsExactly("NO_OP")
    }

    @Test
    fun `the match keeps its identity, date and group`() {
        val before = match()
        val after = applied(before, substitute())

        assertThat(after.id).isEqualTo(before.id)
        assertThat(after.matchDate).isEqualTo(before.matchDate)
        assertThat(after.groupId).isEqualTo(before.groupId)
    }
}
