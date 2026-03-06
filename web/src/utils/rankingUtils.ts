/**
 * ICC-inspired player ranking system.
 * Ported from RankingUtils.kt (Android app).
 *
 * Key design principles:
 * - Per-match ratings on 0-1000 scale, format-aware (5-over, 10-over, 20-over)
 * - Real calendar-based time decay (weekly, not index-based)
 * - Inactivity penalty for players who haven't played recently
 * - Missed match dilution: phantom 0-rating entries for missed group matches
 * - Form multiplier: recent 3-match form boosts/dampens rating by up to ±15%
 * - Per-role experience ramp: 10 innings needed for full credit
 * - Role-aware overall rating (pure batters aren't penalized for not bowling)
 * - Maiden over bonus for bowling (scales with format)
 */

import type { MatchPerformance, PlayerDetailedStats } from '../types';

const WEEKLY_DECAY = 0.97;
const INACTIVITY_DECAY_PER_WEEK = 0.98;
const INACTIVITY_GRACE_WEEKS = 2.0;
const EXPERIENCE_RAMP = 10.0;
const MILLIS_PER_WEEK = 7.0 * 24 * 60 * 60 * 1000;
const FORM_MATCHES = 3;
const MAX_FORM_BOOST = 1.15;
const MIN_FORM_PENALTY = 0.85;

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}

// ==================== Per-innings rating functions ====================

export function rateBattingInnings(perf: MatchPerformance): number {
  if (perf.ballsFaced === 0 && perf.runs === 0) return 0;

  const totalOvers = Math.max(perf.matchTotalOvers, 1);
  const runsMultiplier = 400.0 / totalOvers;
  const srBenchmark = 100.0 + 500.0 / totalOvers;
  const notOutThreshold = Math.max(3, totalOvers);

  const basePoints = perf.runs * runsMultiplier;
  const strikeRate =
    perf.ballsFaced > 0 ? (perf.runs / perf.ballsFaced) * 100.0 : 0;
  const srFactor = clamp(strikeRate / srBenchmark, 0.6, 1.5);
  const notOutBonus =
    !perf.isOut && perf.runs >= notOutThreshold ? 50.0 : 0.0;
  const raw = basePoints * srFactor + notOutBonus;
  return Math.min(raw, 1000.0);
}

export function rateBowlingInnings(perf: MatchPerformance): number {
  if (perf.ballsBowled === 0) return 0;

  const totalOvers = Math.max(perf.matchTotalOvers, 1);
  const formatScale = 20.0 / totalOvers;
  const oversBowled = perf.ballsBowled / 6.0;
  const normalizedOvers = oversBowled * formatScale;

  const economy =
    perf.ballsBowled > 0
      ? (perf.runsConceded / perf.ballsBowled) * 6.0
      : 0;
  const econFactor =
    economy > 0 ? clamp(7.0 / economy, 0.5, 2.0) : 1.5;

  const wicketRating =
    perf.wickets * ((180.0 * formatScale) / 4.0) * econFactor;
  const econBase = Math.min(normalizedOvers * 30.0 * econFactor, 150.0);
  const maidenBonus = perf.maidenOvers * 100.0 * formatScale;

  return Math.min(wicketRating + econBase + maidenBonus, 1000.0);
}

export function rateFieldingMatch(perf: MatchPerformance): number {
  const points =
    perf.catches * 200.0 + perf.runOuts * 250.0 + perf.stumpings * 250.0;
  return Math.min(points, 1000.0);
}

// ==================== Career rating functions ====================

function timeWeightedAverage(
  sortedPerformances: MatchPerformance[],
  allGroupMatches: [string, number][] | null,
  playedMatchIds: Set<string> | null,
  ratingFn: (p: MatchPerformance) => number
): number {
  const now = Date.now();
  let weightedSum = 0;
  let totalWeight = 0;

  for (const perf of sortedPerformances) {
    const weeksSince = (now - perf.matchDate) / MILLIS_PER_WEEK;
    const weight = Math.pow(WEEKLY_DECAY, weeksSince);
    weightedSum += ratingFn(perf) * weight;
    totalWeight += weight;
  }

  // Phantom 0-rating entries for missed group matches
  if (allGroupMatches && playedMatchIds) {
    for (const [matchId, matchDate] of allGroupMatches) {
      if (!playedMatchIds.has(matchId)) {
        const weeksSince = (now - matchDate) / MILLIS_PER_WEEK;
        const weight = Math.pow(WEEKLY_DECAY, weeksSince);
        totalWeight += weight;
      }
    }
  }

  return totalWeight > 0 ? weightedSum / totalWeight : 0;
}

function calculateFormMultiplier(
  sortedPerformances: MatchPerformance[],
  ratingFn: (p: MatchPerformance) => number
): number {
  if (sortedPerformances.length <= FORM_MATCHES) return 1.0;

  const recentRatings = sortedPerformances
    .slice(0, FORM_MATCHES)
    .map(ratingFn);
  const careerRatings = sortedPerformances.map(ratingFn);

  const recentAvg =
    recentRatings.reduce((a, b) => a + b, 0) / recentRatings.length;
  const careerAvg =
    careerRatings.reduce((a, b) => a + b, 0) / careerRatings.length;

  if (careerAvg <= 0) return 1.0;
  return clamp(recentAvg / careerAvg, MIN_FORM_PENALTY, MAX_FORM_BOOST);
}

function calculateInactivityFactor(mostRecentMatchDate: number): number {
  const weeksSinceLast =
    (Date.now() - mostRecentMatchDate) / MILLIS_PER_WEEK;
  const inactiveWeeks = Math.max(weeksSinceLast - INACTIVITY_GRACE_WEEKS, 0);
  return Math.pow(INACTIVITY_DECAY_PER_WEEK, inactiveWeeks);
}

export function calculateBattingRating(
  performances: MatchPerformance[],
  allGroupMatches: [string, number][] | null = null
): number {
  const battingInnings = performances.filter(
    (p) => p.ballsFaced > 0 || p.runs > 0
  );
  if (battingInnings.length === 0) return 0;

  const sorted = [...battingInnings].sort(
    (a, b) => b.matchDate - a.matchDate
  );
  const playedMatchIds = new Set(sorted.map((p) => p.matchId));

  const rawRating = timeWeightedAverage(
    sorted,
    allGroupMatches,
    playedMatchIds,
    rateBattingInnings
  );
  const experienceFactor = Math.min(
    battingInnings.length / EXPERIENCE_RAMP,
    1.0
  );
  const inactivityFactor = calculateInactivityFactor(sorted[0].matchDate);
  const formMultiplier = calculateFormMultiplier(sorted, rateBattingInnings);

  return clamp(
    rawRating * experienceFactor * inactivityFactor * formMultiplier,
    0,
    1000
  );
}

export function calculateBowlingRating(
  performances: MatchPerformance[],
  allGroupMatches: [string, number][] | null = null
): number {
  const bowlingInnings = performances.filter((p) => p.ballsBowled > 0);
  if (bowlingInnings.length === 0) return 0;

  const sorted = [...bowlingInnings].sort(
    (a, b) => b.matchDate - a.matchDate
  );
  const playedMatchIds = new Set(sorted.map((p) => p.matchId));

  const rawRating = timeWeightedAverage(
    sorted,
    allGroupMatches,
    playedMatchIds,
    rateBowlingInnings
  );
  const experienceFactor = Math.min(
    bowlingInnings.length / EXPERIENCE_RAMP,
    1.0
  );
  const inactivityFactor = calculateInactivityFactor(sorted[0].matchDate);
  const formMultiplier = calculateFormMultiplier(sorted, rateBowlingInnings);

  return clamp(
    rawRating * experienceFactor * inactivityFactor * formMultiplier,
    0,
    1000
  );
}

export function calculateFieldingRating(
  performances: MatchPerformance[]
): number {
  const fieldingMatches = performances.filter(
    (p) => p.catches > 0 || p.runOuts > 0 || p.stumpings > 0
  );
  if (fieldingMatches.length === 0) return 0;

  const sorted = [...fieldingMatches].sort(
    (a, b) => b.matchDate - a.matchDate
  );
  const rawRating = timeWeightedAverage(sorted, null, null, rateFieldingMatch);
  const experienceFactor = Math.min(
    fieldingMatches.length / EXPERIENCE_RAMP,
    1.0
  );
  const inactivityFactor = calculateInactivityFactor(sorted[0].matchDate);
  return rawRating * experienceFactor * inactivityFactor;
}

export function calculateOverallRating(
  player: PlayerDetailedStats,
  allGroupMatches: [string, number][] | null = null
): number {
  if (player.matchPerformances.length < 3) return 0;

  const batting = calculateBattingRating(
    player.matchPerformances,
    allGroupMatches
  );
  const bowling = calculateBowlingRating(
    player.matchPerformances,
    allGroupMatches
  );
  const fielding = calculateFieldingRating(player.matchPerformances);

  const hasBatted =
    player.totalRuns > 0 || player.totalBallsFaced > 0;
  const hasBowled = player.totalBallsBowled > 0;

  if (hasBatted && hasBowled) {
    return batting * 0.4 + bowling * 0.4 + fielding * 0.2;
  } else if (hasBatted) {
    return batting * 0.75 + fielding * 0.25;
  } else if (hasBowled) {
    return bowling * 0.75 + fielding * 0.25;
  }
  return fielding;
}
