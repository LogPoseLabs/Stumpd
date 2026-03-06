import { useNavigate } from 'react-router-dom';
import type { InProgressMatchDocument, LivePlayerState, MatchSettings } from '../types';

function safeJsonParse<T>(json: string | undefined | null, fallback: T): T {
  if (!json) return fallback;
  try { return JSON.parse(json); } catch { return fallback; }
}

interface Props {
  matches: InProgressMatchDocument[];
  loading: boolean;
}

export default function LiveMatchesTab({ matches, loading }: Props) {
  const navigate = useNavigate();

  if (loading) {
    return (
      <div className="flex items-center justify-center py-16">
        <div className="w-6 h-6 border-2 border-t-transparent rounded-full animate-spin" style={{ borderColor: 'var(--color-primary)', borderTopColor: 'transparent' }} />
      </div>
    );
  }

  if (matches.length === 0) {
    return (
      <div className="text-center py-16">
        <div className="text-4xl mb-3 opacity-40">🏏</div>
        <p className="font-medium mb-1">No Live Matches</p>
        <p className="text-sm" style={{ color: 'var(--color-text-secondary)' }}>
          When a match is in progress, it will appear here in real-time.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {matches.map((m) => (
        <LiveMatchCard key={m.matchId} match={m} onClick={() => navigate(`/live/${m.matchId}`)} />
      ))}
    </div>
  );
}

function LiveMatchCard({ match: m, onClick }: { match: InProgressMatchDocument; onClick: () => void }) {
  const isFirst = m.currentInnings === 1;
  const battingTeam = isFirst ? m.team1Name : m.team2Name;
  const settings = safeJsonParse<MatchSettings | null>(m.matchSettingsJson, null);

  const battingPlayers = safeJsonParse<LivePlayerState[]>(
    isFirst ? m.team1PlayersJson : m.team2PlayersJson,
    []
  );

  const overs = `${m.currentOver}.${m.ballsInOver}`;
  const totalBalls = m.currentOver * 6 + m.ballsInOver;
  const runRate = totalBalls > 0 ? ((m.calculatedTotalRuns / totalBalls) * 6).toFixed(2) : '0.00';

  const target = isFirst ? null : m.firstInningsRuns + 1;
  let requiredRate: string | null = null;
  if (!isFirst && target !== null && settings) {
    const ballsRemaining = settings.totalOvers * 6 - totalBalls;
    const runsNeeded = target - m.calculatedTotalRuns;
    requiredRate = ballsRemaining > 0 ? ((runsNeeded / ballsRemaining) * 6).toFixed(2) : '0.00';
  }

  // Striker info
  const strikerName =
    m.strikerIndex != null && battingPlayers[m.strikerIndex]
      ? battingPlayers[m.strikerIndex].name
      : null;
  const strikerRuns =
    m.strikerIndex != null && battingPlayers[m.strikerIndex]
      ? battingPlayers[m.strikerIndex].runs
      : 0;
  const strikerBalls =
    m.strikerIndex != null && battingPlayers[m.strikerIndex]
      ? battingPlayers[m.strikerIndex].ballsFaced
      : 0;

  return (
    <button
      onClick={onClick}
      className="card w-full text-left hover:shadow-md transition-shadow cursor-pointer relative overflow-hidden"
    >
      {/* Live pulse indicator */}
      <div className="flex items-center gap-2 mb-3">
        <span className="live-indicator w-2 h-2 rounded-full bg-red-500" />
        <span className="text-xs font-bold text-red-500 uppercase tracking-wider">Live</span>
        {settings && (
          <span className="text-xs ml-auto" style={{ color: 'var(--color-text-secondary)' }}>
            {settings.totalOvers} overs
          </span>
        )}
      </div>

      {/* 1st innings score (if 2nd innings) */}
      {!isFirst && (
        <p className="text-xs mb-1" style={{ color: 'var(--color-text-secondary)' }}>
          {m.team1Name}: {m.firstInningsRuns}/{m.firstInningsWickets} ({m.firstInningsOvers}.{m.firstInningsBalls})
        </p>
      )}

      {/* Main score */}
      <div className="flex items-end justify-between mb-2">
        <div>
          <p className="text-xs font-medium" style={{ color: 'var(--color-text-secondary)' }}>
            {battingTeam}
          </p>
          <p className="text-3xl font-extrabold tracking-tight">
            {m.calculatedTotalRuns}
            <span className="text-xl font-bold" style={{ color: 'var(--color-text-secondary)' }}>
              /{m.totalWickets}
            </span>
          </p>
        </div>
        <div className="text-right">
          <p className="text-xl font-bold">{overs}</p>
          <p className="text-[10px]" style={{ color: 'var(--color-text-secondary)' }}>overs</p>
        </div>
      </div>

      {/* Rates and target */}
      <div className="flex flex-wrap gap-3 text-xs mb-2" style={{ color: 'var(--color-text-secondary)' }}>
        <span>CRR: <span className="font-semibold" style={{ color: 'var(--color-text)' }}>{runRate}</span></span>
        {target && (
          <span>Target: <span className="font-semibold" style={{ color: 'var(--color-text)' }}>{target}</span></span>
        )}
        {requiredRate && (
          <span>RRR: <span className="font-semibold" style={{ color: 'var(--color-danger, #ef4444)' }}>{requiredRate}</span></span>
        )}
      </div>

      {/* Striker */}
      {strikerName && (
        <p className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>
          <span className="font-medium" style={{ color: 'var(--color-text)' }}>{strikerName}*</span>{' '}
          {strikerRuns} ({strikerBalls})
        </p>
      )}
    </button>
  );
}
