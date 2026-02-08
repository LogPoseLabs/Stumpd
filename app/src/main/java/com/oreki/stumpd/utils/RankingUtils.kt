package com.oreki.stumpd.utils

import com.oreki.stumpd.data.manager.MatchPerformance
import com.oreki.stumpd.data.manager.PlayerDetailedStats
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * ICC-inspired player ranking system.
 *
 * Key design principles:
 * - Per-match ratings on a 0-1000 scale, **format-aware** (5-over, 10-over, 20-over)
 * - Real calendar-based time decay (weekly, not index-based)
 * - Inactivity penalty: players who haven't played recently see ratings drop
 * - Missed match dilution: players missing group matches get phantom 0-rating entries
 * - Form multiplier: recent 3-match form boosts/dampens career rating by up to ±15%
 * - Per-role experience ramp: need 10 innings in a discipline for full credit
 * - Role-aware overall rating (pure batters aren't penalized for not bowling)
 * - Maiden over bonus for bowling (scales with format)
 */
object RankingUtils {

    private const val WEEKLY_DECAY = 0.97              // Each week of age loses 3% weight
    private const val INACTIVITY_DECAY_PER_WEEK = 0.98 // 2% penalty per inactive week
    private const val INACTIVITY_GRACE_WEEKS = 2.0     // No penalty for first 2 weeks
    private const val EXPERIENCE_RAMP = 10.0           // Innings needed for full rating
    private const val MILLIS_PER_WEEK = 7.0 * 24 * 60 * 60 * 1000
    private const val FORM_MATCHES = 3                 // Number of recent matches for form calc
    private const val MAX_FORM_BOOST = 1.15            // Max +15% for hot streak
    private const val MIN_FORM_PENALTY = 0.85          // Max -15% for cold streak

    // --- Per-innings rating functions (0-1000 scale, format-aware) ---

    /**
     * Rate a single batting innings on a 0-1000 scale.
     *
     * Format-aware calibration:
     *   runsMultiplier = 400 / totalOvers  (80 for 5-over, 40 for 10-over, 20 for 20-over)
     *   srBenchmark = 100 + 500 / totalOvers  (200 for 5-over, 150 for 10-over, 125 for 20-over)
     *
     * 5-over examples (multiplier=80, srBenchmark=200):
     *   5 runs, SR 125 → 250  |  8 runs, SR 200 → 640  |  12 runs, SR 200 → 960
     *
     * 20-over examples (multiplier=20, srBenchmark=125):
     *   30 runs, SR 120 → 576  |  50 runs, SR 143 → 1000
     */
    fun rateBattingInnings(perf: MatchPerformance): Double {
        if (perf.ballsFaced == 0 && perf.runs == 0) return 0.0

        val totalOvers = perf.matchTotalOvers.coerceAtLeast(1)
        val runsMultiplier = 400.0 / totalOvers
        val srBenchmark = 100.0 + (500.0 / totalOvers)
        val notOutThreshold = max(3, totalOvers)

        val basePoints = perf.runs * runsMultiplier
        val strikeRate = if (perf.ballsFaced > 0) {
            (perf.runs.toDouble() / perf.ballsFaced) * 100.0
        } else {
            0.0
        }
        val srFactor = (strikeRate / srBenchmark).coerceIn(0.6, 1.5)
        val notOutBonus = if (!perf.isOut && perf.runs >= notOutThreshold) 50.0 else 0.0
        val raw = basePoints * srFactor + notOutBonus
        return min(raw, 1000.0)
    }

    /**
     * Rate a single bowling innings on a 0-1000 scale.
     *
     * Format-aware calibration:
     *   normalizedOvers = oversBowled * (20 / totalOvers)
     *   -- makes 1 over in a 5-over match equivalent to 4 overs in T20
     *
     * Maiden bonus (scales with format):
     *   maidenBonus = maidens * 100 * (20 / totalOvers)
     *   -- 1 maiden in 5-over match = 400pts (extraordinary!)
     *   -- 1 maiden in 20-over match = 100pts
     *
     * Economy benchmark stays at 7.0 (runs-per-over, format-independent).
     *
     * 5-over examples (1 over, normalizedOvers=4):
     *   0w eco 5.0 → 150  |  maiden 0w → 550  |  2w eco 4.0 → 780  |  4w eco 1.0 → 1000
     */
    fun rateBowlingInnings(perf: MatchPerformance): Double {
        if (perf.ballsBowled == 0) return 0.0

        val totalOvers = perf.matchTotalOvers.coerceAtLeast(1)
        val formatScale = 20.0 / totalOvers

        val oversBowled = perf.ballsBowled / 6.0
        val normalizedOvers = oversBowled * formatScale

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

        // Wicket reward (primary) — scaled by format
        val wicketRating = perf.wickets * (180.0 * formatScale / 4.0) * econFactor
        // ↑ At formatScale=4 (5-over), this equals wickets * 180 * econFactor (same as before)

        // Economy base: tight bowling is valuable even without wickets
        val econBase = min(normalizedOvers * 30.0 * econFactor, 150.0)

        // Maiden bonus
        val maidenBonus = perf.maidenOvers * (100.0 * formatScale)

        return min(wicketRating + econBase + maidenBonus, 1000.0)
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

    // --- Career rating functions (time-weighted averages + missed matches + form) ---

    /**
     * Calculate career batting rating (0-1000).
     *
     * @param performances All match performances for the player
     * @param allGroupMatches Optional list of (matchId, matchDate) for all group matches.
     *   When provided, missed matches inject phantom 0-rating entries to dilute the average.
     *   Pass null for "All Groups" view where missed-match penalty doesn't apply.
     */
    fun calculateBattingRating(
        performances: List<MatchPerformance>,
        allGroupMatches: List<Pair<String, Long>>? = null
    ): Double {
        val battingInnings = performances.filter { it.ballsFaced > 0 || it.runs > 0 }
        if (battingInnings.isEmpty()) return 0.0

        val sorted = battingInnings.sortedByDescending { it.matchDate }
        val playedMatchIds = sorted.map { it.matchId }.toSet()

        val rawRating = timeWeightedAverage(sorted, allGroupMatches, playedMatchIds) { rateBattingInnings(it) }
        val experienceFactor = min(battingInnings.size / EXPERIENCE_RAMP, 1.0)
        val inactivityFactor = calculateInactivityFactor(sorted.first().matchDate)
        val formMultiplier = calculateFormMultiplier(sorted) { rateBattingInnings(it) }

        return (rawRating * experienceFactor * inactivityFactor * formMultiplier).coerceIn(0.0, 1000.0)
    }

    /**
     * Calculate career bowling rating (0-1000).
     *
     * @param performances All match performances for the player
     * @param allGroupMatches Optional list of (matchId, matchDate) for all group matches.
     */
    fun calculateBowlingRating(
        performances: List<MatchPerformance>,
        allGroupMatches: List<Pair<String, Long>>? = null
    ): Double {
        val bowlingInnings = performances.filter { it.ballsBowled > 0 }
        if (bowlingInnings.isEmpty()) return 0.0

        val sorted = bowlingInnings.sortedByDescending { it.matchDate }
        val playedMatchIds = sorted.map { it.matchId }.toSet()

        val rawRating = timeWeightedAverage(sorted, allGroupMatches, playedMatchIds) { rateBowlingInnings(it) }
        val experienceFactor = min(bowlingInnings.size / EXPERIENCE_RAMP, 1.0)
        val inactivityFactor = calculateInactivityFactor(sorted.first().matchDate)
        val formMultiplier = calculateFormMultiplier(sorted) { rateBowlingInnings(it) }

        return (rawRating * experienceFactor * inactivityFactor * formMultiplier).coerceIn(0.0, 1000.0)
    }

    /**
     * Calculate career fielding rating (0-1000).
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
     *
     * @param allGroupMatches Optional group match context for missed-match penalty
     */
    fun calculateOverallRating(
        player: PlayerDetailedStats,
        allGroupMatches: List<Pair<String, Long>>? = null
    ): Double {
        if (player.matchPerformances.size < 3) return 0.0

        val batting = calculateBattingRating(player.matchPerformances, allGroupMatches)
        val bowling = calculateBowlingRating(player.matchPerformances, allGroupMatches)
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
     *
     * Each match is weighted by 0.97^(weeks since match).
     * A match played today has weight ~1.0, a match from 3 months ago ~0.67,
     * a match from 6 months ago ~0.45, a match from 1 year ago ~0.21.
     *
     * When [allGroupMatches] is provided, missed matches (in the group but not played
     * by this player) inject phantom 0-rating entries, diluting the average.
     */
    private fun timeWeightedAverage(
        sortedPerformances: List<MatchPerformance>,
        allGroupMatches: List<Pair<String, Long>>? = null,
        playedMatchIds: Set<String>? = null,
        ratingFn: (MatchPerformance) -> Double
    ): Double {
        val now = System.currentTimeMillis()
        var weightedSum = 0.0
        var totalWeight = 0.0

        // Real performances
        sortedPerformances.forEach { perf ->
            val weeksSince = (now - perf.matchDate).toDouble() / MILLIS_PER_WEEK
            val weight = WEEKLY_DECAY.pow(weeksSince)
            weightedSum += ratingFn(perf) * weight
            totalWeight += weight
        }

        // Phantom 0-rating entries for missed group matches
        if (allGroupMatches != null && playedMatchIds != null) {
            allGroupMatches.forEach { (matchId, matchDate) ->
                if (matchId !in playedMatchIds) {
                    val weeksSince = (now - matchDate).toDouble() / MILLIS_PER_WEEK
                    val weight = WEEKLY_DECAY.pow(weeksSince)
                    // weightedSum += 0 (phantom zero), but totalWeight increases
                    totalWeight += weight
                }
            }
        }

        return if (totalWeight > 0) weightedSum / totalWeight else 0.0
    }

    /**
     * Form multiplier: compare last [FORM_MATCHES] match ratings vs career average.
     *
     * - Hot streak (recent >> career): up to +15%
     * - Cold streak (recent << career): up to -15%
     * - Neutral: 1.0 (no change)
     *
     * Only applies when the player has more than [FORM_MATCHES] innings.
     */
    private fun calculateFormMultiplier(
        sortedPerformances: List<MatchPerformance>,
        ratingFn: (MatchPerformance) -> Double
    ): Double {
        if (sortedPerformances.size <= FORM_MATCHES) return 1.0

        val recentRatings = sortedPerformances.take(FORM_MATCHES).map { ratingFn(it) }
        val careerRatings = sortedPerformances.map { ratingFn(it) }

        val recentAvg = recentRatings.average()
        val careerAvg = careerRatings.average()

        if (careerAvg <= 0) return 1.0

        return (recentAvg / careerAvg).coerceIn(MIN_FORM_PENALTY, MAX_FORM_BOOST)
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
