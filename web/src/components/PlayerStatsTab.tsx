import { useState, useMemo, useRef, useEffect } from 'react';
import type { PlayerDetailedStats } from '../types';
import {
  battingAverage,
  battingStrikeRate,
  bowlingAverage,
  bowlingEconomy,
  highestScore,
  bestBowling,
  totalFours,
  totalSixes,
  totalMaidens,
} from '../utils/statsCalculator';

// ============================== Derived stat helpers ==============================

function ducks(p: PlayerDetailedStats): number {
  return p.matchPerformances.filter((m) => m.runs === 0 && m.isOut).length;
}

function goldenDucks(p: PlayerDetailedStats): number {
  return p.matchPerformances.filter(
    (m) => m.runs === 0 && m.ballsFaced <= 1 && m.isOut
  ).length;
}

function boundaryPercentage(p: PlayerDetailedStats): number {
  if (p.totalRuns === 0) return 0;
  const boundaryRuns = totalFours(p) * 4 + totalSixes(p) * 6;
  return (boundaryRuns / p.totalRuns) * 100;
}

function consistency(p: PlayerDetailedStats): number {
  const perfs = p.matchPerformances.filter((m) => m.ballsFaced > 0 || m.runs > 0);
  if (perfs.length < 2) return 0;
  const mean = perfs.reduce((s, m) => s + m.runs, 0) / perfs.length;
  const variance =
    perfs.reduce((s, m) => s + (m.runs - mean) ** 2, 0) / perfs.length;
  return Math.sqrt(variance);
}

function bowlingStrikeRate(p: PlayerDetailedStats): number {
  if (p.totalWickets === 0) return 0;
  return p.totalBallsBowled / p.totalWickets;
}

function totalRunsConceded(p: PlayerDetailedStats): number {
  return p.matchPerformances.reduce((s, m) => s + m.runsConceded, 0);
}

// ============================== Stat definitions ==============================

interface StatDef {
  key: string;
  label: string;
  shortLabel: string;
  category: 'batting' | 'bowling' | 'fielding';
  columns: ColumnDef[];
  sort: (a: PlayerDetailedStats, b: PlayerDetailedStats) => number;
  filter?: (p: PlayerDetailedStats) => boolean;
}

interface ColumnDef {
  header: string;
  value: (p: PlayerDetailedStats) => string;
  bold?: boolean;
}

const STAT_DEFS: StatDef[] = [
  // ── Batting ──
  {
    key: 'runs',
    label: 'Runs',
    shortLabel: 'Runs',
    category: 'batting',
    sort: (a, b) => b.totalRuns - a.totalRuns,
    columns: [
      { header: 'M', value: (p) => String(p.matchesPlayed) },
      { header: 'Runs', value: (p) => String(p.totalRuns), bold: true },
      { header: 'Avg', value: (p) => battingAverage(p).toFixed(1) },
      { header: 'SR', value: (p) => battingStrikeRate(p).toFixed(1) },
      { header: 'HS', value: (p) => { const h = highestScore(p); return `${h.runs}${h.isNotOut ? '*' : ''}`; } },
      { header: '4s', value: (p) => String(totalFours(p)) },
      { header: '6s', value: (p) => String(totalSixes(p)) },
    ],
  },
  {
    key: 'avg',
    label: 'Batting Avg',
    shortLabel: 'Bat Avg',
    category: 'batting',
    sort: (a, b) => battingAverage(b) - battingAverage(a),
    filter: (p) => p.matchPerformances.some((m) => m.isOut),
    columns: [
      { header: 'M', value: (p) => String(p.matchesPlayed) },
      { header: 'Avg', value: (p) => battingAverage(p).toFixed(1), bold: true },
      { header: 'Runs', value: (p) => String(p.totalRuns) },
      { header: 'Out', value: (p) => String(p.matchPerformances.filter((m) => m.isOut).length) },
      { header: 'HS', value: (p) => { const h = highestScore(p); return `${h.runs}${h.isNotOut ? '*' : ''}`; } },
    ],
  },
  {
    key: 'sr',
    label: 'Strike Rate',
    shortLabel: 'SR',
    category: 'batting',
    sort: (a, b) => battingStrikeRate(b) - battingStrikeRate(a),
    filter: (p) => p.totalBallsFaced > 0,
    columns: [
      { header: 'M', value: (p) => String(p.matchesPlayed) },
      { header: 'SR', value: (p) => battingStrikeRate(p).toFixed(1), bold: true },
      { header: 'Runs', value: (p) => String(p.totalRuns) },
      { header: 'BF', value: (p) => String(p.totalBallsFaced) },
      { header: '4s', value: (p) => String(totalFours(p)) },
      { header: '6s', value: (p) => String(totalSixes(p)) },
    ],
  },
  {
    key: 'boundaries',
    label: 'Boundaries',
    shortLabel: 'Bdry',
    category: 'batting',
    sort: (a, b) => (totalFours(b) + totalSixes(b)) - (totalFours(a) + totalSixes(a)),
    columns: [
      { header: 'M', value: (p) => String(p.matchesPlayed) },
      { header: 'Bdry', value: (p) => String(totalFours(p) + totalSixes(p)), bold: true },
      { header: '4s', value: (p) => String(totalFours(p)) },
      { header: '6s', value: (p) => String(totalSixes(p)) },
      { header: 'Bdry%', value: (p) => `${boundaryPercentage(p).toFixed(0)}%` },
      { header: 'Runs', value: (p) => String(p.totalRuns) },
    ],
  },
  {
    key: 'ducks',
    label: 'Ducks',
    shortLabel: 'Ducks',
    category: 'batting',
    sort: (a, b) => ducks(b) - ducks(a),
    filter: (p) => ducks(p) > 0,
    columns: [
      { header: 'M', value: (p) => String(p.matchesPlayed) },
      { header: 'Ducks', value: (p) => String(ducks(p)), bold: true },
      { header: 'Golden', value: (p) => String(goldenDucks(p)) },
      { header: 'Avg', value: (p) => battingAverage(p).toFixed(1) },
      { header: 'Runs', value: (p) => String(p.totalRuns) },
    ],
  },
  {
    key: 'highestScores',
    label: 'Highest Scores',
    shortLabel: 'HS',
    category: 'batting',
    sort: (a, b) => {
      const ha = highestScore(a);
      const hb = highestScore(b);
      if (hb.runs !== ha.runs) return hb.runs - ha.runs;
      // prefer not-out when runs are equal
      return (hb.isNotOut ? 1 : 0) - (ha.isNotOut ? 1 : 0);
    },
    filter: (p) => p.totalBallsFaced > 0 || p.totalRuns > 0,
    columns: [
      { header: 'M', value: (p) => String(p.matchesPlayed) },
      { header: 'HS', value: (p) => { const h = highestScore(p); return `${h.runs}${h.isNotOut ? '*' : ''}`; }, bold: true },
      { header: 'Runs', value: (p) => String(p.totalRuns) },
      { header: 'Avg', value: (p) => battingAverage(p).toFixed(1) },
      { header: 'SR', value: (p) => battingStrikeRate(p).toFixed(1) },
      { header: '50s', value: (p) => String(p.matchPerformances.filter((m) => m.runs >= 50).length) },
      { header: '30s', value: (p) => String(p.matchPerformances.filter((m) => m.runs >= 30 && m.runs < 50).length) },
    ],
  },
  {
    key: 'ballsFaced',
    label: 'Balls Faced',
    shortLabel: 'BF',
    category: 'batting',
    sort: (a, b) => b.totalBallsFaced - a.totalBallsFaced,
    filter: (p) => p.totalBallsFaced > 0,
    columns: [
      { header: 'M', value: (p) => String(p.matchesPlayed) },
      { header: 'BF', value: (p) => String(p.totalBallsFaced), bold: true },
      { header: 'Runs', value: (p) => String(p.totalRuns) },
      { header: 'SR', value: (p) => battingStrikeRate(p).toFixed(1) },
      { header: 'Avg', value: (p) => battingAverage(p).toFixed(1) },
      { header: '4s', value: (p) => String(totalFours(p)) },
      { header: '6s', value: (p) => String(totalSixes(p)) },
    ],
  },
  {
    key: 'consistency',
    label: 'Consistency',
    shortLabel: 'Consist.',
    category: 'batting',
    sort: (a, b) => consistency(a) - consistency(b), // lower = more consistent
    filter: (p) => p.matchPerformances.filter((m) => m.ballsFaced > 0 || m.runs > 0).length >= 2,
    columns: [
      { header: 'M', value: (p) => String(p.matchesPlayed) },
      { header: 'Std Dev', value: (p) => consistency(p).toFixed(1), bold: true },
      { header: 'Avg', value: (p) => battingAverage(p).toFixed(1) },
      { header: 'Runs', value: (p) => String(p.totalRuns) },
      { header: 'HS', value: (p) => { const h = highestScore(p); return `${h.runs}${h.isNotOut ? '*' : ''}`; } },
    ],
  },
  // ── Bowling ──
  {
    key: 'wickets',
    label: 'Wickets',
    shortLabel: 'Wkts',
    category: 'bowling',
    sort: (a, b) => b.totalWickets - a.totalWickets,
    filter: (p) => p.totalBallsBowled > 0,
    columns: [
      { header: 'M', value: (p) => String(p.matchesPlayed) },
      { header: 'Wkts', value: (p) => String(p.totalWickets), bold: true },
      { header: 'Avg', value: (p) => bowlingAverage(p) > 0 ? bowlingAverage(p).toFixed(1) : '-' },
      { header: 'Econ', value: (p) => bowlingEconomy(p).toFixed(1) },
      { header: 'Best', value: (p) => { const b = bestBowling(p); return `${b.wickets}/${b.runs}`; } },
      { header: 'Mdn', value: (p) => String(totalMaidens(p)) },
    ],
  },
  {
    key: 'bowlAvg',
    label: 'Bowling Avg',
    shortLabel: 'Bowl Avg',
    category: 'bowling',
    sort: (a, b) => (bowlingAverage(a) || 999) - (bowlingAverage(b) || 999),
    filter: (p) => p.totalWickets > 0,
    columns: [
      { header: 'M', value: (p) => String(p.matchesPlayed) },
      { header: 'Avg', value: (p) => bowlingAverage(p).toFixed(1), bold: true },
      { header: 'Wkts', value: (p) => String(p.totalWickets) },
      { header: 'Runs', value: (p) => String(totalRunsConceded(p)) },
      { header: 'Econ', value: (p) => bowlingEconomy(p).toFixed(1) },
    ],
  },
  {
    key: 'economy',
    label: 'Economy',
    shortLabel: 'Econ',
    category: 'bowling',
    sort: (a, b) => (bowlingEconomy(a) || 999) - (bowlingEconomy(b) || 999),
    filter: (p) => p.totalBallsBowled > 0,
    columns: [
      { header: 'M', value: (p) => String(p.matchesPlayed) },
      { header: 'Econ', value: (p) => bowlingEconomy(p).toFixed(1), bold: true },
      { header: 'Wkts', value: (p) => String(p.totalWickets) },
      { header: 'Runs', value: (p) => String(totalRunsConceded(p)) },
      { header: 'Mdn', value: (p) => String(totalMaidens(p)) },
    ],
  },
  {
    key: 'bowlSR',
    label: 'Bowling SR',
    shortLabel: 'Bowl SR',
    category: 'bowling',
    sort: (a, b) => (bowlingStrikeRate(a) || 999) - (bowlingStrikeRate(b) || 999),
    filter: (p) => p.totalWickets > 0,
    columns: [
      { header: 'M', value: (p) => String(p.matchesPlayed) },
      { header: 'SR', value: (p) => bowlingStrikeRate(p).toFixed(1), bold: true },
      { header: 'Wkts', value: (p) => String(p.totalWickets) },
      { header: 'Balls', value: (p) => String(p.totalBallsBowled) },
      { header: 'Econ', value: (p) => bowlingEconomy(p).toFixed(1) },
    ],
  },
  {
    key: 'maidens',
    label: 'Maidens',
    shortLabel: 'Maidens',
    category: 'bowling',
    sort: (a, b) => totalMaidens(b) - totalMaidens(a),
    filter: (p) => totalMaidens(p) > 0,
    columns: [
      { header: 'M', value: (p) => String(p.matchesPlayed) },
      { header: 'Mdn', value: (p) => String(totalMaidens(p)), bold: true },
      { header: 'Wkts', value: (p) => String(p.totalWickets) },
      { header: 'Econ', value: (p) => bowlingEconomy(p).toFixed(1) },
      { header: 'Best', value: (p) => { const b = bestBowling(p); return `${b.wickets}/${b.runs}`; } },
    ],
  },
  // ── Fielding ──
  {
    key: 'catches',
    label: 'Catches',
    shortLabel: 'Catches',
    category: 'fielding',
    sort: (a, b) => b.totalCatches - a.totalCatches,
    filter: (p) => p.totalCatches > 0,
    columns: [
      { header: 'M', value: (p) => String(p.matchesPlayed) },
      { header: 'Ct', value: (p) => String(p.totalCatches), bold: true },
      { header: 'RO', value: (p) => String(p.totalRunOuts) },
      { header: 'St', value: (p) => String(p.totalStumpings) },
      { header: 'Total', value: (p) => String(p.totalCatches + p.totalRunOuts + p.totalStumpings) },
    ],
  },
  {
    key: 'runouts',
    label: 'Run Outs',
    shortLabel: 'Run Outs',
    category: 'fielding',
    sort: (a, b) => b.totalRunOuts - a.totalRunOuts,
    filter: (p) => p.totalRunOuts > 0,
    columns: [
      { header: 'M', value: (p) => String(p.matchesPlayed) },
      { header: 'RO', value: (p) => String(p.totalRunOuts), bold: true },
      { header: 'Ct', value: (p) => String(p.totalCatches) },
      { header: 'St', value: (p) => String(p.totalStumpings) },
    ],
  },
];

// ============================== Component ==============================

interface Props {
  playerStats: Map<string, PlayerDetailedStats>;
}

export default function PlayerStatsTab({ playerStats }: Props) {
  const [selectedStat, setSelectedStat] = useState('runs');
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const statDef = STAT_DEFS.find((s) => s.key === selectedStat) ?? STAT_DEFS[0];

  // Close dropdown on outside click
  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setDropdownOpen(false);
      }
    }
    if (dropdownOpen) document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, [dropdownOpen]);

  const sorted = useMemo(() => {
    let players = Array.from(playerStats.values());
    if (statDef.filter) players = players.filter(statDef.filter);
    return [...players].sort(statDef.sort);
  }, [playerStats, statDef]);

  // Group stat defs by category for the dropdown
  const categories = [
    { key: 'batting', label: 'Batting' },
    { key: 'bowling', label: 'Bowling' },
    { key: 'fielding', label: 'Fielding' },
  ] as const;

  return (
    <div>
      {/* Stat selector dropdown */}
      <div className="relative mb-4" ref={dropdownRef}>
        <button
          onClick={() => setDropdownOpen(!dropdownOpen)}
          className="flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium transition-colors"
          style={{
            background: 'var(--color-surface)',
            border: '1px solid var(--color-border)',
            color: 'var(--color-text)',
          }}
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <line x1="4" y1="21" x2="4" y2="14" /><line x1="4" y1="10" x2="4" y2="3" />
            <line x1="12" y1="21" x2="12" y2="12" /><line x1="12" y1="8" x2="12" y2="3" />
            <line x1="20" y1="21" x2="20" y2="16" /><line x1="20" y1="12" x2="20" y2="3" />
            <line x1="1" y1="14" x2="7" y2="14" /><line x1="9" y1="8" x2="15" y2="8" />
            <line x1="17" y1="16" x2="23" y2="16" />
          </svg>
          Sort by: {statDef.label}
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="6 9 12 15 18 9" />
          </svg>
        </button>

        {dropdownOpen && (
          <div
            className="absolute left-0 top-full mt-1 z-50 min-w-[220px] max-h-[400px] overflow-y-auto rounded-lg shadow-lg py-1"
            style={{
              background: 'var(--color-surface)',
              border: '1px solid var(--color-border)',
            }}
          >
            {categories.map((cat) => {
              const items = STAT_DEFS.filter((s) => s.category === cat.key);
              return (
                <div key={cat.key}>
                  <div
                    className="px-4 py-1.5 text-[10px] font-bold uppercase tracking-wider"
                    style={{ color: 'var(--color-text-secondary)' }}
                  >
                    {cat.label}
                  </div>
                  {items.map((s) => (
                    <button
                      key={s.key}
                      onClick={() => {
                        setSelectedStat(s.key);
                        setDropdownOpen(false);
                      }}
                      className="w-full text-left px-4 py-2 text-sm transition-colors hover:opacity-80"
                      style={{
                        background:
                          selectedStat === s.key
                            ? 'var(--color-primary)'
                            : 'transparent',
                        color:
                          selectedStat === s.key
                            ? '#fff'
                            : 'var(--color-text)',
                      }}
                    >
                      {s.label}
                    </button>
                  ))}
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Stats table */}
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr
              className="text-left text-xs border-b"
              style={{
                color: 'var(--color-text-secondary)',
                borderColor: 'var(--color-border)',
              }}
            >
              <th className="py-2 pr-3">Player</th>
              {statDef.columns.map((col) => (
                <th key={col.header} className="py-2 px-2 text-right">
                  {col.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {sorted.map((p) => (
              <tr
                key={p.playerName}
                className="border-b"
                style={{ borderColor: 'var(--color-border)' }}
              >
                <td className="py-2.5 pr-3 font-medium truncate max-w-[140px]">
                  {p.playerName}
                </td>
                {statDef.columns.map((col) => (
                  <td
                    key={col.header}
                    className={`py-2.5 px-2 text-right ${
                      col.bold ? 'font-semibold' : ''
                    }`}
                  >
                    {col.value(p)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
        {sorted.length === 0 && (
          <p
            className="text-center py-10"
            style={{ color: 'var(--color-text-secondary)' }}
          >
            No data for this stat.
          </p>
        )}
      </div>
    </div>
  );
}
