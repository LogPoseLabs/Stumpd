import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useFullMatch } from '../hooks/useFirestore';
import Loader from '../components/Loader';
import type {
  PlayerStatDocument,
  DeliveryDocument,
  PartnershipDocument,
} from '../types';

function formatDate(ts: number): string {
  return new Date(ts).toLocaleDateString('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function formatOvers(o: number): string {
  const full = Math.floor(o);
  const balls = Math.round((o - full) * 10);
  return `${full}.${balls}`;
}

function ballClass(outcome: string): string {
  const o = outcome.toUpperCase();
  if (o.includes('RO') || o.includes('RUN OUT')) return 'ball ball-wicket';
  if (o === 'W' || o.includes('WICKET')) return 'ball ball-wicket';
  if (o === '4' || o.includes('FOUR')) return 'ball ball-four';
  if (o === '6' || o.includes('SIX')) return 'ball ball-six';
  if (o === '0' || o === 'DOT') return 'ball ball-dot';
  if (o.includes('WIDE') || o === 'WD') return 'ball ball-wide';
  if (o.includes('NO BALL') || o === 'NB') return 'ball ball-noball';
  return 'ball ball-run';
}

/**
 * Extract a short label (1-3 chars) for the ball indicator.
 * Long outcomes like "0 + RO (Manoj @ S)" become "RO".
 * The full text is shown below or on hover.
 */
function shortLabel(outcome: string): string {
  const o = outcome.toUpperCase().trim();

  // Run-out variants: "0 + RO (...)", "RO (...)", "1 + RO (...)"
  if (o.includes('RO') || o.includes('RUN OUT')) return 'RO';

  // Stumped variants
  if (o.includes('STUMPED') || o.includes('ST ')) return 'ST';

  // Caught variants
  if (o.includes('CAUGHT') || (o.startsWith('C ') && o.includes('B '))) return 'W';

  // Wide / No-ball
  if (o.includes('WIDE') || o === 'WD') return 'WD';
  if (o.includes('NO BALL') || o === 'NB') return 'NB';

  // Standard short outcomes: 0, 1, 2, 3, 4, 6, W, DOT
  if (o.length <= 3) return outcome.trim();

  // Fallback: first 2 characters
  return outcome.trim().slice(0, 2);
}

/**
 * Returns extra detail text for a delivery (shown below the ball), or null.
 * e.g. "0 + RO (Manoj @ S)" → "Manoj @ S"
 */
function deliveryDetail(outcome: string): string | null {
  const o = outcome.trim();

  // Extract parenthesised content: "... (Name @ S)" → "Name @ S"
  const parenMatch = o.match(/\(([^)]+)\)/);
  if (parenMatch) return parenMatch[1].trim();

  return null;
}

// ============================== Dismissal text (mirrors Android getDismissalText) ==============================

function getDismissalText(b: PlayerStatDocument): string {
  if (b.isRetired) return 'retd';
  if (!b.isOut || !b.dismissalType) return 'not out';

  switch (b.dismissalType.toUpperCase()) {
    case 'CAUGHT': {
      // Caught & bowled (same person)
      if (b.fielderName && b.bowlerName && b.fielderName === b.bowlerName) {
        return `c & b ${b.bowlerName}`;
      }
      const fielder = b.fielderName ? `c ${b.fielderName} ` : '';
      const bowler = b.bowlerName ? `b ${b.bowlerName}` : '';
      return `${fielder}${bowler}`.trim() || 'caught';
    }
    case 'BOWLED':
      return b.bowlerName ? `b ${b.bowlerName}` : 'bowled';
    case 'LBW':
      return b.bowlerName ? `lbw b ${b.bowlerName}` : 'lbw';
    case 'STUMPED': {
      const st = b.fielderName ? `st ${b.fielderName} ` : '';
      const bw = b.bowlerName ? `b ${b.bowlerName}` : '';
      return `${st}${bw}`.trim() || 'stumped';
    }
    case 'RUN_OUT':
      return b.fielderName ? `run out (${b.fielderName})` : 'run out';
    case 'HIT_WICKET':
      return b.bowlerName ? `hit wicket b ${b.bowlerName}` : 'hit wicket';
    case 'BOUNDARY_OUT':
      return b.bowlerName ? `boundary out b ${b.bowlerName}` : 'boundary out';
    default:
      return 'out';
  }
}

// ============================== Extras computation ==============================

interface ExtrasInfo {
  total: number;
  wides: number;
  noBalls: number;
  /** Any remaining extras not attributable to wides/no-balls from delivery data */
  other: number;
}

function computeExtras(
  inningsDeliveries: DeliveryDocument[],
  batters: PlayerStatDocument[],
  totalInningsRuns: number
): ExtrasInfo {
  const batRuns = batters.reduce((s, b) => s + b.runs, 0);
  const total = Math.max(0, totalInningsRuns - batRuns);

  if (total === 0) return { total: 0, wides: 0, noBalls: 0, other: 0 };

  // Count wide and no-ball deliveries and their extra runs
  let wides = 0;
  let noBalls = 0;

  for (const d of inningsDeliveries) {
    const o = d.outcome.toUpperCase();
    if (o.includes('WIDE') || o === 'WD') {
      // Wide delivery: the 'runs' field includes the wide run(s) + any batsman runs
      // We count each wide delivery
      wides++;
    } else if (o.includes('NO BALL') || o.includes('NO-BALL') || o === 'NB') {
      noBalls++;
    }
  }

  // If we have delivery data, the total extras should tally
  // Otherwise just show the total
  if (inningsDeliveries.length === 0) {
    return { total, wides: 0, noBalls: 0, other: total };
  }

  return { total, wides, noBalls, other: 0 };
}

type ScorecardTab = 'scorecard' | 'partnerships' | 'deliveries';

export default function MatchScorecard() {
  const { matchId } = useParams<{ matchId: string }>();
  const { fullMatch, loading } = useFullMatch(matchId);
  const [tab, setTab] = useState<ScorecardTab>('scorecard');

  if (loading) return <Loader text="Loading scorecard..." />;
  if (!fullMatch) {
    return (
      <div className="text-center py-20">
        <h2 className="text-2xl font-bold mb-2">Match Not Found</h2>
      </div>
    );
  }

  const { match, stats, partnerships, deliveries } = fullMatch;
  const team1Won = match.winnerTeam === match.team1Name;

  // Separate batting/bowling stats
  const team1Batting = stats
    .filter((s) => s.team === match.team1Name && s.role === 'BAT')
    .sort((a, b) => a.battingPosition - b.battingPosition);
  const team2Batting = stats
    .filter((s) => s.team === match.team2Name && s.role === 'BAT')
    .sort((a, b) => a.battingPosition - b.battingPosition);
  // Team2 bowled in 1st innings, Team1 bowled in 2nd innings
  const team2Bowling = stats
    .filter((s) => s.team === match.team2Name && s.role === 'BOWL')
    .sort((a, b) => a.bowlingPosition - b.bowlingPosition);
  const team1Bowling = stats
    .filter((s) => s.team === match.team1Name && s.role === 'BOWL')
    .sort((a, b) => a.bowlingPosition - b.bowlingPosition);

  // Compute wickets from batting data (more reliable than stored value)
  const innings1Wickets = team1Batting.filter((b) => b.isOut).length || match.firstInningsWickets;
  const innings2Wickets = team2Batting.filter((b) => b.isOut).length || match.secondInningsWickets;

  const innings1Deliveries = deliveries.filter((d) => d.inning === 1);
  const innings2Deliveries = deliveries.filter((d) => d.inning === 2);

  // Compute extras breakdown per innings from delivery data
  const innings1Extras = computeExtras(innings1Deliveries, team1Batting, match.firstInningsRuns);
  const innings2Extras = computeExtras(innings2Deliveries, team2Batting, match.secondInningsRuns);
  // Use innings field if available (new data), fall back to index-based split (legacy data)
  const hasInningsField = partnerships.some((p) => p.innings != null);
  const innings1Partnerships = hasInningsField
    ? partnerships.filter((p) => p.innings === 1)
    : partnerships.slice(0, Math.ceil(partnerships.length / 2));
  const innings2Partnerships = hasInningsField
    ? partnerships.filter((p) => p.innings === 2)
    : partnerships.slice(Math.ceil(partnerships.length / 2));

  const tabs: { key: ScorecardTab; label: string }[] = [
    { key: 'scorecard', label: 'Scorecard' },
    { key: 'partnerships', label: 'Partnerships' },
    { key: 'deliveries', label: 'Ball by Ball' },
  ];

  return (
    <div>
      {/* Match header */}
      <div className="card mb-4">
        <p className="text-xs mb-2" style={{ color: 'var(--color-text-secondary)' }}>
          {formatDate(match.matchDate)}
          {match.groupName && ` · ${match.groupName}`}
        </p>
        <div className="flex items-center gap-3 my-2">
          <div className="flex-1">
            <p className={`font-semibold ${team1Won ? '' : 'opacity-70'}`}>{match.team1Name}</p>
            <p className="text-2xl font-bold">
              {match.firstInningsRuns}/{innings1Wickets}
            </p>
          </div>
          <span className="text-xs font-bold" style={{ color: 'var(--color-text-secondary)' }}>vs</span>
          <div className="flex-1 text-right">
            <p className={`font-semibold ${!team1Won ? '' : 'opacity-70'}`}>{match.team2Name}</p>
            <p className="text-2xl font-bold">
              {match.secondInningsRuns}/{innings2Wickets}
            </p>
          </div>
        </div>
        <p className="text-sm font-medium" style={{ color: 'var(--color-success)' }}>
          {match.winnerTeam} won by {match.winningMargin}
        </p>
        {match.playerOfTheMatchName && (
          <p className="text-xs mt-1" style={{ color: 'var(--color-accent)' }}>
            Player of the Match: {match.playerOfTheMatchName}
            {match.playerOfTheMatchSummary && ` — ${match.playerOfTheMatchSummary}`}
          </p>
        )}
      </div>

      {/* Tabs */}
      <div className="flex gap-1 mb-4 overflow-x-auto border-b" style={{ borderColor: 'var(--color-border)' }}>
        {tabs.map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={`px-3 py-2 text-sm whitespace-nowrap ${tab === t.key ? 'tab-active' : 'tab-inactive'}`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* Tab content */}
      {tab === 'scorecard' && (
        <div className="space-y-3">
          <InningsCard
            title={`${match.team1Name} — 1st Innings`}
            score={`${match.firstInningsRuns}/${innings1Wickets}`}
            batters={team1Batting}
            bowlers={team2Bowling}
            totalRuns={match.firstInningsRuns}
            totalWickets={innings1Wickets}
            extras={innings1Extras}
            defaultOpen={true}
          />
          <InningsCard
            title={`${match.team2Name} — 2nd Innings`}
            score={`${match.secondInningsRuns}/${innings2Wickets}`}
            batters={team2Batting}
            bowlers={team1Bowling}
            totalRuns={match.secondInningsRuns}
            totalWickets={innings2Wickets}
            extras={innings2Extras}
            defaultOpen={true}
          />
        </div>
      )}
      {tab === 'partnerships' && (
        <div className="space-y-4">
          <PartnershipsCard title="1st Innings" partnerships={innings1Partnerships} />
          <PartnershipsCard title="2nd Innings" partnerships={innings2Partnerships} />
        </div>
      )}
      {tab === 'deliveries' && (
        <div className="space-y-4">
          <DeliveriesCard title="1st Innings" deliveries={innings1Deliveries} />
          <DeliveriesCard title="2nd Innings" deliveries={innings2Deliveries} />
        </div>
      )}
    </div>
  );
}

// ============================== Combined Innings Card (Batting + Bowling) ==============================

function InningsCard({
  title,
  score,
  batters,
  bowlers,
  totalRuns,
  totalWickets,
  extras,
  defaultOpen,
}: {
  title: string;
  score: string;
  batters: PlayerStatDocument[];
  bowlers: PlayerStatDocument[];
  totalRuns: number;
  totalWickets: number;
  extras: ExtrasInfo;
  defaultOpen: boolean;
}) {
  const [open, setOpen] = useState(defaultOpen);

  return (
    <div className="card overflow-hidden">
      {/* Collapsible header */}
      <button
        onClick={() => setOpen(!open)}
        className="w-full flex items-center justify-between py-1 cursor-pointer"
      >
        <h3 className="font-semibold text-sm">{title}</h3>
        <div className="flex items-center gap-2">
          <span className="font-bold text-lg">{score}</span>
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.5"
            strokeLinecap="round"
            strokeLinejoin="round"
            className="transition-transform"
            style={{
              transform: open ? 'rotate(180deg)' : 'rotate(0deg)',
              color: 'var(--color-text-secondary)',
            }}
          >
            <polyline points="6 9 12 15 18 9" />
          </svg>
        </div>
      </button>

      {open && (
        <div className="mt-3">
          {/* Batting */}
          <BattingTable batters={batters} totalRuns={totalRuns} totalWickets={totalWickets} extras={extras} />

          {/* Divider */}
          <div
            className="my-4 border-t"
            style={{ borderColor: 'var(--color-border)' }}
          />

          {/* Bowling */}
          <BowlingTable bowlers={bowlers} />
        </div>
      )}
    </div>
  );
}

// ============================== Batting Table ==============================

function BattingTable({
  batters,
  totalRuns,
  totalWickets,
  extras,
}: {
  batters: PlayerStatDocument[];
  totalRuns: number;
  totalWickets: number;
  extras: ExtrasInfo;
}) {
  return (
    <>
      <table className="w-full text-sm">
        <thead>
          <tr
            className="text-xs border-b"
            style={{
              color: 'var(--color-text-secondary)',
              borderColor: 'var(--color-border)',
            }}
          >
            <th className="py-1.5 text-left">Batter</th>
            <th className="py-1.5 text-right">R</th>
            <th className="py-1.5 text-right">B</th>
            <th className="py-1.5 text-right">4s</th>
            <th className="py-1.5 text-right">6s</th>
            <th className="py-1.5 text-right">SR</th>
          </tr>
        </thead>
        <tbody>
          {batters.map((b) => {
            const sr =
              b.ballsFaced > 0
                ? ((b.runs / b.ballsFaced) * 100).toFixed(1)
                : '0.0';
            const dismissal = getDismissalText(b);
            return (
              <tr
                key={`${b.playerId}_${b.team}_BAT`}
                className="border-b"
                style={{ borderColor: 'var(--color-border)' }}
              >
                <td className="py-2">
                  <p className="font-medium">{b.name}</p>
                  <p
                    className="text-[10px]"
                    style={{ color: 'var(--color-text-secondary)' }}
                  >
                    {dismissal}
                  </p>
                </td>
                <td className="py-2 text-right font-semibold">{b.runs}</td>
                <td className="py-2 text-right">{b.ballsFaced}</td>
                <td className="py-2 text-right">{b.fours}</td>
                <td className="py-2 text-right">{b.sixes}</td>
                <td className="py-2 text-right">{sr}</td>
              </tr>
            );
          })}
        </tbody>
      </table>

      {/* Extras row */}
      {extras.total > 0 && (
        <div
          className="py-1.5 flex justify-between text-xs"
          style={{ color: 'var(--color-text-secondary)' }}
        >
          <span>
            Extras
            {(extras.wides > 0 || extras.noBalls > 0) && (
              <span className="ml-1">
                ({[
                  extras.wides > 0 ? `${extras.wides}w` : '',
                  extras.noBalls > 0 ? `${extras.noBalls}nb` : '',
                ].filter(Boolean).join(', ')})
              </span>
            )}
          </span>
          <span className="font-medium">{extras.total}</span>
        </div>
      )}

      {/* Total */}
      <div
        className="pt-2 border-t flex justify-between font-semibold text-sm"
        style={{ borderColor: 'var(--color-border)' }}
      >
        <span>Total</span>
        <span>
          {totalRuns}/{totalWickets}
        </span>
      </div>
    </>
  );
}

// ============================== Bowling Table ==============================

function BowlingTable({ bowlers }: { bowlers: PlayerStatDocument[] }) {
  if (bowlers.length === 0) return null;
  return (
    <>
      <p
        className="text-xs font-semibold mb-2"
        style={{ color: 'var(--color-text-secondary)' }}
      >
        Bowling
      </p>
      <table className="w-full text-sm">
        <thead>
          <tr
            className="text-xs border-b"
            style={{
              color: 'var(--color-text-secondary)',
              borderColor: 'var(--color-border)',
            }}
          >
            <th className="py-1.5 text-left">Bowler</th>
            <th className="py-1.5 text-right">O</th>
            <th className="py-1.5 text-right">M</th>
            <th className="py-1.5 text-right">R</th>
            <th className="py-1.5 text-right">W</th>
            <th className="py-1.5 text-right">Econ</th>
          </tr>
        </thead>
        <tbody>
          {bowlers.map((b) => {
            const econ =
              b.oversBowled > 0
                ? (b.runsConceded / b.oversBowled).toFixed(1)
                : '-';
            return (
              <tr
                key={`${b.playerId}_${b.team}_BOWL`}
                className="border-b"
                style={{ borderColor: 'var(--color-border)' }}
              >
                <td className="py-2 font-medium">{b.name}</td>
                <td className="py-2 text-right">
                  {formatOvers(b.oversBowled)}
                </td>
                <td className="py-2 text-right">{b.maidenOvers}</td>
                <td className="py-2 text-right">{b.runsConceded}</td>
                <td className="py-2 text-right font-semibold">{b.wickets}</td>
                <td className="py-2 text-right">{econ}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </>
  );
}

// ============================== Partnerships Card ==============================

function PartnershipsCard({
  title,
  partnerships,
}: {
  title: string;
  partnerships: PartnershipDocument[];
}) {
  if (partnerships.length === 0) return null;
  return (
    <div className="card">
      <h3 className="font-semibold mb-3">{title}</h3>
      <div className="space-y-2">
        {partnerships.map((p, i) => (
          <div key={i} className="flex items-center gap-3 text-sm">
            <span
              className="text-xs w-6 text-center"
              style={{ color: 'var(--color-text-secondary)' }}
            >
              {i + 1}
            </span>
            <div className="flex-1">
              <span className="font-medium">{p.batsman1Name}</span>
              <span style={{ color: 'var(--color-text-secondary)' }}>
                {' '}
                ({p.batsman1Runs}){' '}
              </span>
              <span style={{ color: 'var(--color-text-secondary)' }}>&</span>
              <span className="font-medium"> {p.batsman2Name}</span>
              <span style={{ color: 'var(--color-text-secondary)' }}>
                {' '}
                ({p.batsman2Runs})
              </span>
            </div>
            <span className="font-bold">{p.runs}</span>
            <span
              className="text-xs"
              style={{ color: 'var(--color-text-secondary)' }}
            >
              ({p.balls}b)
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

// ============================== Deliveries Card ==============================

function DeliveriesCard({
  title,
  deliveries,
}: {
  title: string;
  deliveries: DeliveryDocument[];
}) {
  if (deliveries.length === 0) return null;
  // Group by over
  const overs = new Map<number, DeliveryDocument[]>();
  for (const d of deliveries) {
    if (!overs.has(d.over)) overs.set(d.over, []);
    overs.get(d.over)!.push(d);
  }

  return (
    <div className="card">
      <h3 className="font-semibold mb-3">{title}</h3>
      <div className="space-y-3">
        {Array.from(overs.entries())
          .sort(([a], [b]) => a - b)
          .map(([overNum, balls]) => (
            <div key={overNum}>
              <div className="flex items-center gap-2 mb-1">
                <span
                  className="text-xs font-semibold"
                  style={{ color: 'var(--color-text-secondary)' }}
                >
                  Over {overNum}
                </span>
                <span
                  className="text-xs"
                  style={{ color: 'var(--color-text-secondary)' }}
                >
                  ({balls[0]?.bowlerName})
                </span>
                <span className="text-xs ml-auto font-medium">
                  {balls.reduce((s, b) => s + b.runs, 0)} runs
                </span>
              </div>
              <div className="flex flex-wrap gap-2">
                {balls.map((b, i) => {
                  const detail = deliveryDetail(b.outcome);
                  return (
                    <div key={i} className="flex flex-col items-center" style={{ maxWidth: 48 }}>
                      <span
                        className={ballClass(b.outcome)}
                        title={`${b.strikerName}: ${b.outcome}`}
                      >
                        {shortLabel(b.outcome)}
                      </span>
                      {detail && (
                        <span
                          className="text-[8px] leading-tight mt-0.5 text-center truncate w-full"
                          style={{ color: 'var(--color-text-secondary)' }}
                          title={detail}
                        >
                          {detail}
                        </span>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          ))}
      </div>
    </div>
  );
}
