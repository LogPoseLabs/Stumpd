package com.oreki.stumpd.domain.match

import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.model.FallOfWicket
import com.oreki.stumpd.domain.model.PlayerMatchStats
import com.oreki.stumpd.domain.model.WicketType

/**
 * "That over was someone else's" — moving deliveries to the bowler who actually bowled them.
 *
 * Unlike a dismissal, this one *needs* the ball-by-ball record: without it there's no way to know
 * how many balls and runs to move, and guessing would quietly corrupt two bowlers' figures
 * instead of one. When the trail is missing the op refuses and says why.
 *
 * What moves: the legal balls, the runs charged (byes excluded, as the scorer records them), any
 * wickets the bowler was credited with on those balls, the bowler name on each delivery, and the
 * bowler named against those dismissals on both the fall-of-wickets rows and the dismissed
 * batters' scorecard lines. Maidens are left to the recompute pass, which derives them from the
 * trail anyway.
 */
object ReassignBowlerOp {

    private val BOWLER_CREDITED = setOf(
        WicketType.BOWLED,
        WicketType.CAUGHT,
        WicketType.LBW,
        WicketType.STUMPED,
        WicketType.HIT_WICKET,
    )

    fun apply(
        match: MatchHistory,
        correction: MatchCorrection.ReassignBowler,
    ): CorrectionOutcome {
        if (correction.innings !in 1..2) {
            return reject("BAD_INNINGS", "Innings must be 1 or 2, not ${correction.innings}.")
        }

        val inningsBalls = match.allDeliveries.filter { it.inning == correction.innings }
        if (inningsBalls.isEmpty()) {
            return reject(
                "REQUIRES_DELIVERY_TRAIL",
                "This match has no ball-by-ball record for that innings, so there's no way to " +
                    "tell which balls to move. The bowler's figures can only be corrected on a " +
                    "match that was scored ball by ball.",
            )
        }

        val moving = inningsBalls.filter { ball ->
            ball.over == correction.over &&
                (correction.ballInOver == null || ball.ballInOver == correction.ballInOver)
        }
        if (moving.isEmpty()) {
            return reject(
                "OVER_NOT_FOUND",
                "There's no over ${correction.over}" +
                    (correction.ballInOver?.let { ", ball $it" } ?: "") +
                    " in innings ${correction.innings}.",
            )
        }

        val fromNames = moving.map { it.bowlerName.trim() }.filter { it.isNotBlank() }.distinct()
        if (fromNames.isEmpty()) {
            return reject(
                "BOWLER_UNKNOWN",
                "The ball-by-ball record doesn't say who bowled that over.",
            )
        }
        if (fromNames.size > 1) {
            return reject(
                "MIXED_BOWLERS",
                "Over ${correction.over} is recorded as bowled by ${fromNames.joinToString(" and ")}. " +
                    "Move one ball at a time.",
            )
        }
        val from = fromNames.single()
        if (sameName(from, correction.toBowlerName)) {
            return reject("NO_OP", "${correction.toBowlerName} is already credited with those balls.")
        }

        val bowling = bowlingOf(match, correction.innings)
        val fieldingSideBatting = battingOf(match, otherInnings(correction.innings))

        if (bowling.none { sameName(it.name, from) }) {
            return reject(
                "BOWLER_NOT_IN_INNINGS",
                "$from has no bowling figures in that innings to move.",
            )
        }
        // The replacement must have played on the fielding side, or we'd be inventing a player.
        val onFieldingSide = bowling.any { sameName(it.name, correction.toBowlerName) } ||
            fieldingSideBatting.any { sameName(it.name, correction.toBowlerName) }
        if (!onFieldingSide) {
            return reject(
                "BOWLER_NOT_IN_MATCH",
                "${correction.toBowlerName} wasn't fielding in that innings.",
            )
        }
        if (bowling.count { sameName(it.name, from) } > 1 ||
            bowling.count { sameName(it.name, correction.toBowlerName) } > 1
        ) {
            return reject(
                "AMBIGUOUS_NAME",
                "Two bowlers in that innings share a name, so this correction can't tell them apart.",
            )
        }

        val ballsMoved = moving.count { it.isLegalBall() }
        val runsMoved = moving.sumOf { DeliveryOutcome.runsChargedToBowler(it.outcome, it.runs) }

        // Which of the innings' wickets fall on the balls being moved. Wicket balls appear in the
        // trail in the same order as the fall-of-wickets rows, which is how the two are matched.
        val wicketNumbersMoved = mutableListOf<Int>()
        var seen = 0
        inningsBalls.forEach { ball ->
            if (!ball.isWicket()) return@forEach
            seen++
            if (moving.any { it === ball }) wicketNumbersMoved += seen
        }
        val fow = fowOf(match, correction.innings)
        val creditedWicketsMoved = wicketNumbersMoved.count { number ->
            val row = fow.firstOrNull { it.wicketNumber == number }
            val type = row?.dismissalType?.let { runCatching { WicketType.valueOf(it) }.getOrNull() }
            type in BOWLER_CREDITED && sameName(row?.bowlerName, from)
        }

        // ── the two bowling rows ─────────────────────────────────────────────────────────
        var newBowling = bowling.map { row ->
            if (!sameName(row.name, from)) row else row.copy(
                oversBowled = oversFromBalls(
                    (MatchRecompute.ballsFromOvers(row.oversBowled) - ballsMoved).coerceAtLeast(0)
                ),
                runsConceded = (row.runsConceded - runsMoved).coerceAtLeast(0),
                wickets = (row.wickets - creditedWicketsMoved).coerceAtLeast(0),
            )
        }

        val existingTo = newBowling.firstOrNull { sameName(it.name, correction.toBowlerName) }
        newBowling = if (existingTo != null) {
            newBowling.map { row ->
                if (!sameName(row.name, correction.toBowlerName)) row else row.copy(
                    oversBowled = oversFromBalls(
                        MatchRecompute.ballsFromOvers(row.oversBowled) + ballsMoved
                    ),
                    runsConceded = row.runsConceded + runsMoved,
                    wickets = row.wickets + creditedWicketsMoved,
                )
            }
        } else {
            // First spell for this player in the innings: build the row from their batting one so
            // the id, team and joker flag are right.
            val template = fieldingSideBatting.first { sameName(it.name, correction.toBowlerName) }
            newBowling + PlayerMatchStats(
                id = template.id,
                name = template.name,
                team = template.team,
                role = "BOWL",
                oversBowled = oversFromBalls(ballsMoved),
                runsConceded = runsMoved,
                wickets = creditedWicketsMoved,
                isJoker = template.isJoker,
                bowlingPosition = newBowling.size + 1,
            )
        }

        // A bowler left with nothing didn't bowl: drop the row rather than leave an empty line on
        // the scorecard, and close the gap in the bowling order.
        newBowling = newBowling
            .filterNot { row ->
                sameName(row.name, from) &&
                    MatchRecompute.ballsFromOvers(row.oversBowled) == 0 &&
                    row.runsConceded == 0 &&
                    row.wickets == 0
            }
            .mapIndexed { index, row -> row.copy(bowlingPosition = index + 1) }

        // ── the trail ────────────────────────────────────────────────────────────────────
        val newDeliveries = match.allDeliveries.map { ball ->
            if (moving.any { it === ball }) ball.copy(bowlerName = correction.toBowlerName) else ball
        }

        // ── the dismissals those balls produced ──────────────────────────────────────────
        val newFow = fow.map { row ->
            if (row.wicketNumber in wicketNumbersMoved && sameName(row.bowlerName, from)) {
                row.copy(bowlerName = correction.toBowlerName)
            } else {
                row
            }
        }
        val movedBatterNames = newFow
            .filter { it.wicketNumber in wicketNumbersMoved }
            .map { it.batsmanName }
        val newBatting = battingOf(match, correction.innings).map { row ->
            if (movedBatterNames.any { sameName(it, row.name) } && sameName(row.bowlerName, from)) {
                row.copy(bowlerName = correction.toBowlerName)
            } else {
                row
            }
        }

        val updated = writeBack(
            match = match,
            innings = correction.innings,
            batting = newBatting,
            bowling = newBowling,
            fow = newFow,
        ).copy(allDeliveries = newDeliveries)

        val warnings = mutableListOf<CorrectionWarning>()
        val settings = match.matchSettings
        if (settings != null && settings.maxOversPerBowler > 0) {
            val to = newBowling.firstOrNull { sameName(it.name, correction.toBowlerName) }
            if (to != null && to.oversBowled > settings.maxOversPerBowler) {
                warnings += CorrectionWarning(
                    "OVER_LIMIT_EXCEEDED",
                    "${to.name} now has ${"%.1f".format(to.oversBowled)} overs, more than the " +
                        "${settings.maxOversPerBowler} this match allowed. Recorded anyway — " +
                        "it's what happened.",
                )
            }
        }

        return CorrectionOutcome.Applied(before = match, after = updated, warnings = warnings)
    }

    private fun reject(code: String, message: String) =
        CorrectionOutcome.Rejected(listOf(CorrectionError(code, message)))

    private fun sameName(a: String?, b: String?): Boolean =
        a?.trim()?.lowercase() == b?.trim()?.lowercase()

    private fun otherInnings(innings: Int) = if (innings == 1) 2 else 1

    private fun battingOf(m: MatchHistory, innings: Int) =
        if (innings == 1) m.firstInningsBatting else m.secondInningsBatting

    private fun bowlingOf(m: MatchHistory, innings: Int) =
        if (innings == 1) m.firstInningsBowling else m.secondInningsBowling

    private fun fowOf(m: MatchHistory, innings: Int) =
        if (innings == 1) m.firstInningsFallOfWickets else m.secondInningsFallOfWickets

    /** Balls back to the `O.B` notation the rest of the app stores. */
    private fun oversFromBalls(balls: Int): Double = (balls / 6) + (balls % 6) * 0.1

    private fun writeBack(
        match: MatchHistory,
        innings: Int,
        batting: List<PlayerMatchStats>,
        bowling: List<PlayerMatchStats>,
        fow: List<FallOfWicket>,
    ): MatchHistory = if (innings == 1) {
        match.copy(
            firstInningsBatting = batting,
            firstInningsBowling = bowling,
            firstInningsFallOfWickets = fow,
        )
    } else {
        match.copy(
            secondInningsBatting = batting,
            secondInningsBowling = bowling,
            secondInningsFallOfWickets = fow,
        )
    }
}
