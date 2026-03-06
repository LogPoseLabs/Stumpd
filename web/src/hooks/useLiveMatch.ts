import { useState, useEffect } from 'react';
import { doc, onSnapshot } from 'firebase/firestore';
import { db } from '../firebase';
import type {
  InProgressMatchDocument,
  LivePlayerState,
  MatchSettings,
  DeliveryDocument,
} from '../types';

export interface LiveMatchState {
  raw: InProgressMatchDocument | null;
  battingTeam: string;
  bowlingTeam: string;
  currentRuns: number;
  currentWickets: number;
  currentOvers: string;
  target: number | null;
  strikerName: string;
  nonStrikerName: string;
  bowlerName: string;
  battingPlayers: LivePlayerState[];
  bowlingPlayers: LivePlayerState[];
  deliveries: DeliveryDocument[];
  matchSettings: MatchSettings | null;
  isFirstInnings: boolean;
  firstInningsRuns: number;
  firstInningsWickets: number;
  runRate: number;
  requiredRate: number | null;
}

function safeJsonParse<T>(json: string | undefined | null, fallback: T): T {
  if (!json) return fallback;
  try {
    return JSON.parse(json);
  } catch {
    return fallback;
  }
}

function parseLiveState(raw: InProgressMatchDocument): LiveMatchState {
  const settings = safeJsonParse<MatchSettings | null>(
    raw.matchSettingsJson,
    null
  );
  const isFirst = raw.currentInnings === 1;

  const team1Players = safeJsonParse<LivePlayerState[]>(
    raw.team1PlayersJson,
    []
  );
  const team2Players = safeJsonParse<LivePlayerState[]>(
    raw.team2PlayersJson,
    []
  );

  const battingTeam = isFirst ? raw.team1Name : raw.team2Name;
  const bowlingTeam = isFirst ? raw.team2Name : raw.team1Name;
  const battingPlayers = isFirst ? team1Players : team2Players;
  const bowlingPlayers = isFirst ? team2Players : team1Players;

  // Current batting stats
  const currentRuns = isFirst
    ? raw.calculatedTotalRuns
    : raw.calculatedTotalRuns;
  const currentWickets = raw.totalWickets;
  const overs = raw.currentOver;
  const balls = raw.ballsInOver;
  const currentOvers = `${overs}.${balls}`;

  // Target & required rate
  const target = isFirst ? null : raw.firstInningsRuns + 1;
  const totalBalls = overs * 6 + balls;
  const runRate = totalBalls > 0 ? (currentRuns / totalBalls) * 6 : 0;

  let requiredRate: number | null = null;
  if (!isFirst && target !== null && settings) {
    const totalBallsInInnings = settings.totalOvers * 6;
    const ballsRemaining = totalBallsInInnings - totalBalls;
    const runsNeeded = target - currentRuns;
    requiredRate =
      ballsRemaining > 0 ? (runsNeeded / ballsRemaining) * 6 : 0;
  }

  // Current batsmen/bowler names
  const strikerName =
    raw.strikerIndex != null && battingPlayers[raw.strikerIndex]
      ? battingPlayers[raw.strikerIndex].name
      : '—';
  const nonStrikerName =
    raw.nonStrikerIndex != null && battingPlayers[raw.nonStrikerIndex]
      ? battingPlayers[raw.nonStrikerIndex].name
      : '—';
  const bowlerName =
    raw.bowlerIndex != null && bowlingPlayers[raw.bowlerIndex]
      ? bowlingPlayers[raw.bowlerIndex].name
      : '—';

  const deliveries = safeJsonParse<DeliveryDocument[]>(
    raw.allDeliveriesJson,
    []
  );

  return {
    raw,
    battingTeam,
    bowlingTeam,
    currentRuns,
    currentWickets,
    currentOvers,
    target,
    strikerName,
    nonStrikerName,
    bowlerName,
    battingPlayers,
    bowlingPlayers,
    deliveries,
    matchSettings: settings,
    isFirstInnings: isFirst,
    firstInningsRuns: raw.firstInningsRuns,
    firstInningsWickets: raw.firstInningsWickets,
    runRate,
    requiredRate,
  };
}

/**
 * Real-time Firestore snapshot listener for a live in-progress match.
 */
export function useLiveMatch(matchId: string | undefined) {
  const [state, setState] = useState<LiveMatchState | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!matchId) {
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);

    const unsubscribe = onSnapshot(
      doc(db, 'in_progress_matches', matchId),
      (snap) => {
        if (snap.exists()) {
          const raw = snap.data() as InProgressMatchDocument;
          setState(parseLiveState(raw));
        } else {
          setError('Match not found');
          setState(null);
        }
        setLoading(false);
      },
      (err) => {
        console.error('Live match listener error:', err);
        setError(err.message);
        setLoading(false);
      }
    );

    return () => unsubscribe();
  }, [matchId]);

  return { state, loading, error };
}
