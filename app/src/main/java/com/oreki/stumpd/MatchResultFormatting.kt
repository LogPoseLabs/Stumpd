package com.oreki.stumpd

import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.model.MatchSettings

/**
 * How many wickets a side can lose before its innings is over.
 *
 * In a full-strength game that's one fewer than the squad, because the last batter has nobody to
 * bat with. These matches usually allow single-side batting, where the last batter carries on
 * alone and can be dismissed too, so every player can be out.
 */
fun maxWicketsForSquad(squadSize: Int, allowSingleSideBatting: Boolean): Int = when {
    squadSize <= 0 -> 0
    allowSingleSideBatting -> squadSize
    else -> squadSize - 1
}

/**
 * Everyone who turned out for the side batting in [innings], including those who never batted.
 *
 * A side's batting rows only cover the players who actually came in, so the rest are recovered
 * from the innings they bowled in.
 */
fun squadSizeForInnings(match: MatchHistory, innings: Int): Int {
    val batted = if (innings == 1) match.firstInningsBatting else match.secondInningsBatting
    val bowledInOtherInnings =
        if (innings == 1) match.secondInningsBowling else match.firstInningsBowling
    return (batted + bowledInOtherInnings).distinctBy { it.name }.size
}

/**
 * Wickets the chasing side still had in hand, or null when the squad isn't recoverable from the
 * saved match.
 *
 * The stored margin can't be trusted for this: matches were saved with `maxPlayersPerTeam` floored
 * at 11 regardless of how many actually played, so an eight-a-side win read "by 9 wickets" — more
 * wickets than the side had batters.
 */
fun wicketsInHandForChase(match: MatchHistory, chasingSquadSize: Int? = null): Int? {
    val squadSize = chasingSquadSize?.takeIf { it > 0 }
        ?: squadSizeForInnings(match, innings = 2)
    if (squadSize <= 0) return null
    val settings = match.matchSettings ?: MatchSettings()
    val maxWickets = maxWicketsForSquad(squadSize, settings.allowSingleSideBatting)
    return (maxWickets - match.secondInningsWickets).coerceAtLeast(0)
}

/**
 * The result of a finished match, as one line.
 *
 * The wicket margin is recomputed from the squads rather than read from the saved string, so
 * matches played before the squad size was recorded correctly still read sensibly.
 *
 * [chasingSquadSize] is for screens that list matches without loading their per-player stats —
 * the history list does exactly that, so it supplies the size from a grouped query instead.
 */
fun matchResultLine(match: MatchHistory, chasingSquadSize: Int? = null): String {
    // An eliminator is its own sentence: the run and wicket margins below are both meaningless
    // when the scores finished level.
    if (match.superOverWinner != null && match.firstInningsRuns == match.secondInningsRuns) {
        return if (match.winnerTeam.equals("TIE", ignoreCase = true)) {
            "Match tied after the Super Over"
        } else {
            "${match.winnerTeam} won the Super Over"
        }
    }

    if (match.winnerTeam.equals("TIE", ignoreCase = true)) {
        return match.winningMargin.takeIf { it.isNotBlank() }
            ?.let { "Match tied • $it" }
            ?: "Match tied"
    }

    val chasingTeamWon = match.secondInningsRuns > match.firstInningsRuns
    val margin = if (chasingTeamWon) {
        wicketsInHandForChase(match, chasingSquadSize)?.let { wickets ->
            "$wickets wicket${if (wickets == 1) "" else "s"}"
        } ?: match.winningMargin
    } else {
        val runs = match.firstInningsRuns - match.secondInningsRuns
        if (runs > 0) "$runs run${if (runs == 1) "" else "s"}" else match.winningMargin
    }

    return if (margin.isBlank()) "${match.winnerTeam} won" else "${match.winnerTeam} won by $margin"
}

/** True when the side batting in [innings] was bowled out rather than running out of overs. */
fun wasAllOut(match: MatchHistory, innings: Int): Boolean {
    val squadSize = squadSizeForInnings(match, innings)
    if (squadSize <= 0) return false
    val settings = match.matchSettings ?: MatchSettings()
    val wickets = if (innings == 1) match.firstInningsWickets else match.secondInningsWickets
    return wickets >= maxWicketsForSquad(squadSize, settings.allowSingleSideBatting)
}

/**
 * How many wickets the chasing side still had standing when it won.
 *
 * The save-time twin of [wicketsInHandForChase] — that one re-derives the margin for display from
 * the stored squads, this one computes it from the figures at hand while the match is being
 * finished. Both exist because the margin is stored as a display string and so can go stale.
 */
fun calculateWicketMargin(
    wicketsLost: Int,
    totalPlayers: Int = 11,
    allowSingleSideBatting: Boolean = false,
): Int = (maxWicketsForSquad(totalPlayers, allowSingleSideBatting) - wicketsLost).coerceAtLeast(0)

/** The stored form of a result: a winning team name (or `"TIE"`) and a display margin. */
data class MatchResult(val winnerTeam: String, val winningMargin: String)

/**
 * Decides the result from the two innings' scores.
 *
 * Lifted out of the match-completion dialog so that a correction — which can change a score, and
 * therefore who won — can re-derive it rather than leaving a stale string behind. The output
 * strings are exactly what the completion path wrote, because plenty of code downstream reads
 * them: `"TIE"` / `"Scores level"` for a tie, `"N wickets"` for a successful chase, `"N runs"` for
 * a defended total.
 */
fun resolveMatchResult(
    team1Name: String,
    team2Name: String,
    firstInningsRuns: Int,
    secondInningsRuns: Int,
    secondInningsWickets: Int,
    chasingSquadSize: Int,
    allowSingleSideBatting: Boolean,
    /**
     * Who won the super over, when one was played — a team name, or `"TIE"` if the scorer chose to
     * leave it level.
     *
     * An *input* rather than something derived, and that is the whole point: the two innings totals
     * are equal by definition when a super over happens, so every later re-derivation — a
     * correction, a re-sync, the repair sweep — would otherwise quietly turn the eliminator back
     * into a tie. Ignored unless the scores really are level, so a correction that changes a score
     * makes the super over moot and the ordinary margin wins.
     */
    superOverWinner: String? = null,
): MatchResult = when {
    secondInningsRuns == firstInningsRuns && superOverWinner != null &&
        !superOverWinner.equals("TIE", ignoreCase = true) ->
        MatchResult(superOverWinner, "Super Over")

    secondInningsRuns == firstInningsRuns && superOverWinner != null ->
        MatchResult("TIE", "Tied after Super Over")

    secondInningsRuns == firstInningsRuns -> MatchResult("TIE", "Scores level")
    secondInningsRuns > firstInningsRuns -> MatchResult(
        winnerTeam = team2Name,
        winningMargin = "${
            calculateWicketMargin(
                wicketsLost = secondInningsWickets,
                totalPlayers = chasingSquadSize,
                allowSingleSideBatting = allowSingleSideBatting,
            )
        } wickets",
    )
    else -> MatchResult(team1Name, "${firstInningsRuns - secondInningsRuns} runs")
}

/** [resolveMatchResult] for a match that already exists, taking the squad from its own rows. */
fun resolveMatchResult(match: MatchHistory, settings: MatchSettings): MatchResult =
    resolveMatchResult(
        team1Name = match.team1Name,
        team2Name = match.team2Name,
        firstInningsRuns = match.firstInningsRuns,
        secondInningsRuns = match.secondInningsRuns,
        secondInningsWickets = match.secondInningsWickets,
        chasingSquadSize = squadSizeForInnings(match, innings = 2)
            .takeIf { it > 0 } ?: settings.maxPlayersPerTeam,
        allowSingleSideBatting = settings.allowSingleSideBatting,
        superOverWinner = match.superOverWinner,
    )
