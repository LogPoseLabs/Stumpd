package com.oreki.stumpd.domain.match

import com.oreki.stumpd.domain.model.PlayerImpact
import com.oreki.stumpd.domain.model.PlayerMatchStats

/**
 * Who mattered most in a match, and by how much.
 *
 * This is the formula behind the impact list and Player of the Match. It lived inside a local
 * closure in `ScoringActivity`, which meant it could only ever run once — at the moment a match
 * was completed. Correcting a saved match has to re-run it, because the scores it consumes are
 * exactly what a correction changes, and because the result feeds back in: the winning side gets
 * a 10% multiplier, so an edit that flips the outcome changes every player's score.
 *
 * Pure by design — no Android, no database. Pinned by `MatchImpactScoringTest`, whose numbers were
 * captured from the pre-move implementation.
 */
object MatchImpactScoring {

    data class Result(
        /** Jokers removed, best first. The head of this list is the award. */
        val impacts: List<PlayerImpact>,
        val playerOfTheMatch: PlayerImpact?,
    )

    fun compute(
        firstInningsBatting: List<PlayerMatchStats>,
        firstInningsBowling: List<PlayerMatchStats>,
        secondInningsBatting: List<PlayerMatchStats>,
        secondInningsBowling: List<PlayerMatchStats>,
        firstInningsRuns: Int,
        secondInningsRuns: Int,
        winnerTeam: String,
        team2Name: String,
        totalOvers: Int,
    ): Result {
        val aggMap: LinkedHashMap<String, Agg> = linkedMapOf()

        fun addBatting(rows: List<PlayerMatchStats>) = rows.forEach { p ->
            aggMap.mergeInto(
                Agg(
                    id = p.id,
                    name = p.name,
                    team = p.team,
                    runs = p.runs,
                    balls = p.ballsFaced,
                    fours = p.fours,
                    sixes = p.sixes,
                    notOut = (!p.isOut && p.ballsFaced > 0),
                    isJoker = p.isJoker,
                )
            )
        }

        fun addBowling(rows: List<PlayerMatchStats>) = rows.forEach { p ->
            aggMap.mergeInto(
                Agg(
                    id = p.id,
                    name = p.name,
                    team = p.team,
                    wkts = p.wickets,
                    rcv = p.runsConceded,
                    ballsBowled = (p.oversBowled * 6).toInt(),
                    isJoker = p.isJoker,
                )
            )
        }

        fun addFielding(rows: List<PlayerMatchStats>) = rows.forEach { p ->
            if (p.catches > 0 || p.runOuts > 0 || p.stumpings > 0) {
                aggMap.mergeInto(
                    Agg(
                        id = p.id,
                        name = p.name,
                        team = p.team,
                        catches = p.catches,
                        runOuts = p.runOuts,
                        stumpings = p.stumpings,
                        isJoker = p.isJoker,
                    )
                )
            }
        }

        addBatting(firstInningsBatting)
        addBatting(secondInningsBatting)
        addBowling(firstInningsBowling)
        addBowling(secondInningsBowling)
        addFielding(firstInningsBatting)
        addFielding(firstInningsBowling)
        addFielding(secondInningsBatting)
        addFielding(secondInningsBowling)

        val wasChaseWin = winnerTeam == team2Name

        val unsorted = aggMap.values.map { a ->
            PlayerImpact(
                id = a.id,
                name = a.name,
                team = a.team,
                impact = "%.1f".format(
                    score(
                        a = a,
                        totalOvers = totalOvers,
                        firstInningsRuns = firstInningsRuns,
                        secondInningsRuns = secondInningsRuns,
                        winnerTeam = winnerTeam,
                        wasChaseWin = wasChaseWin,
                    )
                ).toDouble(),
                summary = summarize(a),
                isJoker = a.isJoker,
                runs = a.runs,
                balls = a.balls,
                fours = a.fours,
                sixes = a.sixes,
                wickets = a.wkts,
                runsConceded = a.rcv,
                // overs.balls notation, as the rest of the app stores bowling figures
                oversBowled = (a.ballsBowled / 6) + (a.ballsBowled % 6) * 0.1,
            )
        }

        val ranked = unsorted.filterNot { it.isJoker }.sortedByDescending { it.impact }
        return Result(impacts = ranked, playerOfTheMatch = ranked.firstOrNull())
    }

    /**
     * One player's contribution, merged across innings and disciplines.
     *
     * Keyed by team *and* name below, because the joker appears on both sides and a name alone
     * would fold their two spells into one.
     */
    private data class Agg(
        val id: String,
        val name: String,
        val team: String,
        var runs: Int = 0,
        var balls: Int = 0,
        var fours: Int = 0,
        var sixes: Int = 0,
        var notOut: Boolean = false,
        var wkts: Int = 0,
        var rcv: Int = 0,
        var ballsBowled: Int = 0,
        var isJoker: Boolean = false,
        var catches: Int = 0,
        var runOuts: Int = 0,
        var stumpings: Int = 0,
    )

    private fun LinkedHashMap<String, Agg>.mergeInto(incoming: Agg) {
        val key = "${incoming.team}::${incoming.name.trim().lowercase()}"
        val existing = this[key]
        if (existing == null) {
            this[key] = incoming
            return
        }
        existing.runs += incoming.runs
        existing.balls += incoming.balls
        existing.fours += incoming.fours
        existing.sixes += incoming.sixes
        existing.notOut = existing.notOut || incoming.notOut
        existing.wkts += incoming.wkts
        existing.rcv += incoming.rcv
        existing.ballsBowled += incoming.ballsBowled
        existing.isJoker = existing.isJoker || incoming.isJoker
        existing.catches += incoming.catches
        existing.runOuts += incoming.runOuts
        existing.stumpings += incoming.stumpings
    }

    private fun summarize(a: Agg): String {
        val bat = if (a.balls > 0) "${a.runs}${if (a.notOut) "*" else ""}(${a.balls})" else ""
        val bowl = if (a.ballsBowled > 0) "${a.wkts}/${a.rcv}" else ""
        val field = buildList {
            if (a.catches > 0) add("${a.catches} ct")
            if (a.runOuts > 0) add("${a.runOuts} ro")
            if (a.stumpings > 0) add("${a.stumpings} st")
        }.joinToString(", ")
        return listOf(bat, bowl, field).filter { it.isNotBlank() }.joinToString(" and ")
    }

    private fun score(
        a: Agg,
        totalOvers: Int,
        firstInningsRuns: Int,
        secondInningsRuns: Int,
        winnerTeam: String,
        wasChaseWin: Boolean,
    ): Double {
        if (a.isJoker) return 0.0
        val overs = totalOvers.toDouble()

        // Match context
        val totalMatchRuns = firstInningsRuns + secondInningsRuns
        val actualMatchEconomy = totalMatchRuns / (overs * 2)

        val economyPar = when {
            overs <= 10 -> kotlin.math.max(8.0, actualMatchEconomy - 1.0)
            overs <= 20 -> kotlin.math.max(7.0, actualMatchEconomy - 0.5)
            else -> kotlin.math.max(5.0, actualMatchEconomy - 0.5)
        }

        // BALANCED weights - reduced batting emphasis
        val runsWeight = 15.0 / overs      // Reduced from 20.0
        val fourBonus = 0.8 * (15.0 / overs)  // Reduced multiplier from 1.0
        val sixBonus = 1.2 * (15.0 / overs)   // Reduced multiplier from 1.5
        val wicketWeight = 60.0 / overs       // Increased for bowling from 50.0

        // Batting calculation
        val sr = if (a.balls > 0) a.runs * 100.0 / a.balls else 0.0
        var bat = a.runs * runsWeight + a.fours * fourBonus + a.sixes * sixBonus

        val srBonus = if (a.balls >= 10)
            kotlin.math.max(0.0, kotlin.math.min(8.0, (sr - 100.0) / 6.0))  // Reduced cap & steeper
        else 0.0
        val chaseBonus = if (wasChaseWin) kotlin.math.min(10.0, a.runs / 6.0) else 0.0  // Reduced
        bat += srBonus + chaseBonus

        // Enhanced bowling with matching bonuses
        val eco = if (a.ballsBowled > 0) a.rcv * 6.0 / a.ballsBowled else 0.0
        val runPenalty = if (overs <= 20) 0.03 else 0.08  // Further reduced

        // Expanded economy impact range to match batting bonuses
        val economyImpact = when {
            eco <= economyPar - 2.0 -> 15.0    // Exceptional
            eco <= economyPar - 1.5 -> 12.0    // Brilliant
            eco <= economyPar - 1.0 -> 8.0     // Very good
            eco <= economyPar - 0.5 -> 4.0     // Good
            eco <= economyPar -> 0.0           // Par
            eco <= economyPar + 1.0 -> -3.0    // Expensive
            else -> -8.0                       // Very expensive
        }

        // Bowling "strike rate" bonus - reward quick wickets
        val bowlingStrikeRate = if (a.wkts > 0) a.ballsBowled.toDouble() / a.wkts else 999.0
        val strikeRateBonus = if (a.ballsBowled >= 12 && a.wkts > 0) {  // Min 2 overs
            val parSR = when {
                overs <= 10 -> 9.0   // Very aggressive formats
                overs <= 20 -> 12.0  // T20 standard
                else -> 18.0         // ODI standard
            }
            kotlin.math.max(0.0, kotlin.math.min(8.0, (parSR - bowlingStrikeRate) / 2.0))
        } else 0.0

        val bowlBase = a.wkts * wicketWeight - runPenalty * a.rcv + economyImpact + strikeRateBonus
        val fiveW = if (a.wkts >= 5) 15.0 else if (a.wkts >= 4) 8.0 else 0.0  // Adjusted
        val bowl = if (a.ballsBowled > 0) bowlBase + fiveW else 0.0

        // Fielding contributions
        val catchPoints = a.catches * 5.0  // 5 points per catch
        val runOutPoints = a.runOuts * 8.0  // 8 points per run-out (more impactful)
        val stumpingPoints = a.stumpings * 8.0  // 8 points per stumping (keeper skill)
        val fieldingScore = catchPoints + runOutPoints + stumpingPoints

        // True balance - equal weighting including fielding
        val base = when {
            a.balls > 0 && a.ballsBowled > 0 -> 0.5 * bat + 0.5 * bowl + fieldingScore
            a.balls > 0 -> bat + fieldingScore
            else -> bowl + fieldingScore
        }

        return if (a.team == winnerTeam) base * 1.10 else base
    }
}
