package com.oreki.stumpd.domain.match

import com.oreki.stumpd.domain.model.FallOfWicket
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.model.MatchSettings
import com.oreki.stumpd.domain.model.Partnership
import com.oreki.stumpd.domain.model.PlayerMatchStats

/**
 * "That ball was a two, not a one" — correcting what a single delivery was worth.
 *
 * The widest blast radius of the four corrections, because runs propagate: the striker's figures,
 * the bowler's, the innings total, the partnership in progress **and the score recorded against
 * every wicket that fell later** all shift. That last one is the easiest to forget and the most
 * visible when it's wrong — the fall-of-wickets column stops adding up to the total.
 *
 * Two deliberate refusals. A wicket ball is off limits, because changing its value also moves the
 * score at which that wicket fell and interacts with the dismissal itself; correct the dismissal
 * separately. And a change that turns a legal ball into an illegal one (or back) is refused
 * unless asked for explicitly, because it renumbers every later ball in the innings and pulls
 * over boundaries away from the bowler changes they were aligned to.
 */
object AmendBallOp {

    fun apply(
        match: MatchHistory,
        correction: MatchCorrection.AmendBall,
        settings: MatchSettings,
    ): CorrectionOutcome {
        if (correction.innings !in 1..2) {
            return reject("BAD_INNINGS", "Innings must be 1 or 2, not ${correction.innings}.")
        }
        if (correction.newTotalRuns < 0) {
            return reject("NEGATIVE_RUNS", "A delivery can't be worth less than nothing.")
        }

        val inningsBalls = match.allDeliveries.filter { it.inning == correction.innings }
        if (inningsBalls.isEmpty()) {
            return reject(
                "REQUIRES_DELIVERY_TRAIL",
                "This match has no ball-by-ball record for that innings, so there's no ball to " +
                    "correct. Only matches scored ball by ball can be corrected this way.",
            )
        }

        val target = inningsBalls.firstOrNull {
            it.over == correction.over && it.ballInOver == correction.ballInOver
        } ?: return reject(
            "BALL_NOT_FOUND",
            "There's no ball ${correction.over}.${correction.ballInOver} in innings " +
                "${correction.innings}.",
        )

        if (target.isWicket()) {
            return reject(
                "WICKET_BALL",
                "A wicket fell on that ball. Correct the dismissal instead — changing what the " +
                    "ball was worth would also move the score the wicket fell at.",
            )
        }

        val wasLegal = target.isLegalBall()
        val willBeLegal = DeliveryOutcome.isLegalBall(
            DeliveryOutcome.render(correction.newKind, correction.newTotalRuns)
        )
        if (wasLegal != willBeLegal) {
            return reject(
                "CHANGES_BALL_LEGALITY",
                "That change turns ${if (wasLegal) "a legal ball into an extra" else "an extra into a legal ball"}, " +
                    "which re-numbers every later ball in the innings. Undo back to it and " +
                    "re-score instead.",
            )
        }

        val newOutcome = DeliveryOutcome.render(correction.newKind, correction.newTotalRuns)
        val oldTotal = target.effectiveRuns()
        val newTotal = correction.newTotalRuns
        if (newOutcome == target.outcome && newTotal == oldTotal) {
            return reject("NO_OP", "That ball is already recorded that way.")
        }

        val noBallRuns = settings.noballRuns
        val oldBatterRuns = DeliveryOutcome.batterRuns(target.outcome, oldTotal, noBallRuns)
        val newBatterRuns = DeliveryOutcome.batterRuns(newOutcome, newTotal, noBallRuns)
        val oldBowlerRuns = DeliveryOutcome.runsChargedToBowler(target.outcome, oldTotal)
        val newBowlerRuns = DeliveryOutcome.runsChargedToBowler(newOutcome, newTotal)

        val batting = battingOf(match, correction.innings)
        val bowling = bowlingOf(match, correction.innings)

        if (target.strikerName.isNotBlank() &&
            batting.none { sameName(it.name, target.strikerName) }
        ) {
            return reject(
                "STRIKER_NOT_IN_INNINGS",
                "${target.strikerName} faced that ball but has no batting row in the innings.",
            )
        }

        // ── the delivery itself ──────────────────────────────────────────────────────────
        val newDeliveries = match.allDeliveries.map { ball ->
            if (ball !== target) ball else ball.copy(
                outcome = newOutcome,
                runs = newTotal,
                highlight = DeliveryOutcome.isHighlight(correction.newKind, newTotal),
            )
        }

        // ── the striker: runs, and the shot-type buckets that feed the scoring breakdown ──
        val newBatting = batting.map { row ->
            if (!sameName(row.name, target.strikerName)) row else {
                val moved = row.copy(runs = (row.runs + newBatterRuns - oldBatterRuns).coerceAtLeast(0))
                bucket(bucket(moved, oldBatterRuns, -1), newBatterRuns, +1)
            }
        }

        // ── the bowler ───────────────────────────────────────────────────────────────────
        val newBowling = bowling.map { row ->
            if (!sameName(row.name, target.bowlerName)) row else row.copy(
                runsConceded = (row.runsConceded + newBowlerRuns - oldBowlerRuns).coerceAtLeast(0),
            )
        }

        // ── the partnership in progress when the ball was bowled ─────────────────────────
        val wicketsBefore = inningsBalls
            .takeWhile { it !== target }
            .count { it.isWicket() }
        val delta = newTotal - oldTotal
        val newPartnerships = partnershipsOf(match, correction.innings)
            .mapIndexed { index, stand ->
                if (index != wicketsBefore) stand else shiftPartnership(
                    stand = stand,
                    strikerName = target.strikerName,
                    totalDelta = delta,
                    batterDelta = newBatterRuns - oldBatterRuns,
                )
            }

        // ── every wicket that fell after this ball: the score it fell at moves ───────────
        val newFow = fowOf(match, correction.innings).map { row ->
            if (row.wicketNumber <= wicketsBefore) row
            else row.copy(runs = (row.runs + delta).coerceAtLeast(0))
        }

        val updated = writeBack(
            match = match,
            innings = correction.innings,
            batting = newBatting,
            bowling = newBowling,
            fow = newFow,
            partnerships = newPartnerships,
            inningsRuns = (inningsRunsOf(match, correction.innings) + delta).coerceAtLeast(0),
        ).copy(allDeliveries = newDeliveries)

        return CorrectionOutcome.Applied(before = match, after = updated)
    }

    private fun reject(code: String, message: String) =
        CorrectionOutcome.Rejected(listOf(CorrectionError(code, message)))

    private fun sameName(a: String?, b: String?): Boolean =
        a?.trim()?.lowercase() == b?.trim()?.lowercase()

    /** Moves one delivery in or out of the shot-type counters behind the scoring breakdown. */
    private fun bucket(row: PlayerMatchStats, runs: Int, delta: Int): PlayerMatchStats = when (runs) {
        0 -> row.copy(dots = (row.dots + delta).coerceAtLeast(0))
        1 -> row.copy(singles = (row.singles + delta).coerceAtLeast(0))
        2 -> row.copy(twos = (row.twos + delta).coerceAtLeast(0))
        3 -> row.copy(threes = (row.threes + delta).coerceAtLeast(0))
        4 -> row.copy(fours = (row.fours + delta).coerceAtLeast(0))
        6 -> row.copy(sixes = (row.sixes + delta).coerceAtLeast(0))
        else -> row // a five is real but uncounted, matching the live scorer
    }

    private fun shiftPartnership(
        stand: Partnership,
        strikerName: String,
        totalDelta: Int,
        batterDelta: Int,
    ): Partnership {
        val shifted = stand.copy(runs = (stand.runs + totalDelta).coerceAtLeast(0))
        return when {
            sameName(shifted.batsman1Name, strikerName) ->
                shifted.copy(batsman1Runs = (shifted.batsman1Runs + batterDelta).coerceAtLeast(0))
            sameName(shifted.batsman2Name, strikerName) ->
                shifted.copy(batsman2Runs = (shifted.batsman2Runs + batterDelta).coerceAtLeast(0))
            else -> shifted
        }
    }

    private fun battingOf(m: MatchHistory, innings: Int) =
        if (innings == 1) m.firstInningsBatting else m.secondInningsBatting

    private fun bowlingOf(m: MatchHistory, innings: Int) =
        if (innings == 1) m.firstInningsBowling else m.secondInningsBowling

    private fun fowOf(m: MatchHistory, innings: Int) =
        if (innings == 1) m.firstInningsFallOfWickets else m.secondInningsFallOfWickets

    private fun partnershipsOf(m: MatchHistory, innings: Int) =
        if (innings == 1) m.firstInningsPartnerships else m.secondInningsPartnerships

    private fun inningsRunsOf(m: MatchHistory, innings: Int) =
        if (innings == 1) m.firstInningsRuns else m.secondInningsRuns

    private fun writeBack(
        match: MatchHistory,
        innings: Int,
        batting: List<PlayerMatchStats>,
        bowling: List<PlayerMatchStats>,
        fow: List<FallOfWicket>,
        partnerships: List<Partnership>,
        inningsRuns: Int,
    ): MatchHistory = if (innings == 1) {
        match.copy(
            firstInningsRuns = inningsRuns,
            firstInningsBatting = batting,
            firstInningsBowling = bowling,
            firstInningsFallOfWickets = fow,
            firstInningsPartnerships = partnerships,
        )
    } else {
        match.copy(
            secondInningsRuns = inningsRuns,
            secondInningsBatting = batting,
            secondInningsBowling = bowling,
            secondInningsFallOfWickets = fow,
            secondInningsPartnerships = partnerships,
        )
    }
}
