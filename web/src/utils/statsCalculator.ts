/**
 * Build PlayerDetailedStats from Firestore match + stats data.
 * Mirrors the Android app's PlayerStorageManager logic.
 */

import type {
  MatchDocument,
  PlayerStatDocument,
  MatchPerformance,
  PlayerDetailedStats,
} from '../types';

interface MatchWithStats {
  match: MatchDocument;
  stats: PlayerStatDocument[];
}

function parseTotalOvers(_match: MatchDocument): number {
  // Match settings aren't stored as a top-level field in Firestore.
  // The default for most Stumpd games is 5 overs.
  // If we can infer from data, we try; otherwise default to 5.
  return 5;
}

/**
 * Normalize a player name for deduplication: trim, collapse whitespace, lowercase.
 */
function normalizePlayerName(name: string): string {
  return name.trim().replace(/\s+/g, ' ').toLowerCase();
}

/**
 * Convert oversBowled (stored as e.g. 2.3 meaning 2 overs 3 balls) to total balls.
 */
function oversToBalls(overs: number): number {
  const fullOvers = Math.floor(overs);
  const remainingBalls = Math.round((overs - fullOvers) * 10);
  return fullOvers * 6 + remainingBalls;
}

/**
 * Build a map of normalizedName -> PlayerDetailedStats from all match data.
 * Merges players with different IDs but the same name (case-insensitive).
 */
export function buildPlayerStats(
  matches: MatchWithStats[],
  filterGroupId?: string
): Map<string, PlayerDetailedStats> {
  // Phase 1: build by playerId (within each match, merge BAT + BOWL)
  const playerMap = new Map<string, PlayerDetailedStats>();

  for (const { match, stats } of matches) {
    if (filterGroupId && match.groupId !== filterGroupId) continue;

    const totalOvers = parseTotalOvers(match);

    // Group stats by player: merge BAT and BOWL entries for same player + match
    const playerPerfs = new Map<string, MatchPerformance>();

    for (const stat of stats) {
      const existing = playerPerfs.get(stat.playerId);

      if (stat.role === 'BAT') {
        if (existing) {
          playerPerfs.set(stat.playerId, {
            ...existing,
            runs: existing.runs + stat.runs,
            ballsFaced: existing.ballsFaced + stat.ballsFaced,
            fours: existing.fours + stat.fours,
            sixes: existing.sixes + stat.sixes,
            isOut: existing.isOut || stat.isOut,
            catches: existing.catches + stat.catches,
            runOuts: existing.runOuts + stat.runOuts,
            stumpings: existing.stumpings + stat.stumpings,
          });
        } else {
          playerPerfs.set(stat.playerId, {
            matchId: match.id,
            matchDate: match.matchDate,
            opposingTeam:
              stat.team === match.team1Name
                ? match.team2Name
                : match.team1Name,
            myTeam: stat.team,
            runs: stat.runs,
            ballsFaced: stat.ballsFaced,
            fours: stat.fours,
            sixes: stat.sixes,
            isOut: stat.isOut,
            wickets: 0,
            runsConceded: 0,
            ballsBowled: 0,
            catches: stat.catches,
            runOuts: stat.runOuts,
            stumpings: stat.stumpings,
            isWinner: stat.team === match.winnerTeam,
            isJoker: stat.isJoker,
            groupId: match.groupId ?? undefined,
            isShortPitch: match.shortPitch,
            matchTotalOvers: totalOvers,
            maidenOvers: 0,
          });
        }
      } else if (stat.role === 'BOWL') {
        const ballsBowled = oversToBalls(stat.oversBowled);
        if (existing) {
          playerPerfs.set(stat.playerId, {
            ...existing,
            wickets: existing.wickets + stat.wickets,
            runsConceded: existing.runsConceded + stat.runsConceded,
            ballsBowled: existing.ballsBowled + ballsBowled,
            maidenOvers: existing.maidenOvers + stat.maidenOvers,
            catches: existing.catches + stat.catches,
            runOuts: existing.runOuts + stat.runOuts,
            stumpings: existing.stumpings + stat.stumpings,
          });
        } else {
          playerPerfs.set(stat.playerId, {
            matchId: match.id,
            matchDate: match.matchDate,
            opposingTeam:
              stat.team === match.team1Name
                ? match.team2Name
                : match.team1Name,
            myTeam: stat.team,
            runs: 0,
            ballsFaced: 0,
            fours: 0,
            sixes: 0,
            isOut: false,
            wickets: stat.wickets,
            runsConceded: stat.runsConceded,
            ballsBowled: ballsBowled,
            catches: stat.catches,
            runOuts: stat.runOuts,
            stumpings: stat.stumpings,
            isWinner: stat.team === match.winnerTeam,
            isJoker: stat.isJoker,
            groupId: match.groupId ?? undefined,
            isShortPitch: match.shortPitch,
            matchTotalOvers: totalOvers,
            maidenOvers: stat.maidenOvers,
          });
        }
      }
    }

    // Add each player's performance from this match
    for (const [playerId, perf] of playerPerfs) {
      const statEntry = stats.find((s) => s.playerId === playerId);
      const playerName = statEntry?.name ?? 'Unknown';

      if (!playerMap.has(playerId)) {
        playerMap.set(playerId, {
          playerId,
          playerName,
          totalRuns: 0,
          totalBallsFaced: 0,
          totalBallsBowled: 0,
          totalWickets: 0,
          totalCatches: 0,
          totalRunOuts: 0,
          totalStumpings: 0,
          matchPerformances: [],
          matchesPlayed: 0,
        });
      }

      const player = playerMap.get(playerId)!;
      player.matchPerformances.push(perf);
      player.matchesPlayed++;
      player.totalRuns += perf.runs;
      player.totalBallsFaced += perf.ballsFaced;
      player.totalBallsBowled += perf.ballsBowled;
      player.totalWickets += perf.wickets;
      player.totalCatches += perf.catches;
      player.totalRunOuts += perf.runOuts;
      player.totalStumpings += perf.stumpings;
    }
  }

  // Phase 2: merge entries that share the same name (different IDs)
  const nameMap = new Map<string, PlayerDetailedStats>();

  for (const player of playerMap.values()) {
    const key = normalizePlayerName(player.playerName);
    const existing = nameMap.get(key);

    if (existing) {
      // Merge into existing
      existing.matchPerformances.push(...player.matchPerformances);
      existing.matchesPlayed += player.matchesPlayed;
      existing.totalRuns += player.totalRuns;
      existing.totalBallsFaced += player.totalBallsFaced;
      existing.totalBallsBowled += player.totalBallsBowled;
      existing.totalWickets += player.totalWickets;
      existing.totalCatches += player.totalCatches;
      existing.totalRunOuts += player.totalRunOuts;
      existing.totalStumpings += player.totalStumpings;
    } else {
      nameMap.set(key, { ...player });
    }
  }

  return nameMap;
}

// ==================== Aggregate stat helpers ====================

export function battingAverage(player: PlayerDetailedStats): number {
  const innings = player.matchPerformances.filter(
    (p) => p.ballsFaced > 0 || p.runs > 0
  );
  if (innings.length === 0) return 0;
  const dismissals = innings.filter((p) => p.isOut).length;
  return dismissals > 0
    ? player.totalRuns / dismissals
    : player.totalRuns;
}

export function battingStrikeRate(player: PlayerDetailedStats): number {
  return player.totalBallsFaced > 0
    ? (player.totalRuns / player.totalBallsFaced) * 100
    : 0;
}

export function bowlingAverage(player: PlayerDetailedStats): number {
  if (player.totalWickets === 0) return 0;
  const totalRunsConceded = player.matchPerformances.reduce(
    (s, p) => s + p.runsConceded,
    0
  );
  return totalRunsConceded / player.totalWickets;
}

export function bowlingEconomy(player: PlayerDetailedStats): number {
  if (player.totalBallsBowled === 0) return 0;
  const totalRunsConceded = player.matchPerformances.reduce(
    (s, p) => s + p.runsConceded,
    0
  );
  const totalOvers = player.totalBallsBowled / 6;
  return totalRunsConceded / totalOvers;
}

export function totalFours(player: PlayerDetailedStats): number {
  return player.matchPerformances.reduce((s, p) => s + p.fours, 0);
}

export function totalSixes(player: PlayerDetailedStats): number {
  return player.matchPerformances.reduce((s, p) => s + p.sixes, 0);
}

export function totalMaidens(player: PlayerDetailedStats): number {
  return player.matchPerformances.reduce((s, p) => s + p.maidenOvers, 0);
}

export function highestScore(player: PlayerDetailedStats): {
  runs: number;
  isNotOut: boolean;
} {
  let best = { runs: 0, isNotOut: false };
  for (const p of player.matchPerformances) {
    if (
      p.runs > best.runs ||
      (p.runs === best.runs && !p.isOut && best.isNotOut === false)
    ) {
      best = { runs: p.runs, isNotOut: !p.isOut };
    }
  }
  return best;
}

export function bestBowling(player: PlayerDetailedStats): {
  wickets: number;
  runs: number;
} {
  let best = { wickets: 0, runs: 999 };
  for (const p of player.matchPerformances) {
    if (p.ballsBowled > 0) {
      if (
        p.wickets > best.wickets ||
        (p.wickets === best.wickets && p.runsConceded < best.runs)
      ) {
        best = { wickets: p.wickets, runs: p.runsConceded };
      }
    }
  }
  return best.wickets > 0 ? best : { wickets: 0, runs: 0 };
}
