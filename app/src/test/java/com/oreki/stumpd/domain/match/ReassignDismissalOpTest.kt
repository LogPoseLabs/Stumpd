package com.oreki.stumpd.domain.match

import com.google.common.truth.Truth.assertThat
import com.oreki.stumpd.domain.model.DeliveryUI
import com.oreki.stumpd.domain.model.FallOfWicket
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.model.MatchSettings
import com.oreki.stumpd.domain.model.Partnership
import com.oreki.stumpd.domain.model.PlayerMatchStats
import com.oreki.stumpd.domain.model.WicketType
import org.junit.Test

/**
 * Correcting "the wrong batter was marked out".
 *
 * The fixture is a five-over innings where Kushal is recorded caught by Madhu off Muttu at 12/1,
 * when in fact his partner Ajith was the one dismissed. Every assertion here is about the four
 * places a wicket is recorded agreeing with each other afterwards — the scorecard row, the
 * fall-of-wickets line, the bowler's tally and the fielder's catch — because a scorecard that
 * contradicts itself is worse than one that's simply wrong.
 */
class ReassignDismissalOpTest {

    private val settings = MatchSettings(totalOvers = 5, maxPlayersPerTeam = 5)

    private fun bat(
        name: String,
        runs: Int = 0,
        balls: Int = 0,
        isOut: Boolean = false,
        dismissalType: String? = null,
        bowlerName: String? = null,
        fielderName: String? = null,
        catches: Int = 0,
        runOuts: Int = 0,
        stumpings: Int = 0,
        team: String = "Strikers",
    ) = PlayerMatchStats(
        id = name.lowercase(), name = name, team = team, role = "BAT",
        runs = runs, ballsFaced = balls, isOut = isOut, dismissalType = dismissalType,
        bowlerName = bowlerName, fielderName = fielderName,
        catches = catches, runOuts = runOuts, stumpings = stumpings,
    )

    private fun bowl(
        name: String,
        wickets: Int = 0,
        runsConceded: Int = 0,
        oversBowled: Double = 0.0,
        catches: Int = 0,
        stumpings: Int = 0,
        runOuts: Int = 0,
        team: String = "Chasers",
    ) = PlayerMatchStats(
        id = name.lowercase(), name = name, team = team, role = "BOWL",
        wickets = wickets, runsConceded = runsConceded, oversBowled = oversBowled,
        catches = catches, stumpings = stumpings, runOuts = runOuts,
    )

    /** Kushal is wrongly recorded as caught; Ajith was the one who went. */
    private fun match(
        firstWicketOutcome: String = "W",
        partnerships: List<Partnership> = listOf(
            Partnership("Kushal", "Ajith", runs = 12, balls = 10, isActive = false),
            Partnership("Kushal", "Gokul", runs = 20, balls = 14, isActive = true),
        ),
    ) = MatchHistory(
        id = "m1",
        team1Name = "Strikers",
        team2Name = "Chasers",
        firstInningsRuns = 32,
        firstInningsWickets = 1,
        secondInningsRuns = 20,
        secondInningsWickets = 0,
        winnerTeam = "Strikers",
        winningMargin = "12 runs",
        firstInningsBatting = listOf(
            bat("Kushal", runs = 8, balls = 7, isOut = true, dismissalType = "CAUGHT",
                bowlerName = "Muttu", fielderName = "Madhu"),
            bat("Ajith", runs = 4, balls = 3),
            bat("Gokul", runs = 20, balls = 14),
        ),
        firstInningsBowling = listOf(
            bowl("Muttu", wickets = 1, runsConceded = 18, oversBowled = 2.0),
            bowl("Madhu", wickets = 0, runsConceded = 14, oversBowled = 3.0, catches = 1),
        ),
        secondInningsBatting = listOf(
            bat("Muttu", runs = 12, balls = 10, team = "Chasers"),
            bat("Madhu", runs = 8, balls = 9, team = "Chasers"),
        ),
        secondInningsBowling = listOf(
            bowl("Kushal", wickets = 0, runsConceded = 20, oversBowled = 5.0, team = "Strikers"),
        ),
        firstInningsPartnerships = partnerships,
        firstInningsFallOfWickets = listOf(
            FallOfWicket(
                batsmanName = "Kushal", runs = 12, overs = 1.4, wicketNumber = 1,
                dismissalType = "CAUGHT", bowlerName = "Muttu", fielderName = "Madhu",
            ),
        ),
        allDeliveries = listOf(
            DeliveryUI(1, 1, 1, "4", runs = 4, strikerName = "Kushal", nonStrikerName = "Ajith", bowlerName = "Muttu"),
            DeliveryUI(1, 1, 2, firstWicketOutcome, runs = 0, strikerName = "Kushal", nonStrikerName = "Ajith", bowlerName = "Muttu"),
        ),
        matchSettings = settings,
    )

    private fun reassignTo(
        name: String,
        type: WicketType = WicketType.CAUGHT,
        bowler: String? = "Muttu",
        fielder: String? = "Madhu",
        innings: Int = 1,
        wicketNumber: Int = 1,
    ) = MatchCorrection.ReassignDismissal(
        innings = innings,
        wicketNumber = wicketNumber,
        outBatterName = name,
        dismissalType = type,
        bowlerName = bowler,
        fielderName = fielder,
    )

    private fun applied(
        match: MatchHistory,
        vararg corrections: MatchCorrection,
    ): CorrectionOutcome.Applied {
        val outcome = MatchCorrectionEngine.apply(match, corrections.toList(), settings)
        assertThat(outcome).isInstanceOf(CorrectionOutcome.Applied::class.java)
        return outcome as CorrectionOutcome.Applied
    }

    private fun rejected(
        match: MatchHistory,
        vararg corrections: MatchCorrection,
    ): List<String> {
        val outcome = MatchCorrectionEngine.apply(match, corrections.toList(), settings)
        assertThat(outcome).isInstanceOf(CorrectionOutcome.Rejected::class.java)
        return (outcome as CorrectionOutcome.Rejected).errors.map { it.code }
    }

    @Test
    fun `the dismissal moves to the batter who was actually out`() {
        val after = applied(match(), reassignTo("Ajith")).after

        val kushal = after.firstInningsBatting.single { it.name == "Kushal" }
        val ajith = after.firstInningsBatting.single { it.name == "Ajith" }

        assertThat(kushal.isOut).isFalse()
        assertThat(kushal.dismissalType).isNull()
        assertThat(kushal.bowlerName).isNull()
        assertThat(kushal.fielderName).isNull()

        assertThat(ajith.isOut).isTrue()
        assertThat(ajith.dismissalType).isEqualTo("CAUGHT")
        assertThat(ajith.bowlerName).isEqualTo("Muttu")
        assertThat(ajith.fielderName).isEqualTo("Madhu")
    }

    @Test
    fun `runs and balls stay with the batters who faced them`() {
        val after = applied(match(), reassignTo("Ajith")).after

        // Marking the wrong batter out never misattributed a run.
        assertThat(after.firstInningsBatting.single { it.name == "Kushal" }.runs).isEqualTo(8)
        assertThat(after.firstInningsBatting.single { it.name == "Kushal" }.ballsFaced).isEqualTo(7)
        assertThat(after.firstInningsBatting.single { it.name == "Ajith" }.runs).isEqualTo(4)
        assertThat(after.firstInningsBatting.single { it.name == "Ajith" }.ballsFaced).isEqualTo(3)
    }

    @Test
    fun `the fall of wickets line names the right batter but keeps when it fell`() {
        val after = applied(match(), reassignTo("Ajith")).after

        val wicket = after.firstInningsFallOfWickets.single()
        assertThat(wicket.batsmanName).isEqualTo("Ajith")
        assertThat(wicket.runs).isEqualTo(12)
        assertThat(wicket.overs).isEqualTo(1.4)
        assertThat(wicket.wicketNumber).isEqualTo(1)
    }

    @Test
    fun `the innings wicket count is unchanged, because one wicket still fell`() {
        val after = applied(match(), reassignTo("Ajith")).after

        assertThat(after.firstInningsWickets).isEqualTo(1)
        assertThat(after.firstInningsBatting.count { it.isOut }).isEqualTo(1)
    }

    @Test
    fun `the bowler keeps the wicket when the manner of dismissal still credits them`() {
        val after = applied(match(), reassignTo("Ajith")).after

        assertThat(after.firstInningsBowling.single { it.name == "Muttu" }.wickets).isEqualTo(1)
    }

    @Test
    fun `changing caught to run out takes the wicket off the bowler`() {
        // A run-out is nobody's wicket — the tally has to follow the *type*, not the name.
        val after = applied(
            match(),
            reassignTo("Ajith", type = WicketType.RUN_OUT, bowler = null, fielder = "Madhu"),
        ).after

        assertThat(after.firstInningsBowling.single { it.name == "Muttu" }.wickets).isEqualTo(0)
        val ajith = after.firstInningsBatting.single { it.name == "Ajith" }
        assertThat(ajith.dismissalType).isEqualTo("RUN_OUT")
        assertThat(ajith.bowlerName).isNull()
    }

    @Test
    fun `changing the bowler moves the wicket between them`() {
        val after = applied(match(), reassignTo("Ajith", bowler = "Madhu")).after

        assertThat(after.firstInningsBowling.single { it.name == "Muttu" }.wickets).isEqualTo(0)
        assertThat(after.firstInningsBowling.single { it.name == "Madhu" }.wickets).isEqualTo(1)
    }

    @Test
    fun `a catch given to the wrong fielder moves with the correction`() {
        val after = applied(match(), reassignTo("Ajith", fielder = "Muttu")).after

        // Madhu's catch goes, Muttu gains one; both live on the fielding side's rows.
        assertThat(after.firstInningsBowling.single { it.name == "Madhu" }.catches).isEqualTo(0)
        assertThat(after.firstInningsBowling.single { it.name == "Muttu" }.catches).isEqualTo(1)
    }

    @Test
    fun `caught becoming run out moves the credit from catches to run-outs`() {
        val after = applied(
            match(),
            reassignTo("Ajith", type = WicketType.RUN_OUT, bowler = null, fielder = "Madhu"),
        ).after

        val madhu = after.firstInningsBowling.single { it.name == "Madhu" }
        assertThat(madhu.catches).isEqualTo(0)
        assertThat(madhu.runOuts).isEqualTo(1)
    }

    @Test
    fun `the survivor carries on into the following stands`() {
        // Kushal was recorded out at 12/1 and so vanished from the second stand; in truth Ajith
        // went, and Kushal batted on with Gokul.
        val after = applied(match(), reassignTo("Ajith")).after

        val stands = after.firstInningsPartnerships
        assertThat(stands[0].batsman1Name).isEqualTo("Kushal")
        assertThat(stands[0].batsman2Name).isEqualTo("Ajith")
        // The stand that ended keeps both names — they were both there.
        assertThat(stands[1].batsman1Name).isEqualTo("Kushal")
        assertThat(stands[1].batsman2Name).isEqualTo("Gokul")
    }

    @Test
    fun `a run-out name written into the delivery is rewritten too`() {
        val after = applied(
            match(firstWicketOutcome = "1 + RO (Kushal @ S)"),
            reassignTo("Ajith", type = WicketType.RUN_OUT, bowler = null, fielder = "Madhu"),
        ).after

        assertThat(after.allDeliveries.map { it.outcome })
            .containsExactly("4", "1 + RO (Ajith @ S)")
            .inOrder()
    }

    @Test
    fun `a plain wicket ball is left alone, since it names nobody`() {
        val after = applied(match(), reassignTo("Ajith")).after

        assertThat(after.allDeliveries.map { it.outcome }).containsExactly("4", "W").inOrder()
    }

    @Test
    fun `correcting only the manner of dismissal keeps the same batter out`() {
        val after = applied(
            match(),
            reassignTo("Kushal", type = WicketType.BOWLED, fielder = null),
        ).after

        val kushal = after.firstInningsBatting.single { it.name == "Kushal" }
        assertThat(kushal.isOut).isTrue()
        assertThat(kushal.dismissalType).isEqualTo("BOWLED")
        assertThat(kushal.fielderName).isNull()
        // Still Muttu's wicket, and Madhu's catch is gone.
        assertThat(after.firstInningsBowling.single { it.name == "Muttu" }.wickets).isEqualTo(1)
        assertThat(after.firstInningsBowling.single { it.name == "Madhu" }.catches).isEqualTo(0)
    }

    @Test
    fun `impacts and the award are re-derived from the corrected figures`() {
        val before = match()
        val after = applied(before, reassignTo("Ajith")).after

        // Ajith is now the one dismissed, so Kushal's not-out star appears in his summary.
        val kushal = after.playerImpacts.single { it.name == "Kushal" }
        assertThat(kushal.summary).contains("8*(7)")
        assertThat(after.playerOfTheMatchName).isNotNull()
    }

    @Test
    fun `the match keeps its identity and its date`() {
        val before = match()
        val after = applied(before, reassignTo("Ajith")).after

        assertThat(after.id).isEqualTo(before.id)
        assertThat(after.matchDate).isEqualTo(before.matchDate)
    }

    @Test
    fun `a batter who never batted in that innings is refused`() {
        assertThat(rejected(match(), reassignTo("Nobody"))).containsExactly("BATTER_NOT_IN_INNINGS")
    }

    @Test
    fun `a batter already out for another wicket is refused`() {
        val twoWickets = match().let { m ->
            m.copy(
                firstInningsBatting = m.firstInningsBatting.map {
                    if (it.name == "Gokul") it.copy(isOut = true, dismissalType = "BOWLED", bowlerName = "Muttu") else it
                },
                firstInningsFallOfWickets = m.firstInningsFallOfWickets + FallOfWicket(
                    batsmanName = "Gokul", runs = 30, overs = 4.2, wicketNumber = 2,
                    dismissalType = "BOWLED", bowlerName = "Muttu",
                ),
                firstInningsPartnerships = listOf(
                    Partnership("Kushal", "Ajith", runs = 12, balls = 10, isActive = false),
                    Partnership("Kushal", "Gokul", runs = 20, balls = 14, isActive = false),
                ),
            )
        }

        assertThat(rejected(twoWickets, reassignTo("Gokul"))).contains("BATTER_ALREADY_OUT")
    }

    @Test
    fun `a batter who wasn't at the crease for that wicket is refused`() {
        // Gokul came in after the first wicket, so he cannot be the one it took.
        assertThat(rejected(match(), reassignTo("Gokul"))).contains("BATTER_NOT_AT_CREASE")
    }

    @Test
    fun `a bowler-credited dismissal without a bowler is refused`() {
        assertThat(rejected(match(), reassignTo("Ajith", bowler = null)))
            .contains("BOWLER_REQUIRED")
    }

    @Test
    fun `a bowler who didn't bowl that innings is refused`() {
        assertThat(rejected(match(), reassignTo("Ajith", bowler = "Gokul")))
            .contains("BOWLER_NOT_IN_INNINGS")
    }

    @Test
    fun `an inherited bowler from the wrong innings doesn't block fixing the batter`() {
        // Matches saved by older versions have bowlers recorded against the wrong innings. Held
        // to the same rule as a fresh choice, the batter's name could never be fixed on exactly
        // the matches that most need it — so an unchanged name is left as it is.
        val legacy = match().let { m ->
            m.copy(
                firstInningsBatting = m.firstInningsBatting.map {
                    if (it.name == "Kushal") it.copy(bowlerName = "Sunil") else it
                },
                firstInningsFallOfWickets = m.firstInningsFallOfWickets.map {
                    it.copy(bowlerName = "Sunil")
                },
            )
        }

        val outcome = applied(legacy, reassignTo("Ajith", bowler = "Sunil"))

        assertThat(outcome.after.firstInningsBatting.single { it.name == "Ajith" }.isOut).isTrue()
        assertThat(outcome.after.firstInningsFallOfWickets.single().batsmanName).isEqualTo("Ajith")
        // The wrong bowler is still wrong, and still says so — this fixed the batter, not that.
        assertThat(outcome.after.firstInningsFallOfWickets.single().bowlerName).isEqualTo("Sunil")
    }

    @Test
    fun `a wicket number that doesn't exist is refused`() {
        assertThat(rejected(match(), reassignTo("Ajith", wicketNumber = 3)))
            .containsExactly("WICKET_NOT_FOUND")
    }

    @Test
    fun `two batters with the same name are refused rather than guessed at`() {
        val ambiguous = match().let { m ->
            m.copy(firstInningsBatting = m.firstInningsBatting + bat("Ajith", runs = 1, balls = 1))
        }

        assertThat(rejected(ambiguous, reassignTo("Ajith"))).contains("AMBIGUOUS_NAME")
    }

    @Test
    fun `a wicket missing from the ball-by-ball record is corrected with a warning`() {
        val noTrail = match().copy(allDeliveries = emptyList())

        val outcome = applied(noTrail, reassignTo("Ajith"))

        assertThat(outcome.after.firstInningsFallOfWickets.single().batsmanName).isEqualTo("Ajith")
        assertThat(outcome.warnings.map { it.code }).contains("WICKET_NOT_IN_TRAIL")
    }
}
