package com.oreki.stumpd.utils

import com.oreki.stumpd.data.manager.MatchPerformance
import com.oreki.stumpd.data.manager.PlayerDetailedStats
import kotlin.math.min
import kotlin.math.pow

/**
 * ICC-inspired player ranking system.
 *
 * Key design principles:
 * - Per-match ratings on a 0-1000 scale
 * - Real calendar-based time decay (weekly, not index-based)
 * - Inactivity penalty: players who haven't played recently see ratings drop
 * - Per-role experience ramp: need 10 innings in a discipline for full credit
 * - Role-aware overall rating (pure batters aren't penalized for not bowling)
 */
object RankingUtils {

    private const val WEEKLY_DECAY = 0.97              // Each week of age loses 3% weight
    private const val INACTIVITY_DECAY_PER_WEEK = 0.98 // 2% penalty per inactive week
    private const val INACTIVITY_GRACE_WEEKS = 2.0     // No penalty for first 2 weeks
    private const val EXPERIENCE_RAMP = 10.0           // Innings needed for full rating
    private const val MILLIS_PER_WEEK = 7.0 * 24 * 60 * 60 * 1000

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
     * Two components:
     *   1. Wicket reward: wickets * 180 * econFactor
     *   2. Economy base:  overs * 30 * econFactor (capped at 150)
     *      -- rewards tight bowling even without wickets
     *
     * econFactor = clamp(7.0 / economy, 0.5, 2.0)
     *
     * Examples (4 overs, eco 3.0):
     *   0 wickets → 0 + 150 = 150  (tight spell, not worthless)
     *   1 wicket  → 360 + 150 = 510
     *   2 wickets → 720 + 150 = 870
     *
     * Examples (2 overs, eco 10.0):
     *   0 wickets → 0 + 42 = 42  (expensive, barely any credit)
     *   1 wicket  → 126 + 42 = 168
     */
    fun rateBowlingInnings(perf: MatchPerformance): Double {
        if (perf.ballsBowled == 0) return 0.0

        val oversBowled = perf.ballsBowled / 6.0
        val economy = if (perf.ballsBowled > 0) {
            (perf.runsConceded.toDouble() / perf.ballsBowled) * 6.0
        } else {
            0.0
        }
        val econFactor = if (economy > 0) {
            (7.0 / economy).coerceIn(0.5, 2.0)
        } else {
            1.5
        }

        // Wicket reward (primary)
        val wicketRating = perf.wickets * 180.0 * econFactor

        // Economy base: tight bowling is valuable even without wickets
        // ~30 pts per over scaled by economy, capped at 150
        val econBase = min(oversBowled * 30.0 * econFactor, 150.0)

        return min(wicketRating + econBase, 1000.0)
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

    // --- Career rating functions (time-weighted averages + inactivity) ---

    /**
     * Calculate career batting rating (0-1000).
     * Uses real calendar-based decay + inactivity penalty.
     */
    fun calculateBattingRating(performances: List<MatchPerformance>): Double {
        val battingInnings = performances.filter { it.ballsFaced > 0 || it.runs > 0 }
        if (battingInnings.isEmpty()) return 0.0

        val sorted = battingInnings.sortedByDescending { it.matchDate }
        val rawRating = timeWeightedAverage(sorted) { rateBattingInnings(it) }
        val experienceFactor = min(battingInnings.size / EXPERIENCE_RAMP, 1.0)
        val inactivityFactor = calculateInactivityFactor(sorted.first().matchDate)
        return rawRating * experienceFactor * inactivityFactor
    }

    /**
     * Calculate career bowling rating (0-1000).
     * Uses real calendar-based decay + inactivity penalty.
     */
    fun calculateBowlingRating(performances: List<MatchPerformance>): Double {
        val bowlingInnings = performances.filter { it.ballsBowled > 0 }
        if (bowlingInnings.isEmpty()) return 0.0

        val sorted = bowlingInnings.sortedByDescending { it.matchDate }
        val rawRating = timeWeightedAverage(sorted) { rateBowlingInnings(it) }
        val experienceFactor = min(bowlingInnings.size / EXPERIENCE_RAMP, 1.0)
        val inactivityFactor = calculateInactivityFactor(sorted.first().matchDate)
        return rawRating * experienceFactor * inactivityFactor
    }

    /**
     * Calculate career fielding rating (0-1000).
     * Uses real calendar-based decay + inactivity penalty.
     */
    fun calculateFieldingRating(performances: List<MatchPerformance>): Double {
        val fieldingMatches = performances.filter {
            it.catches > 0 || it.runOuts > 0 || it.stumpings > 0
        }
        if (fieldingMatches.isEmpty()) return 0.0

        val sorted = fieldingMatches.sortedByDescending { it.matchDate }
        val rawRating = timeWeightedAverage(sorted) { rateFieldingMatch(it) }
        val experienceFactor = min(fieldingMatches.size / EXPERIENCE_RAMP, 1.0)
        val inactivityFactor = calculateInactivityFactor(sorted.first().matchDate)
        return rawRating * experienceFactor * inactivityFactor
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
            else -> fielding
        }
    }

    // --- Legacy compatibility ---

    @Suppress("UNUSED_PARAMETER")
    fun calculateOverallScore(player: PlayerDetailedStats, allPlayers: List<PlayerDetailedStats>): Double {
        return calculateOverallRating(player)
    }

    // --- Helpers ---

    /**
     * Compute a time-weighted average using real calendar decay.
     * Each match is weighted by 0.97^(weeks since match).
     * A match played today has weight ~1.0, a match from 3 months ago ~0.67,
     * a match from 6 months ago ~0.45, a match from 1 year ago ~0.21.
     */
    private fun timeWeightedAverage(
        sortedPerformances: List<MatchPerformance>,
        ratingFn: (MatchPerformance) -> Double
    ): Double {
        val now = System.currentTimeMillis()
        var weightedSum = 0.0
        var totalWeight = 0.0

        sortedPerformances.forEach { perf ->
            val weeksSince = (now - perf.matchDate).toDouble() / MILLIS_PER_WEEK
            val weight = WEEKLY_DECAY.pow(weeksSince)
            weightedSum += ratingFn(perf) * weight
            totalWeight += weight
        }

        return if (totalWeight > 0) weightedSum / totalWeight else 0.0
    }

    /**
     * Inactivity penalty: if a player's most recent match was more than
     * [INACTIVITY_GRACE_WEEKS] ago, their rating decays by 2% per additional week.
     *
     * - Played within 2 weeks: no penalty (factor = 1.0)
     * - 1 month inactive:  factor ≈ 0.96
     * - 3 months inactive: factor ≈ 0.80
     * - 6 months inactive: factor ≈ 0.62
     * - 1 year inactive:   factor ≈ 0.37
     */
    private fun calculateInactivityFactor(mostRecentMatchDate: Long): Double {
        val weeksSinceLast = (System.currentTimeMillis() - mostRecentMatchDate).toDouble() / MILLIS_PER_WEEK
        val inactiveWeeks = (weeksSinceLast - INACTIVITY_GRACE_WEEKS).coerceAtLeast(0.0)
        return INACTIVITY_DECAY_PER_WEEK.pow(inactiveWeeks)
    }
}
