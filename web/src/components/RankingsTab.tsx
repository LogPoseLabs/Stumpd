import { useState, useMemo } from 'react';
import type { PlayerDetailedStats } from '../types';
import {
  calculateBattingRating,
  calculateBowlingRating,
  calculateOverallRating,
} from '../utils/rankingUtils';

type RankingType = 'batting' | 'bowling' | 'overall';

interface Props {
  playerStats: Map<string, PlayerDetailedStats>;
  groupMatchContext: [string, number][];
}

export default function RankingsTab({ playerStats, groupMatchContext }: Props) {
  const [type, setType] = useState<RankingType>('batting');

  const rankings = useMemo(() => {
    const players = Array.from(playerStats.values());
    const ctx = groupMatchContext.length > 0 ? groupMatchContext : null;

    const rated = players
      .map((p) => {
        let rating = 0;
        switch (type) {
          case 'batting':
            rating = calculateBattingRating(p.matchPerformances, ctx);
            break;
          case 'bowling':
            rating = calculateBowlingRating(p.matchPerformances, ctx);
            break;
          case 'overall':
            rating = calculateOverallRating(p, ctx);
            break;
        }
        return { player: p, rating };
      })
      .filter((r) => r.rating > 0)
      .sort((a, b) => b.rating - a.rating);

    return rated;
  }, [playerStats, groupMatchContext, type]);

  const types: { key: RankingType; label: string }[] = [
    { key: 'batting', label: 'Batting' },
    { key: 'bowling', label: 'Bowling' },
    { key: 'overall', label: 'All-Rounder' },
  ];

  return (
    <div>
      {/* Sub-tabs */}
      <div className="flex gap-2 mb-5">
        {types.map((t) => (
          <button
            key={t.key}
            onClick={() => setType(t.key)}
            className="px-4 py-1.5 rounded-full text-sm font-medium transition-colors"
            style={{
              background:
                type === t.key ? 'var(--color-primary)' : 'var(--color-surface)',
              color: type === t.key ? '#fff' : 'var(--color-text-secondary)',
              border: `1px solid ${
                type === t.key ? 'var(--color-primary)' : 'var(--color-border)'
              }`,
            }}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* Rankings list */}
      {rankings.length === 0 ? (
        <p
          className="text-center py-10"
          style={{ color: 'var(--color-text-secondary)' }}
        >
          No ranked players yet.
        </p>
      ) : (
        <div className="space-y-2">
          {rankings.map((r, i) => (
            <div
              key={r.player.playerId}
              className="card flex items-center gap-4"
            >
              {/* Rank */}
              <div
                className="w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold flex-shrink-0"
                style={{
                  background:
                    i === 0
                      ? '#fbbf24'
                      : i === 1
                      ? '#9ca3af'
                      : i === 2
                      ? '#d97706'
                      : 'var(--color-bg)',
                  color:
                    i < 3 ? '#fff' : 'var(--color-text-secondary)',
                }}
              >
                {i + 1}
              </div>

              {/* Player info */}
              <div className="flex-1 min-w-0">
                <p className="font-semibold truncate">
                  {r.player.playerName}
                </p>
                <p
                  className="text-xs"
                  style={{ color: 'var(--color-text-secondary)' }}
                >
                  {r.player.matchesPlayed} match
                  {r.player.matchesPlayed !== 1 ? 'es' : ''}
                  {type === 'batting' &&
                    ` · ${r.player.totalRuns} runs`}
                  {type === 'bowling' &&
                    ` · ${r.player.totalWickets} wkts`}
                  {type === 'overall' &&
                    ` · ${r.player.totalRuns}r / ${r.player.totalWickets}w`}
                </p>
              </div>

              {/* Rating */}
              <div className="text-right flex-shrink-0">
                <p
                  className="text-lg font-bold"
                  style={{ color: 'var(--color-primary)' }}
                >
                  {Math.round(r.rating)}
                </p>
                <p
                  className="text-[10px] uppercase tracking-wider"
                  style={{ color: 'var(--color-text-secondary)' }}
                >
                  Rating
                </p>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
