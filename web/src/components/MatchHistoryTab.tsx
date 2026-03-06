import { useNavigate } from 'react-router-dom';
import type { MatchDocument } from '../types';

function formatDate(ts: number): string {
  return new Date(ts).toLocaleDateString('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

interface Props {
  matches: MatchDocument[];
}

export default function MatchHistoryTab({ matches }: Props) {
  const navigate = useNavigate();

  if (matches.length === 0) {
    return (
      <p
        className="text-center py-10"
        style={{ color: 'var(--color-text-secondary)' }}
      >
        No matches yet.
      </p>
    );
  }

  return (
    <div className="space-y-3">
      {matches.map((m) => {
        const team1Won = m.winnerTeam === m.team1Name;
        return (
          <button
            key={m.id}
            onClick={() => navigate(`/match/${m.id}`)}
            className="card w-full text-left hover:shadow-md transition-shadow cursor-pointer"
          >
            <div className="flex items-center justify-between mb-1">
              <span
                className="text-xs"
                style={{ color: 'var(--color-text-secondary)' }}
              >
                {formatDate(m.matchDate)}
              </span>
              {m.playerOfTheMatchName && (
                <span
                  className="badge text-[10px]"
                  style={{
                    background: 'var(--color-accent)',
                    color: '#fff',
                  }}
                >
                  POTM: {m.playerOfTheMatchName}
                </span>
              )}
            </div>

            <div className="flex items-center gap-3 my-2">
              {/* Team 1 */}
              <div className="flex-1 min-w-0">
                <p
                  className={`text-sm font-semibold truncate ${
                    team1Won ? '' : 'opacity-70'
                  }`}
                >
                  {m.team1Name}
                </p>
                <p className="text-lg font-bold">
                  {m.firstInningsRuns}/{m.firstInningsWickets}
                </p>
              </div>

              <span
                className="text-xs font-bold"
                style={{ color: 'var(--color-text-secondary)' }}
              >
                vs
              </span>

              {/* Team 2 */}
              <div className="flex-1 min-w-0 text-right">
                <p
                  className={`text-sm font-semibold truncate ${
                    !team1Won ? '' : 'opacity-70'
                  }`}
                >
                  {m.team2Name}
                </p>
                <p className="text-lg font-bold">
                  {m.secondInningsRuns}/{m.secondInningsWickets}
                </p>
              </div>
            </div>

            <p className="text-xs" style={{ color: 'var(--color-success)' }}>
              {m.winnerTeam} won by {m.winningMargin}
            </p>
          </button>
        );
      })}
    </div>
  );
}
