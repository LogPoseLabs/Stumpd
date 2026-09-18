package com.oreki.stumpd.domain.match

import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.model.PlayerMatchStats
import com.oreki.stumpd.ui.scoring.formatBallsAsOvers

/**
 * What a correction would change, in the words the scorecard uses.
 *
 * This is the safety feature: a correction can rewrite a result and reshuffle an award, so the
 * user has to see the consequences *before* anything is written, not discover them afterwards.
 * Lines are phrased as "before → after" so both halves are visible — a summary that only shows
 * the new value can't be checked against memory.
 */
object MatchCorrectionDiff {

    enum class Scope { Batting, Bowling, Fielding, Wickets, Partnerships, Balls, Result, Award, Score }

    data class Line(val scope: Scope, val text: String)

    data class Diff(
        val lines: List<Line>,
        val resultChanged: Boolean,
        val awardChanged: Boolean,
    ) {
        val isEmpty: Boolean get() = lines.isEmpty()
    }

    fun of(before: MatchHistory, after: MatchHistory): Diff {
        val lines = mutableListOf<Line>()

        // ── the score ────────────────────────────────────────────────────────────────────
        if (before.firstInningsRuns != after.firstInningsRuns ||
            before.firstInningsWickets != after.firstInningsWickets
        ) {
            lines += Line(
                Scope.Score,
                "${before.team1Name}: ${innings(before, 1)} → ${innings(after, 1)}",
            )
        }
        if (before.secondInningsRuns != after.secondInningsRuns ||
            before.secondInningsWickets != after.secondInningsWickets
        ) {
            lines += Line(
                Scope.Score,
                "${before.team2Name}: ${innings(before, 2)} → ${innings(after, 2)}",
            )
        }

        // ── players, by id so a substitution reads as one change rather than two ────────
        lines += statLines(
            before = before.firstInningsBatting + before.secondInningsBatting,
            after = after.firstInningsBatting + after.secondInningsBatting,
            batting = true,
        )
        lines += statLines(
            before = before.firstInningsBowling + before.secondInningsBowling,
            after = after.firstInningsBowling + after.secondInningsBowling,
            batting = false,
        )

        // ── wickets ─────────────────────────────────────────────────────────────────────
        (1..2).forEach { innings ->
            val b = if (innings == 1) before.firstInningsFallOfWickets else before.secondInningsFallOfWickets
            val a = if (innings == 1) after.firstInningsFallOfWickets else after.secondInningsFallOfWickets
            a.forEach { row ->
                val old = b.firstOrNull { it.wicketNumber == row.wicketNumber } ?: return@forEach
                if (old.batsmanName != row.batsmanName) {
                    lines += Line(
                        Scope.Wickets,
                        "Wicket ${row.wicketNumber}: ${old.batsmanName} → ${row.batsmanName}",
                    )
                }
                if (old.bowlerName != row.bowlerName) {
                    lines += Line(
                        Scope.Wickets,
                        "Wicket ${row.wicketNumber} taken by: ${old.bowlerName ?: "nobody"} → " +
                            "${row.bowlerName ?: "nobody"}",
                    )
                }
                if (old.runs != row.runs) {
                    lines += Line(
                        Scope.Wickets,
                        "Wicket ${row.wicketNumber} fell at: ${old.runs} → ${row.runs}",
                    )
                }
            }
            if (b.size != a.size) {
                lines += Line(Scope.Wickets, "Wickets in innings $innings: ${b.size} → ${a.size}")
            }
        }

        // ── partnerships ────────────────────────────────────────────────────────────────
        (1..2).forEach { innings ->
            val b = if (innings == 1) before.firstInningsPartnerships else before.secondInningsPartnerships
            val a = if (innings == 1) after.firstInningsPartnerships else after.secondInningsPartnerships
            a.forEachIndexed { index, stand ->
                val old = b.getOrNull(index) ?: return@forEachIndexed
                if (old.batsman1Name != stand.batsman1Name || old.batsman2Name != stand.batsman2Name) {
                    lines += Line(
                        Scope.Partnerships,
                        "Stand ${index + 1}: ${pair(old.batsman1Name, old.batsman2Name)} → " +
                            pair(stand.batsman1Name, stand.batsman2Name),
                    )
                }
                if (old.runs != stand.runs) {
                    lines += Line(
                        Scope.Partnerships,
                        "Stand ${index + 1} worth: ${old.runs} → ${stand.runs}",
                    )
                }
            }
        }

        // ── individual balls ────────────────────────────────────────────────────────────
        before.allDeliveries.zip(after.allDeliveries).forEach { (old, now) ->
            if (old.outcome != now.outcome) {
                lines += Line(
                    Scope.Balls,
                    "Ball ${now.over}.${now.ballInOver}: ${old.outcome} → ${now.outcome}",
                )
            } else if (old.bowlerName != now.bowlerName) {
                lines += Line(
                    Scope.Balls,
                    "Ball ${now.over}.${now.ballInOver} bowled by: ${old.bowlerName} → ${now.bowlerName}",
                )
            }
        }

        // ── the result and the award, always stated, changed or not ─────────────────────
        val resultChanged = before.winnerTeam != after.winnerTeam ||
            before.winningMargin != after.winningMargin
        lines += Line(
            Scope.Result,
            if (resultChanged) {
                "Result: ${result(before)} → ${result(after)}"
            } else {
                "Result unchanged — ${result(after)}"
            },
        )

        val awardChanged = before.playerOfTheMatchName != after.playerOfTheMatchName
        if (awardChanged) {
            lines += Line(
                Scope.Award,
                "Player of the Match: ${before.playerOfTheMatchName ?: "none"} → " +
                    "${after.playerOfTheMatchName ?: "none"}",
            )
        }

        return Diff(lines = lines, resultChanged = resultChanged, awardChanged = awardChanged)
    }

    private fun statLines(
        before: List<PlayerMatchStats>,
        after: List<PlayerMatchStats>,
        batting: Boolean,
    ): List<Line> {
        val lines = mutableListOf<Line>()
        val byId = before.associateBy { it.id to it.role }

        after.forEach { now ->
            val old = byId[now.id to now.role]
            if (old == null) {
                lines += Line(
                    if (batting) Scope.Batting else Scope.Bowling,
                    if (batting) "${now.name} added: ${battingFigures(now)}"
                    else "${now.name} added: ${bowlingFigures(now)}",
                )
                return@forEach
            }
            if (batting) {
                if (battingFigures(old) != battingFigures(now)) {
                    lines += Line(Scope.Batting, "${now.name}: ${battingFigures(old)} → ${battingFigures(now)}")
                }
            } else {
                if (bowlingFigures(old) != bowlingFigures(now)) {
                    lines += Line(Scope.Bowling, "${now.name}: ${bowlingFigures(old)} → ${bowlingFigures(now)}")
                }
            }
            if (old.catches != now.catches || old.runOuts != now.runOuts || old.stumpings != now.stumpings) {
                lines += Line(
                    Scope.Fielding,
                    "${now.name} in the field: ${fielding(old)} → ${fielding(now)}",
                )
            }
            if (old.name != now.name) {
                lines += Line(
                    if (batting) Scope.Batting else Scope.Bowling,
                    "${old.name} → ${now.name}",
                )
            }
        }

        val afterIds = after.map { it.id to it.role }.toSet()
        before.filterNot { (it.id to it.role) in afterIds }.forEach { gone ->
            lines += Line(
                if (batting) Scope.Batting else Scope.Bowling,
                "${gone.name} removed (was ${if (batting) battingFigures(gone) else bowlingFigures(gone)})",
            )
        }
        return lines
    }

    private fun battingFigures(row: PlayerMatchStats): String =
        "${row.runs}${if (row.isOut) "" else "*"} (${row.ballsFaced})" +
            (row.dismissalType?.let { " ${dismissal(row)}" } ?: "")

    private fun dismissal(row: PlayerMatchStats): String = when (row.dismissalType) {
        "CAUGHT" -> "c ${row.fielderName ?: "?"} b ${row.bowlerName ?: "?"}"
        "BOWLED" -> "b ${row.bowlerName ?: "?"}"
        "LBW" -> "lbw ${row.bowlerName ?: "?"}"
        "RUN_OUT" -> "run out (${row.fielderName ?: "?"})"
        "STUMPED" -> "st ${row.fielderName ?: "?"} b ${row.bowlerName ?: "?"}"
        "HIT_WICKET" -> "hit wicket ${row.bowlerName ?: "?"}"
        "BOUNDARY_OUT" -> "boundary out"
        else -> ""
    }

    private fun bowlingFigures(row: PlayerMatchStats): String =
        "${formatBallsAsOvers(MatchRecompute.ballsFromOvers(row.oversBowled))}-" +
            "${row.maidenOvers}-${row.runsConceded}-${row.wickets}"

    private fun fielding(row: PlayerMatchStats): String {
        val parts = buildList {
            if (row.catches > 0) add("${row.catches} ct")
            if (row.runOuts > 0) add("${row.runOuts} ro")
            if (row.stumpings > 0) add("${row.stumpings} st")
        }
        return if (parts.isEmpty()) "nothing" else parts.joinToString(", ")
    }

    private fun innings(match: MatchHistory, innings: Int): String =
        if (innings == 1) "${match.firstInningsRuns}/${match.firstInningsWickets}"
        else "${match.secondInningsRuns}/${match.secondInningsWickets}"

    private fun result(match: MatchHistory): String =
        if (match.winnerTeam == "TIE") "tied"
        else "${match.winnerTeam} won by ${match.winningMargin}"

    private fun pair(a: String, b: String) = "$a & $b"
}
