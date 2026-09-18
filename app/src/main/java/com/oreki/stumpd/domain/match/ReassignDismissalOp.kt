package com.oreki.stumpd.domain.match

import com.oreki.stumpd.domain.model.FallOfWicket
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.model.MatchSettings
import com.oreki.stumpd.domain.model.Partnership
import com.oreki.stumpd.domain.model.PlayerMatchStats
import com.oreki.stumpd.domain.model.WicketType

/**
 * "That's the wrong batter" — the correction this whole feature was asked for.
 *
 * A wicket is recorded in four places at once: the dismissed batter's scorecard row, the
 * fall-of-wickets row, the bowler's wicket tally, and (for a run-out or boundary-out) the
 * dismissed batter's name inside the delivery's outcome string. Fixing one and not the others is
 * what makes a scorecard contradict itself, so this moves all of them together.
 *
 * What it deliberately does *not* move: the runs and balls the two batters faced. A run-out at
 * the far end still costs the striker the delivery, and marking the wrong batter out never
 * misattributed a single run — only the dismissal. It also leaves the score and over at which the
 * wicket fell alone, because those were right.
 */
object ReassignDismissalOp {

    /** Dismissals the bowler is credited with. A run-out or boundary-out is nobody's wicket. */
    private val BOWLER_CREDITED = setOf(
        WicketType.BOWLED,
        WicketType.CAUGHT,
        WicketType.LBW,
        WicketType.STUMPED,
        WicketType.HIT_WICKET,
    )

    fun apply(
        match: MatchHistory,
        correction: MatchCorrection.ReassignDismissal,
        @Suppress("UNUSED_PARAMETER") settings: MatchSettings,
    ): CorrectionOutcome {
        val errors = mutableListOf<CorrectionError>()
        val warnings = mutableListOf<CorrectionWarning>()

        if (correction.innings !in 1..2) {
            return reject("BAD_INNINGS", "Innings must be 1 or 2, not ${correction.innings}.")
        }

        val batting = battingOf(match, correction.innings)
        val bowling = bowlingOf(match, correction.innings)
        val fow = fowOf(match, correction.innings)

        val wicket = fow.firstOrNull { it.wicketNumber == correction.wicketNumber }
            ?: return reject(
                "WICKET_NOT_FOUND",
                "There is no wicket ${correction.wicketNumber} in innings ${correction.innings}.",
            )

        val previouslyOut = batting.firstOrNull { sameName(it.name, wicket.batsmanName) }
        val nowOut = batting.firstOrNull { sameName(it.name, correction.outBatterName) }
            ?: return reject(
                "BATTER_NOT_IN_INNINGS",
                "${correction.outBatterName} didn't bat in that innings.",
            )

        // Two players with the same name can't be told apart anywhere in a saved match, so
        // refusing is the only honest answer.
        if (batting.count { sameName(it.name, correction.outBatterName) } > 1) {
            errors += CorrectionError(
                "AMBIGUOUS_NAME",
                "Two players in that innings are called ${correction.outBatterName}, so this " +
                    "correction can't tell them apart.",
            )
        }

        // Moving a wicket onto someone already dismissed elsewhere would leave two wickets on one
        // batter and one fewer than the team lost.
        val alreadyOutForAnotherWicket = fow.any {
            it.wicketNumber != correction.wicketNumber && sameName(it.batsmanName, nowOut.name)
        }
        if (alreadyOutForAnotherWicket) {
            errors += CorrectionError(
                "BATTER_ALREADY_OUT",
                "${nowOut.name} is already recorded as out for another wicket.",
            )
        }

        // The batter who was really out must have been at the crease for that stand — otherwise
        // the partnership list and the wicket disagree about who was batting.
        val stand = partnershipsOf(match, correction.innings)
            .getOrNull(correction.wicketNumber - 1)
        if (stand != null && !sameName(stand.batsman1Name, nowOut.name) &&
            !sameName(stand.batsman2Name, nowOut.name)
        ) {
            errors += CorrectionError(
                "BATTER_NOT_AT_CREASE",
                "${nowOut.name} wasn't at the crease when wicket ${correction.wicketNumber} " +
                    "fell — ${stand.batsman1Name} and ${stand.batsman2Name} were.",
            )
        }

        if (correction.dismissalType in BOWLER_CREDITED && correction.bowlerName == null) {
            errors += CorrectionError(
                "BOWLER_REQUIRED",
                "A ${correction.dismissalType.name.lowercase().replace('_', ' ')} needs a bowler.",
            )
        }
        correction.bowlerName?.let { name ->
            // Only a bowler the user is *choosing* has to be one who bowled. A match saved by an
            // older version can name a bowler from the other innings, and blocking on that would
            // mean the batter's name could never be fixed — the exact case this feature exists
            // for. Keeping a wrong inherited name is no worse than it already is.
            // Compared against the fall-of-wickets row as well as the batter's, because on a
            // legacy match the two can disagree and the editor offers what the row shows.
            val unchanged = previouslyOut?.bowlerName?.let { sameName(it, name) } == true ||
                wicket.bowlerName?.let { sameName(it, name) } == true
            if (!unchanged && bowling.none { sameName(it.name, name) }) {
                errors += CorrectionError(
                    "BOWLER_NOT_IN_INNINGS",
                    "$name didn't bowl in that innings.",
                )
            }
        }

        if (errors.isNotEmpty()) return CorrectionOutcome.Rejected(errors)

        val oldType = previouslyOut?.dismissalType?.let { runCatching { WicketType.valueOf(it) }.getOrNull() }
        val oldBowler = previouslyOut?.bowlerName
        val oldFielder = previouslyOut?.fielderName

        // ── the batting rows ──────────────────────────────────────────────────────────────
        val newBatting = batting.map { row ->
            when {
                previouslyOut != null && sameName(row.name, previouslyOut.name) &&
                    !sameName(row.name, nowOut.name) ->
                    row.copy(
                        isOut = false,
                        dismissalType = null,
                        bowlerName = null,
                        fielderName = null,
                    )

                sameName(row.name, nowOut.name) ->
                    row.copy(
                        isOut = true,
                        dismissalType = correction.dismissalType.name,
                        bowlerName = correction.bowlerName,
                        fielderName = correction.fielderName,
                    )

                else -> row
            }
        }

        // ── the bowler's wicket tally, from the type transition rather than the name ──────
        val creditedBefore = oldType in BOWLER_CREDITED
        val creditedAfter = correction.dismissalType in BOWLER_CREDITED
        var newBowling = bowling
        if (creditedBefore && oldBowler != null) {
            newBowling = adjust(newBowling, oldBowler, wickets = -1)
        }
        if (creditedAfter && correction.bowlerName != null) {
            newBowling = adjust(newBowling, correction.bowlerName, wickets = +1)
        }

        // ── fielding credit, which lives on the fielding side's rows ─────────────────────
        var fieldingSideBatting = battingOf(match, otherInnings(correction.innings))
        var fieldingSideBowling = newBowling
        if (oldFielder != null && oldType != null) {
            val (b, w) = adjustFielding(fieldingSideBatting, fieldingSideBowling, oldFielder, oldType, -1, warnings)
            fieldingSideBatting = b; fieldingSideBowling = w
        }
        if (correction.fielderName != null) {
            val (b, w) = adjustFielding(
                fieldingSideBatting, fieldingSideBowling, correction.fielderName,
                correction.dismissalType, +1, warnings,
            )
            fieldingSideBatting = b; fieldingSideBowling = w
        }

        // ── the fall-of-wickets row: who and how, not when ────────────────────────────────
        val newFow = fow.map { row ->
            if (row.wicketNumber != correction.wicketNumber) row
            else row.copy(
                batsmanName = nowOut.name,
                dismissalType = correction.dismissalType.name,
                bowlerName = correction.bowlerName,
                fielderName = correction.fielderName,
            )
        }

        // ── the name embedded in a run-out or boundary-out outcome ────────────────────────
        val newDeliveries = if (previouslyOut != null && !sameName(previouslyOut.name, nowOut.name)) {
            rewriteEmbeddedName(match, correction, previouslyOut.name, nowOut.name, warnings)
        } else {
            match.allDeliveries
        }

        // ── partnerships: the stand that ended keeps both names, but whoever survived it
        //    carries on into the following stands ──────────────────────────────────────────
        val newPartnerships = if (previouslyOut != null && !sameName(previouslyOut.name, nowOut.name)) {
            swapSurvivor(
                partnershipsOf(match, correction.innings),
                fromIndex = correction.wicketNumber,
                replace = nowOut.name,
                with = previouslyOut.name,
            )
        } else {
            partnershipsOf(match, correction.innings)
        }

        val updated = writeBack(
            match = match,
            innings = correction.innings,
            batting = newBatting,
            bowling = fieldingSideBowling,
            fieldingSideBatting = fieldingSideBatting,
            fow = newFow,
            partnerships = newPartnerships,
        ).copy(allDeliveries = newDeliveries)

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

    private fun partnershipsOf(m: MatchHistory, innings: Int) =
        if (innings == 1) m.firstInningsPartnerships else m.secondInningsPartnerships

    private fun adjust(
        rows: List<PlayerMatchStats>,
        name: String,
        wickets: Int,
    ): List<PlayerMatchStats> = rows.map { row ->
        if (sameName(row.name, name)) {
            row.copy(wickets = (row.wickets + wickets).coerceAtLeast(0))
        } else {
            row
        }
    }

    /**
     * Moves a catch, stumping or run-out credit by [delta].
     *
     * The fielder can be on either of the fielding side's two row sets — a bowler who took a
     * catch has the credit on their bowling row, an outfielder on their batting row — so both are
     * searched, and only the first match is adjusted.
     */
    private fun adjustFielding(
        fieldingBatting: List<PlayerMatchStats>,
        fieldingBowling: List<PlayerMatchStats>,
        fielder: String,
        type: WicketType,
        delta: Int,
        warnings: MutableList<CorrectionWarning>,
    ): Pair<List<PlayerMatchStats>, List<PlayerMatchStats>> {
        fun bump(row: PlayerMatchStats): PlayerMatchStats = when (type) {
            WicketType.CAUGHT -> row.copy(catches = clamp(row.catches + delta, row.catches, warnings, fielder, "catch"))
            WicketType.STUMPED -> row.copy(stumpings = clamp(row.stumpings + delta, row.stumpings, warnings, fielder, "stumping"))
            WicketType.RUN_OUT -> row.copy(runOuts = clamp(row.runOuts + delta, row.runOuts, warnings, fielder, "run-out"))
            else -> row
        }

        if (type !in setOf(WicketType.CAUGHT, WicketType.STUMPED, WicketType.RUN_OUT)) {
            return fieldingBatting to fieldingBowling
        }
        if (fieldingBowling.any { sameName(it.name, fielder) }) {
            return fieldingBatting to fieldingBowling.map { if (sameName(it.name, fielder)) bump(it) else it }
        }
        if (fieldingBatting.any { sameName(it.name, fielder) }) {
            return fieldingBatting.map { if (sameName(it.name, fielder)) bump(it) else it } to fieldingBowling
        }
        warnings += CorrectionWarning(
            "FIELDER_NOT_IN_MATCH",
            "$fielder has no row in this match, so the fielding credit wasn't moved.",
        )
        return fieldingBatting to fieldingBowling
    }

    private fun clamp(
        value: Int,
        previous: Int,
        warnings: MutableList<CorrectionWarning>,
        fielder: String,
        kind: String,
    ): Int {
        if (value >= 0) return value
        warnings += CorrectionWarning(
            "FIELDING_CREDIT_UNDERFLOW",
            "$fielder had no $kind recorded to take away, so their count stays at $previous.",
        )
        return 0
    }

    private fun rewriteEmbeddedName(
        match: MatchHistory,
        correction: MatchCorrection.ReassignDismissal,
        from: String,
        to: String,
        warnings: MutableList<CorrectionWarning>,
    ) = run {
        val inningsBalls = match.allDeliveries.filter { it.inning == correction.innings }
        val wicketBalls = inningsBalls.filter { it.isWicket() }
        val ball = wicketBalls.getOrNull(correction.wicketNumber - 1)
        when {
            ball == null -> {
                warnings += CorrectionWarning(
                    "WICKET_NOT_IN_TRAIL",
                    "The ball-by-ball record doesn't cover that wicket, so only the scorecard " +
                        "was corrected.",
                )
                match.allDeliveries
            }

            DeliveryOutcome.embeddedOutName(ball.outcome) == null -> match.allDeliveries

            else -> match.allDeliveries.map { delivery ->
                if (delivery !== ball) delivery
                else delivery.copy(
                    outcome = DeliveryOutcome.withEmbeddedOutName(delivery.outcome, to),
                )
            }
        }
    }

    /**
     * Follows the survivor forward through the innings.
     *
     * If X was recorded out but Y actually went, then X batted on — so every later stand that
     * lists Y should list X instead, with their runs following the name. Stops at the first stand
     * Y isn't in, which is where the two histories converge again.
     */
    internal fun swapSurvivor(
        partnerships: List<Partnership>,
        fromIndex: Int,
        replace: String,
        with: String,
    ): List<Partnership> {
        val result = partnerships.toMutableList()
        var index = fromIndex
        while (index < result.size) {
            val stand = result[index]
            result[index] = when {
                sameName(stand.batsman1Name, replace) -> stand.copy(batsman1Name = with)
                sameName(stand.batsman2Name, replace) -> stand.copy(batsman2Name = with)
                else -> return result
            }
            index++
        }
        return result
    }

    private fun writeBack(
        match: MatchHistory,
        innings: Int,
        batting: List<PlayerMatchStats>,
        bowling: List<PlayerMatchStats>,
        fieldingSideBatting: List<PlayerMatchStats>,
        fow: List<FallOfWicket>,
        partnerships: List<Partnership>,
    ): MatchHistory = if (innings == 1) {
        match.copy(
            firstInningsBatting = batting,
            firstInningsBowling = bowling,
            secondInningsBatting = fieldingSideBatting,
            firstInningsFallOfWickets = fow,
            firstInningsPartnerships = partnerships,
        )
    } else {
        match.copy(
            secondInningsBatting = batting,
            secondInningsBowling = bowling,
            firstInningsBatting = fieldingSideBatting,
            secondInningsFallOfWickets = fow,
            secondInningsPartnerships = partnerships,
        )
    }
}
