package com.oreki.stumpd.domain.match

import com.google.common.truth.Truth.assertThat
import com.oreki.stumpd.domain.model.DeliveryUI
import com.oreki.stumpd.domain.model.FallOfWicket
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.model.MatchSettings
import com.oreki.stumpd.domain.model.Partnership
import com.oreki.stumpd.domain.model.PlayerMatchStats
import org.junit.Test

/**
 * Moving an over to the bowler who actually bowled it.
 *
 * The fixture's second over is recorded as Muttu's when Madhu bowled it: six legal balls, eight
 * runs off the bat, a bye that shouldn't be charged to anyone, and a caught-behind. Getting this
 * right means all four of those behaving differently — the balls move, most of the runs move, the
 * bye's runs don't, and the wicket moves with its credit intact on three separate rows.
 */
class ReassignBowlerOpTest {

    private val settings = MatchSettings(totalOvers = 5, maxPlayersPerTeam = 5, maxOversPerBowler = 2)

    private fun ball(over: Int, inOver: Int, outcome: String, runs: Int, bowler: String, striker: String = "Kushal") =
        DeliveryUI(
            inning = 1, over = over, ballInOver = inOver, outcome = outcome, runs = runs,
            strikerName = striker, nonStrikerName = "Gokul", bowlerName = bowler,
        )

    private fun match() = MatchHistory(
        id = "m1",
        team1Name = "Strikers",
        team2Name = "Chasers",
        firstInningsRuns = 21,
        firstInningsWickets = 1,
        secondInningsRuns = 10,
        secondInningsWickets = 0,
        winnerTeam = "Strikers",
        winningMargin = "11 runs",
        firstInningsBatting = listOf(
            PlayerMatchStats(
                id = "kushal", name = "Kushal", team = "Strikers", role = "BAT",
                runs = 12, ballsFaced = 9, isOut = true, dismissalType = "CAUGHT",
                bowlerName = "Muttu", fielderName = "Prasanna",
            ),
            PlayerMatchStats(id = "gokul", name = "Gokul", team = "Strikers", role = "BAT", runs = 8, ballsFaced = 6),
        ),
        firstInningsBowling = listOf(
            // Overs 1 and 2 are both recorded as Muttu's: 6 charged in the first, 6 in the
            // second (its 2-run bye is charged to nobody), plus the wicket in over 2.
            PlayerMatchStats(
                id = "muttu", name = "Muttu", team = "Chasers", role = "BOWL",
                wickets = 1, runsConceded = 12, oversBowled = 2.0, bowlingPosition = 1,
            ),
            PlayerMatchStats(
                id = "madhu", name = "Madhu", team = "Chasers", role = "BOWL",
                wickets = 0, runsConceded = 7, oversBowled = 1.0, bowlingPosition = 2,
            ),
        ),
        secondInningsBatting = listOf(
            PlayerMatchStats(id = "muttu", name = "Muttu", team = "Chasers", role = "BAT", runs = 6, ballsFaced = 5),
            PlayerMatchStats(id = "madhu", name = "Madhu", team = "Chasers", role = "BAT", runs = 4, ballsFaced = 4),
            PlayerMatchStats(id = "prasanna", name = "Prasanna", team = "Chasers", role = "BAT", runs = 0, ballsFaced = 1),
        ),
        secondInningsBowling = listOf(
            PlayerMatchStats(id = "kushal", name = "Kushal", team = "Strikers", role = "BOWL", runsConceded = 10, oversBowled = 2.0),
        ),
        firstInningsPartnerships = listOf(
            Partnership("Kushal", "Gokul", runs = 20, balls = 12, isActive = false),
        ),
        firstInningsFallOfWickets = listOf(
            FallOfWicket(
                batsmanName = "Kushal", runs = 20, overs = 2.5, wicketNumber = 1,
                dismissalType = "CAUGHT", bowlerName = "Muttu", fielderName = "Prasanna",
            ),
        ),
        allDeliveries = listOf(
            // Over 1 — Muttu, six legal balls, six runs.
            ball(1, 1, "1", 1, "Muttu"), ball(1, 2, "0", 0, "Muttu"), ball(1, 3, "4", 4, "Muttu"),
            ball(1, 4, "0", 0, "Muttu"), ball(1, 5, "1", 1, "Muttu"), ball(1, 6, "0", 0, "Muttu"),
            // Over 2 — recorded as Muttu's, actually Madhu's: 6 off the bat, a 2-run bye, a wicket.
            ball(2, 1, "4", 4, "Muttu"), ball(2, 2, "B+2", 2, "Muttu"), ball(2, 3, "2", 2, "Muttu"),
            ball(2, 4, "0", 0, "Muttu"), ball(2, 5, "W", 0, "Muttu"), ball(2, 6, "0", 0, "Muttu"),
            // Over 3 — Madhu's own over, seven runs.
            ball(3, 1, "1", 1, "Gokul"), ball(3, 2, "4", 4, "Gokul"), ball(3, 3, "0", 0, "Gokul"),
            ball(3, 4, "2", 2, "Gokul"), ball(3, 5, "0", 0, "Gokul"), ball(3, 6, "0", 0, "Gokul"),
        ).map { if (it.over == 3) it.copy(bowlerName = "Madhu") else it },
        matchSettings = settings,
    )

    private fun move(over: Int = 2, to: String = "Madhu", ball: Int? = null, innings: Int = 1) =
        MatchCorrection.ReassignBowler(
            innings = innings, over = over, ballInOver = ball, toBowlerName = to,
        )

    private fun applied(match: MatchHistory, correction: MatchCorrection): CorrectionOutcome.Applied {
        val outcome = MatchCorrectionEngine.apply(match, listOf(correction), settings)
        assertThat(outcome).isInstanceOf(CorrectionOutcome.Applied::class.java)
        return outcome as CorrectionOutcome.Applied
    }

    private fun rejected(match: MatchHistory, correction: MatchCorrection): List<String> {
        val outcome = MatchCorrectionEngine.apply(match, listOf(correction), settings)
        assertThat(outcome).isInstanceOf(CorrectionOutcome.Rejected::class.java)
        return (outcome as CorrectionOutcome.Rejected).errors.map { it.code }
    }

    private fun bowler(match: MatchHistory, name: String) =
        match.firstInningsBowling.single { it.name == name }

    @Test
    fun `the over's balls move to the right bowler`() {
        val after = applied(match(), move()).after

        assertThat(bowler(after, "Muttu").oversBowled).isEqualTo(1.0)
        assertThat(bowler(after, "Madhu").oversBowled).isEqualTo(2.0)
    }

    @Test
    fun `runs off the bat move, but a bye is charged to nobody`() {
        val after = applied(match(), move()).after

        // Over 2 was worth 8 runs, of which 2 were byes: only 6 leave Muttu's figures.
        assertThat(bowler(after, "Muttu").runsConceded).isEqualTo(6)
        assertThat(bowler(after, "Madhu").runsConceded).isEqualTo(13)
    }

    @Test
    fun `the wicket moves with the over, on all three rows that record it`() {
        val after = applied(match(), move()).after

        assertThat(bowler(after, "Muttu").wickets).isEqualTo(0)
        assertThat(bowler(after, "Madhu").wickets).isEqualTo(1)
        assertThat(after.firstInningsFallOfWickets.single().bowlerName).isEqualTo("Madhu")
        assertThat(after.firstInningsBatting.single { it.name == "Kushal" }.bowlerName)
            .isEqualTo("Madhu")
    }

    @Test
    fun `every delivery in the over now names the new bowler, and no other does`() {
        val after = applied(match(), move()).after

        val overTwo = after.allDeliveries.filter { it.over == 2 }
        val overOne = after.allDeliveries.filter { it.over == 1 }
        assertThat(overTwo.map { it.bowlerName }.distinct()).containsExactly("Madhu")
        assertThat(overOne.map { it.bowlerName }.distinct()).containsExactly("Muttu")
    }

    @Test
    fun `the team total and the batters are untouched — only the bowling changes`() {
        val before = match()
        val after = applied(before, move()).after

        assertThat(after.firstInningsRuns).isEqualTo(before.firstInningsRuns)
        assertThat(after.firstInningsWickets).isEqualTo(1)
        assertThat(after.firstInningsBatting.map { it.runs }).isEqualTo(before.firstInningsBatting.map { it.runs })
        assertThat(after.firstInningsFallOfWickets.single().runs).isEqualTo(20)
        assertThat(after.firstInningsFallOfWickets.single().batsmanName).isEqualTo("Kushal")
    }

    @Test
    fun `moving a single ball moves one ball and its runs`() {
        val after = applied(match(), move(ball = 3)).after

        // Ball 2.3 was worth two runs off the bat.
        assertThat(bowler(after, "Muttu").oversBowled).isEqualTo(1.5)
        assertThat(bowler(after, "Muttu").runsConceded).isEqualTo(10)
        assertThat(bowler(after, "Madhu").oversBowled).isEqualTo(1.1)
        assertThat(bowler(after, "Madhu").runsConceded).isEqualTo(9)
        // The wicket wasn't on that ball, so it stays with Muttu.
        assertThat(bowler(after, "Muttu").wickets).isEqualTo(1)
    }

    @Test
    fun `a bowler with a first spell gets a row built from their squad entry`() {
        val after = applied(match(), move(to = "Prasanna")).after

        val prasanna = bowler(after, "Prasanna")
        assertThat(prasanna.id).isEqualTo("prasanna")
        assertThat(prasanna.team).isEqualTo("Chasers")
        assertThat(prasanna.oversBowled).isEqualTo(1.0)
        assertThat(prasanna.runsConceded).isEqualTo(6)
        assertThat(prasanna.wickets).isEqualTo(1)
    }

    @Test
    fun `a bowler left with nothing loses their row, and the order closes up`() {
        // Muttu bowled only overs 1 and 2 here; move both and he didn't bowl at all.
        val once = applied(match(), move(over = 1, to = "Madhu")).after
        val twice = applied(once, move(over = 2, to = "Madhu")).after

        assertThat(twice.firstInningsBowling.map { it.name }).containsExactly("Madhu")
        assertThat(twice.firstInningsBowling.map { it.bowlingPosition }).containsExactly(1)
        assertThat(bowler(twice, "Madhu").oversBowled).isEqualTo(3.0)
    }

    @Test
    fun `going past the match's over limit warns rather than refuses`() {
        // Madhu ends with 2 overs — at the limit. Give him a third and it's over.
        val once = applied(match(), move(over = 2, to = "Madhu"))
        assertThat(once.warnings).isEmpty()

        val twice = applied(once.after, move(over = 1, to = "Madhu"))
        assertThat(twice.warnings.map { it.code }).contains("OVER_LIMIT_EXCEEDED")
        assertThat(bowler(twice.after, "Madhu").oversBowled).isEqualTo(3.0)
    }

    @Test
    fun `maidens are re-derived, so a moved wicketless over follows its bowler`() {
        // Zeroing over 1 takes six runs out of the innings, so the batter who scored them has
        // to lose them too — otherwise the invariant check rightly refuses a scorecard whose
        // batters have more runs between them than the innings total.
        val quiet = match().let { m ->
            m.copy(
                firstInningsRuns = 15,
                allDeliveries = m.allDeliveries.map { if (it.over == 1) it.copy(outcome = "0", runs = 0) else it },
                firstInningsBatting = m.firstInningsBatting.map {
                    if (it.name == "Kushal") it.copy(runs = 6, ballsFaced = 9) else it
                },
                firstInningsBowling = m.firstInningsBowling.map {
                    if (it.name == "Muttu") it.copy(runsConceded = 6, maidenOvers = 1) else it
                },
                firstInningsPartnerships = listOf(
                    Partnership("Kushal", "Gokul", runs = 14, balls = 12, isActive = false),
                ),
                firstInningsFallOfWickets = m.firstInningsFallOfWickets.map { it.copy(runs = 14) },
            )
        }

        val after = applied(quiet, move(over = 1, to = "Madhu")).after

        assertThat(bowler(after, "Muttu").maidenOvers).isEqualTo(0)
        assertThat(bowler(after, "Madhu").maidenOvers).isEqualTo(1)
    }

    @Test
    fun `a match with no ball-by-ball record is refused, with the reason`() {
        val noTrail = match().copy(allDeliveries = emptyList())

        val outcome = MatchCorrectionEngine.apply(noTrail, listOf(move()), settings)
        val errors = (outcome as CorrectionOutcome.Rejected).errors
        assertThat(errors.map { it.code }).containsExactly("REQUIRES_DELIVERY_TRAIL")
        assertThat(errors.single().message).contains("scored ball by ball")
    }

    @Test
    fun `an over split between two bowlers is refused rather than lumped together`() {
        val split = match().let { m ->
            m.copy(
                allDeliveries = m.allDeliveries.map {
                    if (it.over == 2 && it.ballInOver > 3) it.copy(bowlerName = "Madhu") else it
                },
            )
        }

        assertThat(rejected(split, move(to = "Prasanna"))).containsExactly("MIXED_BOWLERS")
    }

    @Test
    fun `moving an over to someone who wasn't fielding is refused`() {
        // Gokul batted for the other side.
        assertThat(rejected(match(), move(to = "Gokul"))).containsExactly("BOWLER_NOT_IN_MATCH")
    }

    @Test
    fun `moving an over to the bowler who already has it is refused`() {
        assertThat(rejected(match(), move(to = "Muttu"))).containsExactly("NO_OP")
    }

    @Test
    fun `an over that doesn't exist is refused`() {
        assertThat(rejected(match(), move(over = 9))).containsExactly("OVER_NOT_FOUND")
    }

    @Test
    fun `the match keeps its identity and date`() {
        val before = match()
        val after = applied(before, move()).after

        assertThat(after.id).isEqualTo(before.id)
        assertThat(after.matchDate).isEqualTo(before.matchDate)
    }
}
