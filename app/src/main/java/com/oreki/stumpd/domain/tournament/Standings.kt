package com.oreki.stumpd.domain.tournament

/**
 * The league table.
 *
 * Recomputed from match results on every read, like every other aggregate in this app — so a
 * correction that changes who won changes the table too, with nothing to remember to invalidate.
 */

/** Cricket over notation (2.3 means two overs and three balls) as a ball count. */
fun oversNotationToBalls(overs: Double): Int {
    if (overs <= 0.0) return 0
    val whole = overs.toInt()
    // Rounded, because the stored figure is a Double and 0.3 is not exactly representable.
    val balls = Math.round((overs - whole) * 10).toInt()
    return whole * 6 + balls
}

enum class MatchOutcome { WIN, TIE, NO_RESULT }

/** Two for a win, one for a tie. Stored per tournament, so a group can disagree. */
data class PointsRule(
    val win: Int = 2,
    val tie: Int = 1,
    val loss: Int = 0,
    val noResult: Int = 1,
)

/**
 * One played fixture, reduced to what a table needs.
 *
 * The two sides are unordered — `matches.team1Name` means "batted first", not "home" — so nothing
 * here assumes which is which.
 */
data class TournamentMatchResult(
    val matchId: String,
    val teamAId: String,
    val teamBId: String,
    val outcome: MatchOutcome,
    val winnerTeamId: String?,
    val teamARuns: Int,
    val teamABalls: Int,
    val teamAAllOut: Boolean,
    val teamBRuns: Int,
    val teamBBalls: Int,
    val teamBAllOut: Boolean,
)

data class StandingsRow(
    val teamId: String,
    val teamName: String,
    val poolOrdinal: Int,
    val played: Int = 0,
    val won: Int = 0,
    val lost: Int = 0,
    val tied: Int = 0,
    val noResult: Int = 0,
    val points: Int = 0,
    val runsFor: Int = 0,
    val ballsFaced: Int = 0,
    val runsAgainst: Int = 0,
    val ballsBowled: Int = 0,
) {
    /**
     * Net run rate, or null when no balls have been recorded.
     *
     * Null rather than zero, because "no data" and "exactly par" are different things and sorting
     * them together would flatter a team that hasn't played.
     */
    val netRunRate: Double?
        get() {
            if (ballsFaced == 0 || ballsBowled == 0) return null
            return (runsFor * 6.0 / ballsFaced) - (runsAgainst * 6.0 / ballsBowled)
        }
}

/**
 * Who won, as a team id.
 *
 * Compared with `equals` and never `contains`: a side called "Tie Breakers" must not be read as a
 * tie, which is a live hazard the moment team names are user-chosen. And deliberately *not* decided
 * by comparing the two innings totals — a super over leaves those level while `winnerTeam` names a
 * side, so a totals comparison would score an eliminator as a tie.
 *
 * A winner matching neither side — a corrupted row, a team renamed out from under the match — is a
 * no result rather than a crash or a silently wrong win.
 */
fun resolveTournamentOutcome(
    winnerTeam: String,
    teamAId: String,
    teamAName: String,
    teamBId: String,
    teamBName: String,
): Pair<MatchOutcome, String?> = when {
    winnerTeam.isBlank() -> MatchOutcome.NO_RESULT to null
    winnerTeam.equals("TIE", ignoreCase = true) -> MatchOutcome.TIE to null
    winnerTeam.equals(teamAName, ignoreCase = true) -> MatchOutcome.WIN to teamAId
    winnerTeam.equals(teamBName, ignoreCase = true) -> MatchOutcome.WIN to teamBId
    else -> MatchOutcome.NO_RESULT to null
}

/**
 * The table, ordered by points then net run rate.
 *
 * A side bowled out is charged the full quota of overs for its net run rate, as the real rule has
 * it — otherwise being dismissed cheaply in ten balls would *improve* a team's run rate.
 */
fun computeStandings(
    teams: List<TeamRef>,
    results: List<TournamentMatchResult>,
    points: PointsRule = PointsRule(),
    allottedBallsPerInnings: Int,
): List<StandingsRow> {
    val rows = teams.associate { team ->
        team.teamId to StandingsRow(
            teamId = team.teamId,
            teamName = team.name,
            poolOrdinal = team.poolOrdinal,
        )
    }.toMutableMap()

    results.forEach { result ->
        val a = rows[result.teamAId]
        val b = rows[result.teamBId]
        if (a == null || b == null) return@forEach

        /** Balls to charge a side: its own, or the full quota if it was bowled out. */
        fun charged(balls: Int, allOut: Boolean) =
            if (allOut) allottedBallsPerInnings else balls.coerceAtMost(allottedBallsPerInnings)

        // A no result — reachable here only via corrupted data, since a real save always
        // resolves to a team name or "TIE" — counts as played but must not move net run rate at
        // all, the same way a rained-off match never happened for NRR purposes.
        val aBalls = if (result.outcome == MatchOutcome.NO_RESULT) 0 else {
            charged(result.teamABalls, result.teamAAllOut)
        }
        val bBalls = if (result.outcome == MatchOutcome.NO_RESULT) 0 else {
            charged(result.teamBBalls, result.teamBAllOut)
        }
        val aRuns = if (result.outcome == MatchOutcome.NO_RESULT) 0 else result.teamARuns
        val bRuns = if (result.outcome == MatchOutcome.NO_RESULT) 0 else result.teamBRuns

        fun accrue(row: StandingsRow, ownRuns: Int, ownBalls: Int, oppRuns: Int, oppBalls: Int) =
            row.copy(
                played = row.played + 1,
                won = row.won + if (result.winnerTeamId == row.teamId) 1 else 0,
                lost = row.lost + if (
                    result.outcome == MatchOutcome.WIN && result.winnerTeamId != row.teamId
                ) 1 else 0,
                tied = row.tied + if (result.outcome == MatchOutcome.TIE) 1 else 0,
                noResult = row.noResult + if (result.outcome == MatchOutcome.NO_RESULT) 1 else 0,
                points = row.points + when {
                    result.outcome == MatchOutcome.NO_RESULT -> points.noResult
                    result.outcome == MatchOutcome.TIE -> points.tie
                    result.winnerTeamId == row.teamId -> points.win
                    else -> points.loss
                },
                runsFor = row.runsFor + ownRuns,
                ballsFaced = row.ballsFaced + ownBalls,
                runsAgainst = row.runsAgainst + oppRuns,
                ballsBowled = row.ballsBowled + oppBalls,
            )

        rows[a.teamId] = accrue(a, aRuns, aBalls, bRuns, bBalls)
        rows[b.teamId] = accrue(b, bRuns, bBalls, aRuns, aBalls)
    }

    return rows.values.sortedWith(
        compareByDescending<StandingsRow> { it.points }
            // A null net run rate sorts last rather than as zero.
            .thenByDescending { it.netRunRate ?: Double.NEGATIVE_INFINITY }
            .thenByDescending { it.won }
            .thenByDescending { it.runsFor }
            .thenBy { it.teamName.lowercase() },
    )
}
