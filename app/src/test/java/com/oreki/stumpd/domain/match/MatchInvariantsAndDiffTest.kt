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
 * The two things standing between the correction engine and a wrecked scorecard: the invariant
 * check that decides whether a correction is allowed, and the diff the user confirms first.
 *
 * The invariant policy is the interesting half. Judged absolutely, it would lock the feature out
 * of every match an older version of the app saved inconsistently — which is exactly where
 * corrections are most wanted. So it's judged relative to where the match started.
 */
class MatchInvariantsAndDiffTest {

    private val settings = MatchSettings(totalOvers = 5, maxPlayersPerTeam = 5)

    private fun match(
        firstInningsWickets: Int = 1,
        battingOverrides: (List<PlayerMatchStats>) -> List<PlayerMatchStats> = { it },
    ): MatchHistory {
        val batting = battingOverrides(
            listOf(
                PlayerMatchStats(
                    id = "kushal", name = "Kushal", team = "Strikers", role = "BAT",
                    runs = 8, ballsFaced = 7, isOut = true, dismissalType = "CAUGHT",
                    bowlerName = "Muttu", fielderName = "Madhu",
                ),
                PlayerMatchStats(id = "gokul", name = "Gokul", team = "Strikers", role = "BAT", runs = 12, ballsFaced = 9),
            )
        )
        return MatchHistory(
            id = "m1",
            team1Name = "Strikers",
            team2Name = "Chasers",
            firstInningsRuns = 20,
            firstInningsWickets = firstInningsWickets,
            secondInningsRuns = 12,
            secondInningsWickets = 0,
            winnerTeam = "Strikers",
            winningMargin = "8 runs",
            firstInningsBatting = batting,
            firstInningsBowling = listOf(
                PlayerMatchStats(
                    id = "muttu", name = "Muttu", team = "Chasers", role = "BOWL",
                    wickets = 1, runsConceded = 20, oversBowled = 3.0,
                ),
                PlayerMatchStats(
                    id = "madhu", name = "Madhu", team = "Chasers", role = "BOWL",
                    runsConceded = 0, oversBowled = 0.0, catches = 1,
                ),
            ),
            secondInningsBatting = listOf(
                PlayerMatchStats(id = "muttu", name = "Muttu", team = "Chasers", role = "BAT", runs = 7, ballsFaced = 8),
                PlayerMatchStats(id = "madhu", name = "Madhu", team = "Chasers", role = "BAT", runs = 5, ballsFaced = 6),
            ),
            secondInningsBowling = listOf(
                PlayerMatchStats(id = "gokul", name = "Gokul", team = "Strikers", role = "BOWL", runsConceded = 12, oversBowled = 2.0),
            ),
            firstInningsPartnerships = listOf(
                Partnership("Kushal", "Gokul", runs = 8, balls = 7, isActive = false),
                Partnership("Gokul", "Ajith", runs = 12, balls = 9, isActive = true),
            ),
            firstInningsFallOfWickets = listOf(
                FallOfWicket(
                    batsmanName = "Kushal", runs = 8, overs = 1.1, wicketNumber = 1,
                    dismissalType = "CAUGHT", bowlerName = "Muttu", fielderName = "Madhu",
                ),
            ),
            allDeliveries = listOf(
                DeliveryUI(1, 1, 1, "4", runs = 4, strikerName = "Kushal", nonStrikerName = "Gokul", bowlerName = "Muttu"),
                DeliveryUI(1, 1, 2, "W", runs = 0, strikerName = "Kushal", nonStrikerName = "Gokul", bowlerName = "Muttu"),
            ),
            matchSettings = settings,
        )
    }

    // ── the invariants themselves ───────────────────────────────────────────────────────

    @Test
    fun `a coherent match has nothing to report`() {
        assertThat(MatchInvariants.check(match(), settings)).isEmpty()
    }

    @Test
    fun `a wicket count that disagrees with the dismissals is reported in plain words`() {
        val broken = match(firstInningsWickets = 3)

        val violations = MatchInvariants.check(broken, settings)

        assertThat(violations.map { it.code }).contains("WICKET_COUNT")
        assertThat(violations.first { it.code == "WICKET_COUNT" }.message)
            .isEqualTo("Innings 1 is recorded as 3 down, but 1 batter is out.")
    }

    @Test
    fun `more dismissals than fall-of-wickets lines is reported`() {
        val broken = match { rows -> rows.map { it.copy(isOut = true, dismissalType = "BOWLED", bowlerName = "Muttu") } }

        assertThat(MatchInvariants.check(broken, settings).map { it.code }).contains("FOW_COUNT")
    }

    @Test
    fun `a squad cannot lose more wickets than it has batters`() {
        // Two players in the squad, both out, single-side batting off: the ceiling is one.
        val tiny = match { rows -> rows.map { it.copy(isOut = true, dismissalType = "BOWLED", bowlerName = "Muttu") } }
            .copy(
                secondInningsBowling = emptyList(),
                firstInningsWickets = 2,
                matchSettings = settings.copy(allowSingleSideBatting = false),
            )

        val violations = MatchInvariants.check(tiny, settings.copy(allowSingleSideBatting = false))

        assertThat(violations.map { it.code }).contains("WICKETS_OVER_SQUAD")
    }

    @Test
    fun `batters cannot have more runs between them than the innings total`() {
        val broken = match { rows -> rows.map { if (it.name == "Gokul") it.copy(runs = 40) else it } }

        assertThat(MatchInvariants.check(broken, settings).map { it.code })
            .contains("BATTER_RUNS_EXCEED_TOTAL")
    }

    // ── the policy: relative to where the match started ─────────────────────────────────

    @Test
    fun `only violations the correction introduced count against it`() {
        // The ops are built not to produce broken states, so the policy is tested directly: a
        // clean match gaining a violation is the correction's fault...
        val clean = match()
        val broken = clean.copy(firstInningsWickets = 3)

        assertThat(MatchInvariants.introduced(clean, broken, settings).map { it.code })
            .contains("WICKET_COUNT")

        // ...while the same violation present beforehand is not.
        assertThat(MatchInvariants.introduced(broken, broken, settings)).isEmpty()
    }

    @Test
    fun `the engine refuses a correction that would introduce a violation`() {
        // Reach past the ops' own guards by asking for a dismissal the fixture can't support:
        // both batters out leaves two dismissals against one fall-of-wickets line.
        val bothOut = match { rows ->
            rows.map { it.copy(isOut = true, dismissalType = "BOWLED", bowlerName = "Muttu") }
        }

        val introduced = MatchInvariants.introduced(match(), bothOut, settings)

        assertThat(introduced.map { it.code }).contains("FOW_COUNT")
    }

    @Test
    fun `a match that was already inconsistent can still be corrected`() {
        // This is the case that decides whether the feature is usable at all: an older match
        // saved with the wrong wicket count must not be frozen forever.
        val alreadyBroken = match(firstInningsWickets = 3)

        val outcome = MatchCorrectionEngine.apply(
            alreadyBroken,
            listOf(
                MatchCorrection.ReassignDismissal(
                    innings = 1, wicketNumber = 1, outBatterName = "Gokul",
                    dismissalType = WicketType.BOWLED, bowlerName = "Muttu", fielderName = null,
                )
            ),
            settings,
        )

        assertThat(outcome).isInstanceOf(CorrectionOutcome.Applied::class.java)
        val applied = outcome as CorrectionOutcome.Applied
        assertThat(applied.after.firstInningsBatting.single { it.name == "Gokul" }.isOut).isTrue()
        // And the recompute fixed the count it inherited, which is worth telling the user.
        assertThat(applied.resolvedProblems.map { it.code }).contains("WICKET_COUNT")
    }

    // ── the diff the user confirms ──────────────────────────────────────────────────────

    @Test
    fun `the diff names both sides of every change`() {
        val outcome = MatchCorrectionEngine.apply(
            match(),
            listOf(
                MatchCorrection.ReassignDismissal(
                    innings = 1, wicketNumber = 1, outBatterName = "Gokul",
                    dismissalType = WicketType.BOWLED, bowlerName = "Muttu", fielderName = null,
                )
            ),
            settings,
        ) as CorrectionOutcome.Applied

        val text = outcome.diff.lines.map { it.text }
        assertThat(text).contains("Wicket 1: Kushal → Gokul")
        assertThat(text).contains("Kushal: 8 (7) c Madhu b Muttu → 8* (7)")
        assertThat(text).contains("Gokul: 12* (9) → 12 (9) b Muttu")
        assertThat(text).contains("Madhu in the field: 1 ct → nothing")
    }

    @Test
    fun `the result is always stated, so an unchanged one is visible too`() {
        val outcome = MatchCorrectionEngine.apply(
            match(),
            listOf(
                MatchCorrection.ReassignDismissal(
                    innings = 1, wicketNumber = 1, outBatterName = "Gokul",
                    dismissalType = WicketType.BOWLED, bowlerName = "Muttu", fielderName = null,
                )
            ),
            settings,
        ) as CorrectionOutcome.Applied

        assertThat(outcome.diff.resultChanged).isFalse()
        assertThat(outcome.diff.lines.map { it.text })
            .contains("Result unchanged — Strikers won by 8 runs")
    }

    @Test
    fun `bowling changes read as figures, the way a scorecard prints them`() {
        val trail = match().copy(
            allDeliveries = (1..6).map {
                DeliveryUI(1, 1, it, "0", runs = 0, strikerName = "Kushal", nonStrikerName = "Gokul", bowlerName = "Muttu")
            } + (1..6).map {
                DeliveryUI(1, 2, it, "0", runs = 0, strikerName = "Kushal", nonStrikerName = "Gokul", bowlerName = "Muttu")
            },
            firstInningsRuns = 0,
            firstInningsBatting = listOf(
                PlayerMatchStats(id = "kushal", name = "Kushal", team = "Strikers", role = "BAT", runs = 0, ballsFaced = 12),
            ),
            firstInningsWickets = 0,
            firstInningsFallOfWickets = emptyList(),
            firstInningsPartnerships = listOf(Partnership("Kushal", "Gokul", runs = 0, balls = 12)),
            firstInningsBowling = listOf(
                PlayerMatchStats(id = "muttu", name = "Muttu", team = "Chasers", role = "BOWL", runsConceded = 0, oversBowled = 2.0, maidenOvers = 2),
                PlayerMatchStats(id = "madhu", name = "Madhu", team = "Chasers", role = "BOWL", runsConceded = 0, oversBowled = 0.0),
            ),
            secondInningsRuns = 1,
            winnerTeam = "Chasers",
            winningMargin = "5 wickets",
        )

        val outcome = MatchCorrectionEngine.apply(
            trail,
            listOf(MatchCorrection.ReassignBowler(innings = 1, over = 2, ballInOver = null, toBowlerName = "Madhu")),
            settings,
        ) as CorrectionOutcome.Applied

        val text = outcome.diff.lines.map { it.text }
        assertThat(text).contains("Muttu: 2.0-2-0-0 → 1.0-1-0-0")
        assertThat(text).contains("Madhu: 0.0-0-0-0 → 1.0-1-0-0")
    }

    @Test
    fun `a substitution reads as one renaming, not two players`() {
        val outcome = MatchCorrectionEngine.apply(
            match(),
            listOf(MatchCorrection.SubstitutePlayer(fromName = "Gokul", toPlayerId = "p-new", toName = "Prasanna")),
            settings,
        ) as CorrectionOutcome.Applied

        val text = outcome.diff.lines.map { it.text }
        assertThat(text.any { it.contains("Gokul") && it.contains("Prasanna") }).isTrue()
        assertThat(outcome.diff.awardChanged || text.any { it.contains("Prasanna") }).isTrue()
    }
}
