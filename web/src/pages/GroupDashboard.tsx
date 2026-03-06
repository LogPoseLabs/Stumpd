import { useState, useMemo } from 'react';
import { useParams } from 'react-router-dom';
import {
  useGroup,
  useGroupByInviteCode,
  useGroupMatchesWithStats,
  useGroupLiveMatches,
} from '../hooks/useFirestore';
import { buildPlayerStats } from '../utils/statsCalculator';
import RankingsTab from '../components/RankingsTab';
import MatchHistoryTab from '../components/MatchHistoryTab';
import PlayerStatsTab from '../components/PlayerStatsTab';
import LiveMatchesTab from '../components/LiveMatchesTab';
import HeadToHeadTab from '../components/HeadToHeadTab';
import DateFilter, {
  applyDateFilter,
  type DateFilterState,
} from '../components/DateFilter';
import Loader from '../components/Loader';

const TABS = ['Matches', 'Stats', 'Rankings', 'H2H'] as const;
type TabName = (typeof TABS)[number];
type MatchSubTab = 'Live' | 'History';
type PitchFilter = 'all' | 'long' | 'short';

export default function GroupDashboard() {
  const { groupId, inviteCode } = useParams<{
    groupId?: string;
    inviteCode?: string;
  }>();

  // If coming via invite code, resolve to groupId
  const inviteResult = useGroupByInviteCode(inviteCode);
  const resolvedGroupId = groupId ?? inviteResult.group?.id;

  const { group, loading: groupLoading, error: groupError } = useGroup(resolvedGroupId);
  const { data: matchesWithStats, loading: matchesLoading, error: matchesError } =
    useGroupMatchesWithStats(resolvedGroupId);
  const { matches: liveMatches, loading: liveLoading } =
    useGroupLiveMatches(resolvedGroupId);

  const [activeTab, setActiveTab] = useState<TabName>('Matches');
  const [matchSubTab, setMatchSubTab] = useState<MatchSubTab>('Live');
  const [dateFilter, setDateFilter] = useState<DateFilterState>({
    preset: 'all',
    startDate: '',
    endDate: '',
  });
  const [pitchFilter, setPitchFilter] = useState<PitchFilter>('long');

  // All matches (sorted newest first, which they already are from Firestore)
  const allMatches = useMemo(
    () => matchesWithStats.map((m) => m.match),
    [matchesWithStats]
  );

  // Matches filtered by pitch type only (used for date filter dropdown context)
  const pitchFilteredMatches = useMemo(() => {
    if (pitchFilter === 'long') return allMatches.filter((m) => !m.shortPitch);
    if (pitchFilter === 'short') return allMatches.filter((m) => m.shortPitch);
    return allMatches;
  }, [allMatches, pitchFilter]);

  // Filtered matches based on pitch + date filter
  const filteredMatchIds = useMemo(() => {
    const filtered = applyDateFilter(pitchFilteredMatches, dateFilter);
    return new Set(filtered.map((m) => m.id));
  }, [pitchFilteredMatches, dateFilter]);

  const filteredMatchesWithStats = useMemo(
    () => matchesWithStats.filter((m) => filteredMatchIds.has(m.match.id)),
    [matchesWithStats, filteredMatchIds]
  );

  const filteredMatches = useMemo(
    () => filteredMatchesWithStats.map((m) => m.match),
    [filteredMatchesWithStats]
  );

  // Build player stats from filtered matches
  const playerStats = useMemo(() => {
    if (!filteredMatchesWithStats.length) return new Map();
    return buildPlayerStats(filteredMatchesWithStats, resolvedGroupId);
  }, [filteredMatchesWithStats, resolvedGroupId]);

  // Group match context for ranking missed-match penalty (respects filters)
  const groupMatchContext = useMemo<[string, number][]>(() => {
    return filteredMatchesWithStats.map(({ match }) => [match.id, match.matchDate]);
  }, [filteredMatchesWithStats]);

  // H2H data (uses filtered matches respecting pitch + date filters)
  const h2hMatchIds = useMemo(
    () => filteredMatchesWithStats.map(({ match }) => match.id),
    [filteredMatchesWithStats]
  );
  const h2hMatchMeta = useMemo(() => {
    const map = new Map<string, { date: number; team1: string; team2: string }>();
    for (const { match } of filteredMatchesWithStats) {
      map.set(match.id, { date: match.matchDate, team1: match.team1Name, team2: match.team2Name });
    }
    return map;
  }, [filteredMatchesWithStats]);
  const h2hAllStats = useMemo(() => {
    const map = new Map<string, import('../types').PlayerStatDocument[]>();
    for (const { match, stats } of filteredMatchesWithStats) {
      map.set(match.id, stats);
    }
    return map;
  }, [filteredMatchesWithStats]);

  const loading = groupLoading || matchesLoading || inviteResult.loading;

  if (loading) return <Loader text="Loading group data..." />;

  if (inviteResult.error) {
    return (
      <div className="text-center py-20">
        <h2 className="text-2xl font-bold mb-2">Group Not Found</h2>
        <p style={{ color: 'var(--color-text-secondary)' }}>
          No group found with invite code "{inviteCode}". Check the code and try
          again.
        </p>
      </div>
    );
  }

  if (groupError || matchesError) {
    const errMsg = groupError || matchesError || '';
    const isIndexError = errMsg.includes('index');
    return (
      <div className="text-center py-20">
        <h2 className="text-2xl font-bold mb-2">
          {isIndexError ? 'Setting Up...' : 'Something Went Wrong'}
        </h2>
        <p className="max-w-md mx-auto" style={{ color: 'var(--color-text-secondary)' }}>
          {isIndexError
            ? 'The database index is still being built. This usually takes 1-2 minutes. Please refresh the page shortly.'
            : errMsg}
        </p>
        <button
          onClick={() => window.location.reload()}
          className="mt-4 px-5 py-2 rounded-lg font-semibold text-white"
          style={{ background: 'var(--color-primary)' }}
        >
          Refresh
        </button>
      </div>
    );
  }

  if (!group && !resolvedGroupId) {
    return (
      <div className="text-center py-20">
        <h2 className="text-2xl font-bold mb-2">Group Not Found</h2>
        <p style={{ color: 'var(--color-text-secondary)' }}>
          The group you're looking for doesn't exist.
        </p>
      </div>
    );
  }

  const displayName = group?.name ?? 'Group';
  const showFilters =
    (activeTab === 'Matches' && matchSubTab === 'History') ||
    activeTab === 'Stats' ||
    activeTab === 'Rankings';
  const isFiltered = dateFilter.preset !== 'all' || pitchFilter !== 'all';

  return (
    <div>
      {/* Group header */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold">{displayName}</h1>
        <p className="text-sm" style={{ color: 'var(--color-text-secondary)' }}>
          {matchesWithStats.length} match{matchesWithStats.length !== 1 ? 'es' : ''} &middot;{' '}
          {playerStats.size} player{playerStats.size !== 1 ? 's' : ''}
          {isFiltered && (
            <span style={{ color: 'var(--color-primary)' }}>
              {' '}
              &middot; Showing {filteredMatches.length} of {allMatches.length}
            </span>
          )}
        </p>
      </div>

      {/* Primary Tabs */}
      <div
        className="flex gap-1 mb-4 overflow-x-auto border-b"
        style={{ borderColor: 'var(--color-border)' }}
      >
        {TABS.map((tab) => (
          <button
            key={tab}
            onClick={() => setActiveTab(tab)}
            className={`px-4 py-2.5 text-sm whitespace-nowrap transition-colors ${
              activeTab === tab ? 'tab-active' : 'tab-inactive'
            }`}
          >
            {tab}
            {tab === 'Matches' && liveMatches.length > 0 && (
              <span
                className="ml-1.5 inline-flex items-center justify-center w-2 h-2 rounded-full bg-red-500"
                title="Live matches"
              />
            )}
          </button>
        ))}
      </div>

      {/* Matches sub-tabs */}
      {activeTab === 'Matches' && (
        <div className="flex gap-2 mb-4">
          {(['Live', 'History'] as MatchSubTab[]).map((sub) => (
            <button
              key={sub}
              onClick={() => setMatchSubTab(sub)}
              className="px-3.5 py-1.5 rounded-full text-xs font-semibold transition-colors"
              style={{
                background:
                  matchSubTab === sub
                    ? 'var(--color-primary)'
                    : 'var(--color-surface)',
                color: matchSubTab === sub ? '#fff' : 'var(--color-text-secondary)',
                border:
                  matchSubTab === sub
                    ? 'none'
                    : '1px solid var(--color-border)',
              }}
            >
              {sub}
              {sub === 'Live' && liveMatches.length > 0 && (
                <span className="ml-1.5 inline-block w-1.5 h-1.5 rounded-full bg-red-500 live-indicator" />
              )}
            </button>
          ))}
        </div>
      )}

      {/* Filters (History, Stats & Rankings) */}
      {showFilters && (
        <div className="mb-4 flex flex-wrap gap-3 items-start">
          {/* Pitch type pills */}
          <div className="flex rounded-lg overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
            {([
              { key: 'long' as PitchFilter, label: 'Long Pitch' },
              { key: 'short' as PitchFilter, label: 'Short Pitch' },
              { key: 'all' as PitchFilter, label: 'All' },
            ]).map((opt) => (
              <button
                key={opt.key}
                onClick={() => setPitchFilter(opt.key)}
                className="px-3 py-2 text-xs font-medium transition-colors"
                style={{
                  background: pitchFilter === opt.key ? 'var(--color-primary)' : 'var(--color-surface)',
                  color: pitchFilter === opt.key ? '#fff' : 'var(--color-text-secondary)',
                }}
              >
                {opt.label}
              </button>
            ))}
          </div>

          {/* Date filter */}
          <DateFilter
            value={dateFilter}
            onChange={setDateFilter}
            matchDates={pitchFilteredMatches.map((m) => m.matchDate)}
          />
        </div>
      )}

      {/* Tab content */}
      {activeTab === 'Matches' && matchSubTab === 'Live' && (
        <LiveMatchesTab matches={liveMatches} loading={liveLoading} />
      )}
      {activeTab === 'Matches' && matchSubTab === 'History' && (
        <MatchHistoryTab matches={filteredMatches} />
      )}
      {activeTab === 'Stats' && <PlayerStatsTab playerStats={playerStats} />}
      {activeTab === 'Rankings' && (
        <RankingsTab
          playerStats={playerStats}
          groupMatchContext={groupMatchContext}
        />
      )}
      {activeTab === 'H2H' && (
        <HeadToHeadTab
          matchIds={h2hMatchIds}
          matchMeta={h2hMatchMeta}
          allStats={h2hAllStats}
        />
      )}
    </div>
  );
}
