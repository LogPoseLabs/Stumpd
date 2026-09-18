package com.oreki.stumpd.domain.match

import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.model.MatchSettings
import com.oreki.stumpd.maxWicketsForSquad
import com.oreki.stumpd.squadSizeForInnings

/**
 * The things that must be true of a scorecard for it to make sense to a reader.
 *
 * These aren't a schema — the database will happily store a match where four batters are out but
 * only three wickets fell. They're the statements a person would notice being broken: the
 * fall-of-wickets column not matching the dismissals, more wickets than the side had batters, a
 * player appearing twice.
 *
 * Judged **relative to where the match started**, not absolutely. Plenty of matches in the wild
 * were saved by older versions and already break one of these; refusing to correct them would
 * make the feature useless exactly where it's needed most. So a correction is rejected only when
 * it *introduces* a violation, and a pre-existing one is reported as something the correction
 * didn't fix rather than something it caused.
 */
object MatchInvariants {

    data class Violation(val code: String, val message: String)

    fun check(match: MatchHistory, settings: MatchSettings): List<Violation> = buildList {
        (1..2).forEach { innings ->
            val batting = if (innings == 1) match.firstInningsBatting else match.secondInningsBatting
            val bowling = if (innings == 1) match.firstInningsBowling else match.secondInningsBowling
            val fow = if (innings == 1) match.firstInningsFallOfWickets else match.secondInningsFallOfWickets
            val storedWickets = if (innings == 1) match.firstInningsWickets else match.secondInningsWickets
            val storedRuns = if (innings == 1) match.firstInningsRuns else match.secondInningsRuns
            val dismissed = batting.count { it.isOut }

            if (fow.size != dismissed) {
                add(
                    Violation(
                        "FOW_COUNT",
                        "Innings $innings lists $dismissed dismissed " +
                            "${plural(dismissed, "batter")} but ${fow.size} " +
                            "${plural(fow.size, "fall-of-wickets line")}.",
                    )
                )
            }
            if (storedWickets != dismissed) {
                add(
                    Violation(
                        "WICKET_COUNT",
                        "Innings $innings is recorded as $storedWickets down, but $dismissed " +
                            "${plural(dismissed, "batter")} ${if (dismissed == 1) "is" else "are"} out.",
                    )
                )
            }
            if (fow.map { it.wicketNumber } != (1..fow.size).toList()) {
                add(
                    Violation(
                        "FOW_NUMBERING",
                        "Innings $innings has gaps or repeats in its wicket numbering.",
                    )
                )
            }

            val squad = squadSizeForInnings(match, innings)
            val ceiling = maxWicketsForSquad(squad, settings.allowSingleSideBatting)
            if (squad > 0 && dismissed > ceiling) {
                add(
                    Violation(
                        "WICKETS_OVER_SQUAD",
                        "Innings $innings has $dismissed wickets, more than the $ceiling a squad " +
                            "of $squad can lose.",
                    )
                )
            }

            val battedRuns = batting.sumOf { it.runs }
            if (battedRuns > storedRuns) {
                add(
                    Violation(
                        "BATTER_RUNS_EXCEED_TOTAL",
                        "Innings $innings totals $storedRuns but its batters have $battedRuns " +
                            "between them.",
                    )
                )
            }

            val creditedWickets = bowling.sumOf { it.wickets }
            if (creditedWickets > dismissed) {
                add(
                    Violation(
                        "BOWLER_WICKETS_EXCEED_DISMISSALS",
                        "Innings $innings credits bowlers with $creditedWickets " +
                            "${plural(creditedWickets, "wicket")} but only $dismissed " +
                            "${plural(dismissed, "batter")} out.",
                    )
                )
            }

            val duplicateRows = (batting + bowling)
                .groupBy { Triple(it.team, it.role, it.id) }
                .filterValues { it.size > 1 }
            if (duplicateRows.isNotEmpty()) {
                add(
                    Violation(
                        "DUPLICATE_ROWS",
                        "Innings $innings has two rows for the same player and role " +
                            "(${duplicateRows.keys.joinToString { it.third }}).",
                    )
                )
            }

            (batting + bowling).forEach { row ->
                val negatives = listOf(
                    "runs" to row.runs, "balls" to row.ballsFaced, "wickets" to row.wickets,
                    "runs conceded" to row.runsConceded, "catches" to row.catches,
                    "run-outs" to row.runOuts, "stumpings" to row.stumpings,
                ).filter { it.second < 0 }
                if (negatives.isNotEmpty() || row.oversBowled < 0.0) {
                    add(
                        Violation(
                            "NEGATIVE_FIGURE",
                            "${row.name} has a negative figure in innings $innings " +
                                "(${negatives.joinToString { it.first }}).",
                        )
                    )
                }
            }
        }

        val duplicateImpacts = match.playerImpacts.groupBy { it.id }.filterValues { it.size > 1 }
        if (duplicateImpacts.isNotEmpty()) {
            add(
                Violation(
                    "DUPLICATE_IMPACTS",
                    "More than one impact score for ${duplicateImpacts.keys.joinToString()}.",
                )
            )
        }
    }

    /**
     * Violations the correction is responsible for — the ones that weren't there before.
     *
     * Compared by code *and* message so "innings 1 doesn't add up" and "innings 2 doesn't add up"
     * are separate problems rather than one being masked by the other.
     */
    fun introduced(
        before: MatchHistory,
        after: MatchHistory,
        settings: MatchSettings,
    ): List<Violation> {
        val existing = check(before, settings).toSet()
        return check(after, settings).filterNot { it in existing }
    }

    /** Violations the correction cleared up, worth telling the user about. */
    fun resolved(
        before: MatchHistory,
        after: MatchHistory,
        settings: MatchSettings,
    ): List<Violation> {
        val remaining = check(after, settings).toSet()
        return check(before, settings).filterNot { it in remaining }
    }

    private fun plural(count: Int, noun: String) = if (count == 1) noun else "${noun}s"
}
