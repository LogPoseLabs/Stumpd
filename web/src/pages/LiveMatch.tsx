import { useEffect, useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { useLiveMatch } from '../hooks/useLiveMatch';
import { resolveShareCode } from '../hooks/useFirestore';
import Loader from '../components/Loader';

function ballClass(outcome: string): string {
  const o = outcome.toUpperCase();
  if (o === 'W' || o.includes('WICKET')) return 'ball ball-wicket';
  if (o === '4' || o.includes('FOUR')) return 'ball ball-four';
  if (o === '6' || o.includes('SIX')) return 'ball ball-six';
  if (o === '0' || o === 'DOT') return 'ball ball-dot';
  if (o.includes('WIDE') || o === 'WD') return 'ball ball-wide';
  if (o.includes('NO BALL') || o === 'NB') return 'ball ball-noball';
  return 'ball ball-run';
}

export default function LiveMatch() {
  const { matchId: paramMatchId } = useParams<{ matchId: string }>();
  const [searchParams] = useSearchParams();
  const codeParam = searchParams.get('code');
  const [resolvedId, setResolvedId] = useState<string | undefined>(paramMatchId);
  const [resolving, setResolving] = useState(false);
  const [resolveError, setResolveError] = useState<string | null>(null);

  // Resolve share code to match ID
  useEffect(() => {
    if (paramMatchId) { setResolvedId(paramMatchId); return; }
    if (!codeParam) return;
    setResolving(true);
    resolveShareCode(codeParam).then((shared) => {
      if (shared && shared.isActive) {
        setResolvedId(shared.matchId);
      } else {
        setResolveError('Share code expired or not found.');
      }
      setResolving(false);
    });
  }, [paramMatchId, codeParam]);

  const { state, loading, error } = useLiveMatch(resolvedId);

  if (resolving || loading) return <Loader text="Connecting to live match..." />;
  if (resolveError || error) {
    return (
      <div className="text-center py-20">
        <h2 className="text-2xl font-bold mb-2">Match Not Available</h2>
        <p style={{ color: 'var(--color-text-secondary)' }}>
          {resolveError || error}
        </p>
      </div>
    );
  }
  if (!state) {
    return (
      <div className="text-center py-20">
        <h2 className="text-2xl font-bold mb-2">No Live Match</h2>
        <p style={{ color: 'var(--color-text-secondary)' }}>
          This match is not currently in progress.
        </p>
      </div>
    );
  }

  const {
    battingTeam,
    currentRuns,
    currentWickets,
    currentOvers,
    target,
    strikerName,
    nonStrikerName,
    bowlerName,
    battingPlayers,
    deliveries,
    isFirstInnings,
    firstInningsRuns,
    firstInningsWickets,
    runRate,
    requiredRate,
    raw,
  } = state;

  // Group deliveries by over (current innings only)
  const currentInningsDeliveries = deliveries.filter(
    (d) => d.inning === (isFirstInnings ? 1 : 2)
  );
  const lastOverDeliveries = currentInningsDeliveries.slice(-6);

  return (
    <div>
      {/* Live badge */}
      <div className="flex items-center gap-2 mb-4">
        <span className="live-indicator w-2.5 h-2.5 rounded-full bg-red-500" />
        <span className="text-sm font-semibold text-red-500">LIVE</span>
        {raw?.groupName && (
          <span className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>
            {raw.groupName}
          </span>
        )}
      </div>

      {/* Score card */}
      <div className="card mb-4">
        {/* If 2nd innings, show 1st innings score */}
        {!isFirstInnings && (
          <p className="text-xs mb-2" style={{ color: 'var(--color-text-secondary)' }}>
            {raw?.team1Name}: {firstInningsRuns}/{firstInningsWickets}
          </p>
        )}

        <div className="flex items-end justify-between mb-2">
          <div>
            <p className="text-sm font-medium" style={{ color: 'var(--color-text-secondary)' }}>
              {battingTeam} batting
            </p>
            <p className="text-4xl font-extrabold tracking-tight">
              {currentRuns}
              <span className="text-2xl font-bold" style={{ color: 'var(--color-text-secondary)' }}>
                /{currentWickets}
              </span>
            </p>
          </div>
          <div className="text-right">
            <p className="text-2xl font-bold">{currentOvers}</p>
            <p className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>overs</p>
          </div>
        </div>

        {/* Run rate & target */}
        <div className="flex gap-4 text-sm">
          <span style={{ color: 'var(--color-text-secondary)' }}>
            CRR: <span className="font-semibold" style={{ color: 'var(--color-text)' }}>{runRate.toFixed(2)}</span>
          </span>
          {target && (
            <span style={{ color: 'var(--color-text-secondary)' }}>
              Target: <span className="font-semibold" style={{ color: 'var(--color-text)' }}>{target}</span>
            </span>
          )}
          {requiredRate !== null && requiredRate > 0 && (
            <span style={{ color: 'var(--color-text-secondary)' }}>
              RRR: <span className="font-semibold" style={{ color: 'var(--color-danger)' }}>{requiredRate.toFixed(2)}</span>
            </span>
          )}
        </div>
      </div>

      {/* Current batsmen & bowler */}
      <div className="grid grid-cols-2 gap-3 mb-4">
        <div className="card">
          <p className="text-xs mb-1" style={{ color: 'var(--color-text-secondary)' }}>Batting</p>
          <p className="font-semibold text-sm">
            {strikerName} <span style={{ color: 'var(--color-primary)' }}>*</span>
          </p>
          <p className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>
            {nonStrikerName}
          </p>
        </div>
        <div className="card">
          <p className="text-xs mb-1" style={{ color: 'var(--color-text-secondary)' }}>Bowling</p>
          <p className="font-semibold text-sm">{bowlerName}</p>
        </div>
      </div>

      {/* Last over */}
      {lastOverDeliveries.length > 0 && (
        <div className="card mb-4">
          <p className="text-xs font-semibold mb-2" style={{ color: 'var(--color-text-secondary)' }}>
            This Over
          </p>
          <div className="flex flex-wrap gap-2">
            {lastOverDeliveries.map((d, i) => (
              <span key={i} className={ballClass(d.outcome)}>
                {d.outcome}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* Batting summary */}
      <div className="card">
        <p className="text-xs font-semibold mb-2" style={{ color: 'var(--color-text-secondary)' }}>
          Batting Summary
        </p>
        <div className="space-y-1.5">
          {battingPlayers
            .filter((p) => p.ballsFaced > 0 || p.runs > 0)
            .map((p) => (
              <div key={p.id} className="flex items-center text-sm">
                <span className={`flex-1 truncate ${p.isOut ? 'line-through opacity-50' : 'font-medium'}`}>
                  {p.name}
                </span>
                <span className="w-10 text-right font-semibold">{p.runs}</span>
                <span className="w-10 text-right text-xs" style={{ color: 'var(--color-text-secondary)' }}>
                  ({p.ballsFaced})
                </span>
              </div>
            ))}
        </div>
      </div>
    </div>
  );
}
