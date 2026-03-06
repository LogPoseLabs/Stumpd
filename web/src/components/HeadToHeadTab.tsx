import { useState, useMemo, useCallback, useRef, useEffect } from 'react';
import { collection, getDocs } from 'firebase/firestore';
import { db } from '../firebase';
import type { DeliveryDocument, PlayerStatDocument } from '../types';

// ============================== H2H Types ==============================

interface HeadToHeadStats {
  batsmanName: string;
  bowlerName: string;
  ballsFaced: number;
  runsScored: number;
  dotBalls: number;
  singles: number;
  twos: number;
  threes: number;
  fours: number;
  sixes: number;
  dismissals: number;
  matchCount: number;
}

interface MatchH2HDetail {
  matchId: string;
  matchDate: number;
  team1Name: string;
  team2Name: string;
  runsScored: number;
  ballsFaced: number;
  fours: number;
  sixes: number;
  wasOut: boolean;
}

// ============================== H2H Calculation ==============================

function calculateHeadToHead(
  allDeliveries: Map<string, DeliveryDocument[]>,
  matchMeta: Map<string, { date: number; team1: string; team2: string }>,
  batsmanName: string,
  bowlerName: string
): { stats: HeadToHeadStats | null; details: MatchH2HDetail[] } {
  let totalRuns = 0;
  let totalBalls = 0;
  let dots = 0;
  let singles = 0;
  let twos = 0;
  let threes = 0;
  let fours = 0;
  let sixes = 0;
  let dismissals = 0;
  const matchesWithData = new Set<string>();
  const details: MatchH2HDetail[] = [];

  for (const [matchId, deliveries] of allDeliveries.entries()) {
    let matchRuns = 0;
    let matchBalls = 0;
    let matchFours = 0;
    let matchSixes = 0;
    let wasOut = false;
    let hasData = false;

    for (const d of deliveries) {
      if (
        d.strikerName.toLowerCase() === batsmanName.toLowerCase() &&
        d.bowlerName.toLowerCase() === bowlerName.toLowerCase()
      ) {
        hasData = true;
        matchesWithData.add(matchId);

        const o = d.outcome.toUpperCase().trim();

        if (o === '0' || o === 'DOT') {
          dots++;
          matchBalls++;
        } else if (o === '1') {
          singles++;
          totalRuns += 1;
          matchRuns += 1;
          matchBalls++;
        } else if (o === '2') {
          twos++;
          totalRuns += 2;
          matchRuns += 2;
          matchBalls++;
        } else if (o === '3') {
          threes++;
          totalRuns += 3;
          matchRuns += 3;
          matchBalls++;
        } else if (o === '4') {
          fours++;
          matchFours++;
          totalRuns += 4;
          matchRuns += 4;
          matchBalls++;
        } else if (o === '6') {
          sixes++;
          matchSixes++;
          totalRuns += 6;
          matchRuns += 6;
          matchBalls++;
        } else if (o.startsWith('W') || o === 'OUT' || o.includes('RO') || o.includes('RUN OUT') || o.includes('CAUGHT') || o.includes('STUMPED') || o.includes('BOWLED') || o.includes('BOUNDARY_OUT')) {
          dismissals++;
          wasOut = true;
          matchBalls++;
        } else if (o.startsWith('WD') || o.includes('WIDE')) {
          // Wides don't count as balls faced
          totalRuns += d.runs;
          matchRuns += d.runs;
        } else if (o.startsWith('NB') || o.includes('NOBALL') || o.includes('NO BALL')) {
          totalRuns += d.runs;
          matchRuns += d.runs;
        } else {
          totalRuns += d.runs;
          matchRuns += d.runs;
          matchBalls++;
        }
      }
    }

    totalBalls += matchBalls;
    const meta = matchMeta.get(matchId);

    if (hasData && meta) {
      details.push({
        matchId,
        matchDate: meta.date,
        team1Name: meta.team1,
        team2Name: meta.team2,
        runsScored: matchRuns,
        ballsFaced: matchBalls,
        fours: matchFours,
        sixes: matchSixes,
        wasOut,
      });
    }
  }

  if (matchesWithData.size === 0) return { stats: null, details: [] };

  return {
    stats: {
      batsmanName,
      bowlerName,
      ballsFaced: totalBalls,
      runsScored: totalRuns,
      dotBalls: dots,
      singles,
      twos,
      threes,
      fours,
      sixes,
      dismissals,
      matchCount: matchesWithData.size,
    },
    details: details.sort((a, b) => b.matchDate - a.matchDate),
  };
}

// ============================== Component ==============================

interface Props {
  matchIds: string[];
  matchMeta: Map<string, { date: number; team1: string; team2: string }>;
  allStats: Map<string, PlayerStatDocument[]>;
}

export default function HeadToHeadTab({ matchIds, matchMeta, allStats }: Props) {
  const [batsman, setBatsman] = useState('');
  const [bowler, setBowler] = useState('');
  const [batsmanSearch, setBatsmanSearch] = useState('');
  const [bowlerSearch, setBowlerSearch] = useState('');
  const [showBatsmanDrop, setShowBatsmanDrop] = useState(false);
  const [showBowlerDrop, setShowBowlerDrop] = useState(false);
  const [loading, setLoading] = useState(false);
  const [deliveryCache] = useState(() => new Map<string, DeliveryDocument[]>());
  const [allDeliveries, setAllDeliveries] = useState<Map<string, DeliveryDocument[]>>(new Map());

  const batsmanRef = useRef<HTMLDivElement>(null);
  const bowlerRef = useRef<HTMLDivElement>(null);

  // Close dropdowns on outside click
  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (batsmanRef.current && !batsmanRef.current.contains(e.target as Node)) setShowBatsmanDrop(false);
      if (bowlerRef.current && !bowlerRef.current.contains(e.target as Node)) setShowBowlerDrop(false);
    }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  // Build player list from stats
  const players = useMemo(() => {
    const names = new Set<string>();
    for (const stats of allStats.values()) {
      for (const s of stats) {
        if (s.name) names.add(s.name);
      }
    }
    return [...names].sort((a, b) => a.localeCompare(b));
  }, [allStats]);

  const filteredBatsmen = useMemo(() => {
    const q = batsmanSearch.toLowerCase();
    return players.filter((p) => p.toLowerCase().includes(q) && p !== bowler);
  }, [players, batsmanSearch, bowler]);

  const filteredBowlers = useMemo(() => {
    const q = bowlerSearch.toLowerCase();
    return players.filter((p) => p.toLowerCase().includes(q) && p !== batsman);
  }, [players, bowlerSearch, batsman]);

  // Fetch deliveries for relevant matches when both players are selected
  const fetchDeliveries = useCallback(async () => {
    if (!batsman || !bowler) return;

    setLoading(true);

    // Find matches where both players appear
    const relevantMatchIds = matchIds.filter((mid) => {
      const stats = allStats.get(mid);
      if (!stats) return false;
      const names = new Set(stats.map((s) => s.name.toLowerCase()));
      return names.has(batsman.toLowerCase()) && names.has(bowler.toLowerCase());
    });

    // Fetch deliveries (with cache)
    const result = new Map<string, DeliveryDocument[]>();
    const toFetch = relevantMatchIds.filter((id) => !deliveryCache.has(id));

    if (toFetch.length > 0) {
      const fetched = await Promise.all(
        toFetch.map(async (mid) => {
          const snap = await getDocs(collection(db, 'matches', mid, 'deliveries'));
          const dels = snap.docs.map((d) => d.data() as DeliveryDocument);
          return [mid, dels] as [string, DeliveryDocument[]];
        })
      );
      for (const [mid, dels] of fetched) {
        deliveryCache.set(mid, dels);
      }
    }

    for (const mid of relevantMatchIds) {
      const cached = deliveryCache.get(mid);
      if (cached) result.set(mid, cached);
    }

    setAllDeliveries(result);
    setLoading(false);
  }, [batsman, bowler, matchIds, allStats, deliveryCache]);

  // Auto-fetch when both players are selected
  useEffect(() => {
    if (batsman && bowler) {
      fetchDeliveries();
    } else {
      setAllDeliveries(new Map());
    }
  }, [batsman, bowler, fetchDeliveries]);

  // Calculate H2H
  const { stats, details } = useMemo(() => {
    if (!batsman || !bowler || allDeliveries.size === 0) {
      return { stats: null, details: [] };
    }
    return calculateHeadToHead(allDeliveries, matchMeta, batsman, bowler);
  }, [allDeliveries, matchMeta, batsman, bowler]);

  const swap = () => {
    const temp = batsman;
    setBatsman(bowler);
    setBowler(temp);
    setBatsmanSearch('');
    setBowlerSearch('');
  };

  const sr = stats && stats.ballsFaced > 0 ? ((stats.runsScored / stats.ballsFaced) * 100).toFixed(1) : '0.0';
  const avg = stats ? (stats.dismissals > 0 ? (stats.runsScored / stats.dismissals).toFixed(1) : stats.runsScored.toFixed(1)) : '0.0';
  const dotPct = stats && stats.ballsFaced > 0 ? ((stats.dotBalls / stats.ballsFaced) * 100).toFixed(0) : '0';

  return (
    <div>
      {/* Player selectors */}
      <div className="flex items-center gap-2 mb-4">
        {/* Batsman selector */}
        <div className="flex-1 relative" ref={batsmanRef}>
          <label className="text-[10px] font-semibold uppercase tracking-wider mb-1 block" style={{ color: 'var(--color-text-secondary)' }}>
            Batsman
          </label>
          <input
            type="text"
            placeholder="Select batsman..."
            value={showBatsmanDrop ? batsmanSearch : batsman}
            onChange={(e) => { setBatsmanSearch(e.target.value); setShowBatsmanDrop(true); }}
            onFocus={() => { setShowBatsmanDrop(true); setBatsmanSearch(''); }}
            className="w-full px-3 py-2 rounded-lg text-sm outline-none"
            style={{
              background: 'var(--color-surface)',
              border: '1px solid var(--color-border)',
              color: 'var(--color-text)',
            }}
          />
          {showBatsmanDrop && (
            <div
              className="absolute left-0 right-0 top-full mt-1 z-50 max-h-[200px] overflow-y-auto rounded-lg shadow-lg py-1"
              style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)' }}
            >
              {filteredBatsmen.length === 0 ? (
                <p className="px-3 py-2 text-xs" style={{ color: 'var(--color-text-secondary)' }}>No players found</p>
              ) : (
                filteredBatsmen.map((p) => (
                  <button
                    key={p}
                    onClick={() => { setBatsman(p); setShowBatsmanDrop(false); setBatsmanSearch(''); }}
                    className="w-full text-left px-3 py-2 text-sm hover:opacity-80 transition-colors"
                    style={{
                      background: batsman === p ? 'var(--color-primary)' : 'transparent',
                      color: batsman === p ? '#fff' : 'var(--color-text)',
                    }}
                  >
                    {p}
                  </button>
                ))
              )}
            </div>
          )}
        </div>

        {/* Swap button */}
        <button
          onClick={swap}
          className="mt-4 p-2 rounded-lg transition-colors hover:opacity-80"
          style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)' }}
          title="Swap"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="17 1 21 5 17 9" />
            <path d="M3 11V9a4 4 0 0 1 4-4h14" />
            <polyline points="7 23 3 19 7 15" />
            <path d="M21 13v2a4 4 0 0 1-4 4H3" />
          </svg>
        </button>

        {/* Bowler selector */}
        <div className="flex-1 relative" ref={bowlerRef}>
          <label className="text-[10px] font-semibold uppercase tracking-wider mb-1 block" style={{ color: 'var(--color-text-secondary)' }}>
            Bowler
          </label>
          <input
            type="text"
            placeholder="Select bowler..."
            value={showBowlerDrop ? bowlerSearch : bowler}
            onChange={(e) => { setBowlerSearch(e.target.value); setShowBowlerDrop(true); }}
            onFocus={() => { setShowBowlerDrop(true); setBowlerSearch(''); }}
            className="w-full px-3 py-2 rounded-lg text-sm outline-none"
            style={{
              background: 'var(--color-surface)',
              border: '1px solid var(--color-border)',
              color: 'var(--color-text)',
            }}
          />
          {showBowlerDrop && (
            <div
              className="absolute left-0 right-0 top-full mt-1 z-50 max-h-[200px] overflow-y-auto rounded-lg shadow-lg py-1"
              style={{ background: 'var(--color-surface)', border: '1px solid var(--color-border)' }}
            >
              {filteredBowlers.length === 0 ? (
                <p className="px-3 py-2 text-xs" style={{ color: 'var(--color-text-secondary)' }}>No players found</p>
              ) : (
                filteredBowlers.map((p) => (
                  <button
                    key={p}
                    onClick={() => { setBowler(p); setShowBowlerDrop(false); setBowlerSearch(''); }}
                    className="w-full text-left px-3 py-2 text-sm hover:opacity-80 transition-colors"
                    style={{
                      background: bowler === p ? 'var(--color-primary)' : 'transparent',
                      color: bowler === p ? '#fff' : 'var(--color-text)',
                    }}
                  >
                    {p}
                  </button>
                ))
              )}
            </div>
          )}
        </div>
      </div>

      {/* Loading */}
      {loading && (
        <div className="text-center py-8">
          <div className="inline-block w-6 h-6 border-2 border-current border-t-transparent rounded-full animate-spin" style={{ color: 'var(--color-primary)' }} />
          <p className="text-sm mt-2" style={{ color: 'var(--color-text-secondary)' }}>Loading H2H stats...</p>
        </div>
      )}

      {/* Empty state */}
      {!loading && !stats && batsman && bowler && (
        <p className="text-center py-10 text-sm" style={{ color: 'var(--color-text-secondary)' }}>
          No head-to-head data found between {batsman} (bat) and {bowler} (bowl).
        </p>
      )}

      {!loading && (!batsman || !bowler) && (
        <p className="text-center py-10 text-sm" style={{ color: 'var(--color-text-secondary)' }}>
          Select a batsman and bowler to view head-to-head stats.
        </p>
      )}

      {/* Stats summary */}
      {!loading && stats && (
        <div className="space-y-4">
          {/* Summary card */}
          <div className="card">
            <div className="flex items-center justify-between mb-3">
              <h3 className="font-semibold text-sm">
                {stats.batsmanName} vs {stats.bowlerName}
              </h3>
              <span className="text-xs px-2 py-0.5 rounded-full" style={{ background: 'var(--color-primary)', color: '#fff' }}>
                {stats.matchCount} match{stats.matchCount !== 1 ? 'es' : ''}
              </span>
            </div>

            {/* Key stats grid */}
            <div className="grid grid-cols-4 gap-3 mb-4">
              <StatBox label="Runs" value={String(stats.runsScored)} />
              <StatBox label="Balls" value={String(stats.ballsFaced)} />
              <StatBox label="SR" value={sr} />
              <StatBox label="Avg" value={avg} />
            </div>

            <div className="grid grid-cols-4 gap-3 mb-4">
              <StatBox label="Dots" value={String(stats.dotBalls)} sub={`${dotPct}%`} />
              <StatBox label="4s" value={String(stats.fours)} />
              <StatBox label="6s" value={String(stats.sixes)} />
              <StatBox label="Outs" value={String(stats.dismissals)} />
            </div>

            {/* Scoring breakdown bar */}
            {stats.ballsFaced > 0 && (
              <div>
                <p className="text-[10px] font-semibold uppercase tracking-wider mb-1.5" style={{ color: 'var(--color-text-secondary)' }}>
                  Scoring breakdown
                </p>
                <div className="flex rounded-lg overflow-hidden h-5 text-[10px] font-medium">
                  {stats.dotBalls > 0 && (
                    <div
                      className="flex items-center justify-center"
                      style={{ width: `${(stats.dotBalls / stats.ballsFaced) * 100}%`, background: '#6b7280', color: '#fff' }}
                      title={`Dots: ${stats.dotBalls}`}
                    >
                      {stats.dotBalls > 1 ? `${stats.dotBalls}` : ''}
                    </div>
                  )}
                  {stats.singles > 0 && (
                    <div
                      className="flex items-center justify-center"
                      style={{ width: `${(stats.singles / stats.ballsFaced) * 100}%`, background: '#3b82f6', color: '#fff' }}
                      title={`Singles: ${stats.singles}`}
                    >
                      {stats.singles > 1 ? `${stats.singles}` : ''}
                    </div>
                  )}
                  {stats.twos > 0 && (
                    <div
                      className="flex items-center justify-center"
                      style={{ width: `${(stats.twos / stats.ballsFaced) * 100}%`, background: '#8b5cf6', color: '#fff' }}
                      title={`Twos: ${stats.twos}`}
                    >
                      {stats.twos > 1 ? `${stats.twos}` : ''}
                    </div>
                  )}
                  {stats.threes > 0 && (
                    <div
                      className="flex items-center justify-center"
                      style={{ width: `${(stats.threes / stats.ballsFaced) * 100}%`, background: '#a855f7', color: '#fff' }}
                      title={`Threes: ${stats.threes}`}
                    >
                      {stats.threes > 0 ? `${stats.threes}` : ''}
                    </div>
                  )}
                  {stats.fours > 0 && (
                    <div
                      className="flex items-center justify-center"
                      style={{ width: `${(stats.fours / stats.ballsFaced) * 100}%`, background: '#22c55e', color: '#fff' }}
                      title={`Fours: ${stats.fours}`}
                    >
                      {stats.fours > 0 ? `${stats.fours}` : ''}
                    </div>
                  )}
                  {stats.sixes > 0 && (
                    <div
                      className="flex items-center justify-center"
                      style={{ width: `${(stats.sixes / stats.ballsFaced) * 100}%`, background: '#f59e0b', color: '#fff' }}
                      title={`Sixes: ${stats.sixes}`}
                    >
                      {stats.sixes > 0 ? `${stats.sixes}` : ''}
                    </div>
                  )}
                  {stats.dismissals > 0 && (
                    <div
                      className="flex items-center justify-center"
                      style={{ width: `${(stats.dismissals / stats.ballsFaced) * 100}%`, background: '#ef4444', color: '#fff' }}
                      title={`Wickets: ${stats.dismissals}`}
                    >
                      W
                    </div>
                  )}
                </div>
                <div className="flex flex-wrap gap-3 mt-1.5 text-[10px]" style={{ color: 'var(--color-text-secondary)' }}>
                  <span><span className="inline-block w-2 h-2 rounded-sm mr-1" style={{ background: '#6b7280' }} />Dots</span>
                  <span><span className="inline-block w-2 h-2 rounded-sm mr-1" style={{ background: '#3b82f6' }} />1s</span>
                  <span><span className="inline-block w-2 h-2 rounded-sm mr-1" style={{ background: '#8b5cf6' }} />2s</span>
                  <span><span className="inline-block w-2 h-2 rounded-sm mr-1" style={{ background: '#22c55e' }} />4s</span>
                  <span><span className="inline-block w-2 h-2 rounded-sm mr-1" style={{ background: '#f59e0b' }} />6s</span>
                  <span><span className="inline-block w-2 h-2 rounded-sm mr-1" style={{ background: '#ef4444' }} />W</span>
                </div>
              </div>
            )}
          </div>

          {/* Match-by-match breakdown */}
          {details.length > 0 && (
            <div>
              <h3 className="font-semibold text-sm mb-2">Match-by-Match</h3>
              <div className="space-y-2">
                {details.map((d) => (
                  <div key={d.matchId} className="card flex items-center justify-between">
                    <div>
                      <p className="text-sm font-medium">
                        {d.team1Name} vs {d.team2Name}
                      </p>
                      <p className="text-[10px]" style={{ color: 'var(--color-text-secondary)' }}>
                        {new Date(d.matchDate).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })}
                      </p>
                    </div>
                    <div className="flex items-center gap-3">
                      <div className="text-right">
                        <p className="font-bold text-sm">
                          {d.runsScored}({d.ballsFaced})
                        </p>
                        <p className="text-[10px]" style={{ color: 'var(--color-text-secondary)' }}>
                          {d.fours}x4 {d.sixes}x6
                        </p>
                      </div>
                      {d.wasOut ? (
                        <span className="w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-bold" style={{ background: 'rgba(239,68,68,0.15)', color: '#ef4444' }}>
                          W
                        </span>
                      ) : (
                        <span className="w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-bold" style={{ background: 'rgba(34,197,94,0.15)', color: '#22c55e' }}>
                          ✓
                        </span>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// ============================== Helpers ==============================

function StatBox({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div className="text-center">
      <p className="text-lg font-bold">{value}</p>
      <p className="text-[10px]" style={{ color: 'var(--color-text-secondary)' }}>
        {label}
        {sub && <span className="ml-0.5">({sub})</span>}
      </p>
    </div>
  );
}
