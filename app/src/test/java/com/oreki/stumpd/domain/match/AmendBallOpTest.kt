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
 * Correcting what a single delivery was worth.
 *
 * Runs propagate, so this is the op where "did you remember X?" matters most. The fixture has a
 * wicket *after* the ball being corrected precisely so the forgotten case is covered: the score
 * recorded against that wicket has to move too, or the fall-of-wickets column stops agreeing with
 * the total.
 */
class AmendBallOpTest {

    private val settings = MatchSettings(totalOvers = 5, maxPlayersPerTeam = 5, noballRuns = 1)

    private fun ball(over: Int, inOver: Int, outcome: String, runs: Int, striker: String = "Kushal") =
        DeliveryUI(
            inning = 1, over = over, ballInOver = inOver, outcome = outcome, runs = runs,
            strikerName = striker, nonStrikerName = "Gokul", bowlerName = "Muttu",
        )

    /** 1, 1, then a wicket: eleven runs in the innings, the wicket falling at 2. */
    private fun match() = MatchHistory(
        id = "m1",
        team1Name = "Strikers",
        team2Name = "Chasers",
        firstInningsRuns = 11,
        firstInningsWickets = 1,
        secondInningsRuns = 5,
        secondInningsWickets = 0,
        winnerTeam = "Strikers",
        winningMargin = "6 runs",
        firstInningsBatting = listOf(
            PlayerMatchStats(
                id = "kushal", name = "Kushal", team = "Strikers", role = "BAT",
                runs = 2, ballsFaced = 3, singles = 2, dots = 1,
                isOut = true, dismissalType = "BOWLED", bowlerName = "Muttu",
            ),
            PlayerMatchStats(id = "gokul", name = "Gokul", team = "Strikers", role = "BAT", runs = 9, ballsFaced = 8, fours = 2, singles = 1),
        ),
        firstInningsBowling = listOf(
            PlayerMatchStats(
                id = "muttu", name = "Muttu", team = "Chasers", role = "BOWL",
                wickets = 1, runsConceded = 11, oversBowled = 2.0,
            ),
        ),
        secondInningsBatting = listOf(
            PlayerMatchStats(id = "muttu", name = "Muttu", team = "Chasers", role = "BAT", runs = 5, ballsFaced = 6),
        ),
        secondInningsBowling = listOf(
            PlayerMatchStats(id = "kushal", name = "Kushal", team = "Strikers", role = "BOWL", runsConceded = 5, oversBowled = 1.0),
        ),
        firstInningsPartnerships = listOf(
            Partnership("Kushal", "Gokul", runs = 2, balls = 3, batsman1Runs = 2, batsman2Runs = 0, isActive = false),
            Partnership("Gokul", "Ajith", runs = 9, balls = 9, batsman1Runs = 9, batsman2Runs = 0, isActive = true),
        ),
        firstInningsFallOfWickets = listOf(
            FallOfWicket(
                batsmanName = "Kushal", runs = 2, overs = 0.3, wicketNumber = 1,
                dismissalType = "BOWLED", bowlerName = "Muttu",
            ),
        ),
        allDeliveries = listOf(
            ball(1, 1, "1", 1),
            ball(1, 2, "1", 1),
            ball(1, 3, "W", 0),
            ball(1, 4, "4", 4, striker = "Gokul"),
            ball(1, 5, "4", 4, striker = "Gokul"),
            ball(1, 6, "1", 1, striker = "Gokul"),
        ),
        matchSettings = settings,
    )

    private fun amend(
        over: Int = 1,
        ballInOver: Int = 1,
        kind: DeliveryOutcome.Kind = DeliveryOutcome.Kind.OFF_THE_BAT,
        runs: Int = 4,
        innings: Int = 1,
    ) = MatchCorrection.AmendBall(
        innings = innings, over = over, ballInOver = ballInOver,
        newKind = kind, newTotalRuns = runs,
    )

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
    fun `a single becomes a four everywhere at once`() {
        val after = applied(match(), amend(runs = 4))

        assertThat(after.allDeliveries.first().outcome).isEqualTo("4")
        assertThat(after.allDeliveries.first().runs).isEqualTo(4)
        assertThat(after.allDeliveries.first().highlight).isTrue()
        assertThat(after.firstInningsRuns).isEqualTo(14)
        assertThat(after.firstInningsBatting.single { it.name == "Kushal" }.runs).isEqualTo(5)
        assertThat(after.firstInningsBowling.single().runsConceded).isEqualTo(14)
    }

    @Test
    fun `the shot-type counters move with it`() {
        val after = applied(match(), amend(runs = 4))

        val kushal = after.firstInningsBatting.single { it.name == "Kushal" }
        assertThat(kushal.singles).isEqualTo(1)   // was 2
        assertThat(kushal.fours).isEqualTo(1)     // was 0
        assertThat(kushal.dots).isEqualTo(1)      // untouched
    }

    @Test
    fun `the score at every later wicket shifts — the easiest thing to forget`() {
        val after = applied(match(), amend(runs = 4))

        // The wicket fell two balls later, so it fell at 5, not 2.
        assertThat(after.firstInningsFallOfWickets.single().runs).isEqualTo(5)
        assertThat(after.firstInningsFallOfWickets.single().overs).isEqualTo(0.3)
        assertThat(after.firstInningsFallOfWickets.single().wicketNumber).isEqualTo(1)
    }

    @Test
    fun `a wicket before the corrected ball keeps its score`() {
        val after = applied(match(), amend(ballInOver = 4, runs = 6))

        // Ball 1.4 came after the wicket, so the wicket's score is untouched.
        assertThat(after.firstInningsFallOfWickets.single().runs).isEqualTo(2)
        assertThat(after.firstInningsRuns).isEqualTo(13)
    }

    @Test
    fun `the partnership in progress at that ball absorbs the change`() {
        val after = applied(match(), amend(runs = 4))

        val first = after.firstInningsPartnerships[0]
        assertThat(first.runs).isEqualTo(5)
        assertThat(first.batsman1Runs).isEqualTo(5)
        // The later stand is unaffected.
        assertThat(after.firstInningsPartnerships[1].runs).isEqualTo(9)
    }

    @Test
    fun `turning runs into leg byes takes them off both the batter and the bowler`() {
        val after = applied(match(), amend(kind = DeliveryOutcome.Kind.LEG_BYE, runs = 1))

        val kushal = after.firstInningsBatting.single { it.name == "Kushal" }
        assertThat(after.allDeliveries.first().outcome).isEqualTo("Lb+1")
        // The team keeps the run; the batter and the bowler don't.
        assertThat(after.firstInningsRuns).isEqualTo(11)
        assertThat(kushal.runs).isEqualTo(1)
        assertThat(after.firstInningsBowling.single().runsConceded).isEqualTo(10)
    }

    @Test
    fun `a no-ball credits the batter only with what came off the bat`() {
        // A no-ball is an extra, so this also changes the ball's legality — refused by default.
        assertThat(rejected(match(), amend(kind = DeliveryOutcome.Kind.NO_BALL, runs = 3)))
            .containsExactly("CHANGES_BALL_LEGALITY")
    }

    @Test
    fun `a correction that changes the result re-derives the winner and the award`() {
        // Strikers won by 6; take 8 runs off their innings and the chase wins instead.
        val before = match()
        val after = applied(before, amend(ballInOver = 4, runs = 0))

        assertThat(before.winnerTeam).isEqualTo("Strikers")
        assertThat(after.firstInningsRuns).isEqualTo(7)
        assertThat(after.winnerTeam).isEqualTo("Strikers") // still ahead of 5
        // And with both boundaries gone it's a tie.
        val tied = applied(after, amend(ballInOver = 5, runs = 0))
        assertThat(tied.firstInningsRuns).isEqualTo(3)
        assertThat(tied.winnerTeam).isEqualTo("Chasers")
        assertThat(tied.winningMargin).contains("wickets")
    }

    @Test
    fun `a wicket ball is refused, with the dismissal pointed to instead`() {
        val errors = MatchCorrectionEngine.apply(match(), listOf(amend(ballInOver = 3, runs = 2)), settings)
        val rejected = errors as CorrectionOutcome.Rejected
        assertThat(rejected.errors.map { it.code }).containsExactly("WICKET_BALL")
        assertThat(rejected.errors.single().message).contains("Correct the dismissal instead")
    }

    @Test
    fun `a match with no ball-by-ball record is refused`() {
        val noTrail = match().copy(allDeliveries = emptyList())

        assertThat(rejected(noTrail, amend())).containsExactly("REQUIRES_DELIVERY_TRAIL")
    }

    @Test
    fun `a ball that doesn't exist is refused`() {
        assertThat(rejected(match(), amend(over = 4, ballInOver = 2))).containsExactly("BALL_NOT_FOUND")
    }

    @Test
    fun `recording the same value again is refused rather than written`() {
        assertThat(rejected(match(), amend(runs = 1))).containsExactly("NO_OP")
    }

    @Test
    fun `negative runs are refused`() {
        assertThat(rejected(match(), amend(runs = -1))).containsExactly("NEGATIVE_RUNS")
    }

    @Test
    fun `balls faced and the wicket count are untouched`() {
        val before = match()
        val after = applied(before, amend(runs = 4))

        assertThat(after.firstInningsBatting.map { it.ballsFaced })
            .isEqualTo(before.firstInningsBatting.map { it.ballsFaced })
        assertThat(after.firstInningsWickets).isEqualTo(1)
        assertThat(after.firstInningsBowling.single().oversBowled).isEqualTo(2.0)
    }
}
