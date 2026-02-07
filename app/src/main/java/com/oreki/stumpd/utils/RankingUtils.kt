package com.oreki.stumpd.utils

import com.oreki.stumpd.data.manager.MatchPerformance
import com.oreki.stumpd.data.manager.PlayerDetailedStats
import kotlin.math.min
import kotlin.math.pow

/**
 * ICC-inspired player ranking system.
 *
 * Instead of normalizing aggregate totals against other players (volume-based),
 * this system rates each individual match performance on a 0-1000 scale and
 * computes a time-weighted career average where recent form matters more.
 *
 * Key differences from the old system:
 * - Per-match ratings instead of aggregate totals
 * - Time decay: recent matches weighted more (5% decay per older match)
 * - Absolute scale (0-1000) instead of relative to other players
 * - Role-aware overall rating (pure batters aren't penalized for not bowling)
 */
object RankingUtils {

    private const val DECAY_FACTOR = 0.95  // Each older match loses 5% weight

    // --- Per-innings rating functions (0-1000 scale) ---

    /**
     * Rate a single batting innings on a 0-1000 scale.
     *
     * Formula:
     *   base = runs * 18  (so ~55 runs at neutral SR = 1000)
     *   sr_factor = clamp(strikeRate / 125, 0.6, 1.5)
     *   not_out_bonus = 50 if not out with 10+ runs
     *   rating = min(base * sr_factor + bonus, 1000)
     */
    fun rateBattingInnings(perf: MatchPerformance): Double {
        if (perf.ballsFaced == 0 && perf.runs == 0) return 0.0

        val basePoints = perf.runs * 18.0
        val strikeRate = if (perf.ballsFaced > 0) {
            (perf.runs.toDouble() / perf.ballsFaced) * 100.0
        } else {
            0.0
        }
        val srFactor = (strikeRate / 125.0).coerceIn(0.6, 1.5)
        val notOutBonus = if (!perf.isOut && perf.runs >= 10) 50.0 else 0.0
        val raw = basePoints * srFactor + notOutBonus
        return min(raw, 1000.0)
    }

    /**
     * Rate a single bowling innings on a 0-1000 scale.
     *
     * Formula:
     *   wicket_points = wickets * 220  (3w = 660, 5w = 1100 before cap)
     *   econ_factor = clamp(7.0 / economy, 0.5, 1.5)
     *   rating = min(wicket_points * econ_factor, 1000)
     */
    fun rateBowlingInnings(perf: MatchPerformance): Double {
        if (perf.ballsBowled == 0) return 0.0

        val wicketPoints = perf.wickets * 220.0
        val economy = if (perf.ballsBowled > 0) {
            (perf.runsConceded.toDouble() / perf.ballsBowled) * 6.0
        } else {
            0.0
        }
        val econFactor = if (economy > 0) {
            (7.0 / economy).coerceIn(0.5, 1.5)
        } else {
            1.0
        }
        val raw = wicketPoints * econFactor
        return min(raw, 1000.0)
    }

    /**
     * Rate fielding in a single match on a 0-1000 scale.
     *
     * Formula:
     *   points = catches * 200 + runOuts * 250 + stumpings * 250
     *   rating = min(points, 1000)
     */
    fun rateFieldingMatch(perf: MatchPerformance): Double {
        val points = (perf.catches * 200.0) + (perf.runOuts * 250.0) + (perf.stumpings * 250.0)
        return min(points, 1000.0)
    }

    // --- Career rating functions (time-weighted averages) ---

    /**
     * Calculate time-weighted career batting rating (0-1000).
     * Matches are sorted by date (newest first), each weighted by [DECAY_FACTOR]^index.
     */
    fun calculateBattingRating(performances: List<MatchPerformance>): Double {
        // Only consider innings where the player actually batted
        val battingInnings = performances.filter { it.ballsFaced > 0 || it.runs > 0 }
        if (battingInnings.isEmpty()) return 0.0

        val sorted = battingInnings.sortedByDescending { it.matchDate }
        return timeWeightedAverage(sorted) { rateBattingInnings(it) }
    }

    /**
     * Calculate time-weighted career bowling rating (0-1000).
     * Matches are sorted by date (newest first), each weighted by [DECAY_FACTOR]^index.
     */
    fun calculateBowlingRating(performances: List<MatchPerformance>): Double {
        // Only consider innings where the player actually bowled
        val bowlingInnings = performances.filter { it.ballsBowled > 0 }
        if (bowlingInnings.isEmpty()) return 0.0

        val sorted = bowlingInnings.sortedByDescending { it.matchDate }
        return timeWeightedAverage(sorted) { rateBowlingInnings(it) }
    }

    /**
     * Calculate time-weighted career fielding rating (0-1000).
     * Matches are sorted by date (newest first), each weighted by [DECAY_FACTOR]^index.
     */
    fun calculateFieldingRating(performances: List<MatchPerformance>): Double {
        val fieldingMatches = performances.filter {
            it.catches > 0 || it.runOuts > 0 || it.stumpings > 0
        }
        if (fieldingMatches.isEmpty()) return 0.0

        val sorted = fieldingMatches.sortedByDescending { it.matchDate }
        return timeWeightedAverage(sorted) { rateFieldingMatch(it) }
    }

    /**
     * Calculate overall player rating (0-1000) with role-aware weighting.
     *
     * - All-rounder (batted AND bowled): 40% batting + 40% bowling + 20% fielding
     * - Pure batter (never bowled):      75% batting + 25% fielding
     * - Pure bowler (never batted):      75% bowling + 25% fielding
     *
     * Requires at least 3 matches to qualify.
     */
    fun calculateOverallRating(player: PlayerDetailedStats): Double {
        if (player.matchPerformances.size < 3) return 0.0

        val batting = calculateBattingRating(player.matchPerformances)
        val bowling = calculateBowlingRating(player.matchPerformances)
        val fielding = calculateFieldingRating(player.matchPerformances)

        val hasBatted = player.totalRuns > 0 || player.totalBallsFaced > 0
        val hasBowled = player.totalBallsBowled > 0

        return when {
            hasBatted && hasBowled -> batting * 0.40 + bowling * 0.40 + fielding * 0.20
            hasBatted -> batting * 0.75 + fielding * 0.25
            hasBowled -> bowling * 0.75 + fielding * 0.25
            else -> fielding  // Fielding-only player (unlikely)
        }
    }

    // --- Legacy compatibility ---

    /**
     * Legacy wrapper -- callers that used the old (player, allPlayers) signature
     * now get the new ICC-inspired rating. The allPlayers parameter is ignored
     * since the new system is absolute, not relative.
     */
    @Suppress("UNUSED_PARAMETER")
    fun calculateOverallScore(player: PlayerDetailedStats, allPlayers: List<PlayerDetailedStats>): Double {
        return calculateOverallRating(player)
    }

    // --- Helper ---

    /**
     * Compute a time-weighted average of per-match ratings.
     * The most recent match has weight 1.0, each subsequent match
     * decays by [DECAY_FACTOR] (0.95), so match 10 ago = 0.60, match 20 ago = 0.36.
     */
    private fun timeWeightedAverage(
        sortedPerformances: List<MatchPerformance>,
        ratingFn: (MatchPerformance) -> Double
    ): Double {
        var weightedSum = 0.0
        var totalWeight = 0.0

        sortedPerformances.forEachIndexed { index, perf ->
            val weight = DECAY_FACTOR.pow(index.toDouble())
            weightedSum += ratingFn(perf) * weight
            totalWeight += weight
        }

        return if (totalWeight > 0) weightedSum / totalWeight else 0.0
    }
}
