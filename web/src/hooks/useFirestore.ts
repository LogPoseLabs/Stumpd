import { useState, useEffect } from 'react';
import {
  collection,
  query,
  where,
  getDocs,
  doc,
  getDoc,
  orderBy,
  limit,
  onSnapshot,
} from 'firebase/firestore';
import { db } from '../firebase';
import type {
  MatchDocument,
  PlayerStatDocument,
  GroupDocument,
  PartnershipDocument,
  FallOfWicketDocument,
  DeliveryDocument,
  PlayerImpactDocument,
  SharedMatchDocument,
  InProgressMatchDocument,
  FullMatch,
} from '../types';

// ==================== Group hooks ====================

export function useGroup(groupId: string | undefined) {
  const [group, setGroup] = useState<GroupDocument | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!groupId) { setLoading(false); return; }
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const snap = await getDoc(doc(db, 'groups', groupId));
        if (snap.exists()) setGroup(snap.data() as GroupDocument);
        else setError('Group not found');
      } catch (e: any) {
        console.error('useGroup error:', e);
        setError(e.message || 'Failed to load group');
      }
      setLoading(false);
    })();
  }, [groupId]);

  return { group, loading, error };
}

export function useGroupByInviteCode(inviteCode: string | undefined) {
  const [group, setGroup] = useState<GroupDocument | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!inviteCode) { setLoading(false); return; }
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const q = query(
          collection(db, 'groups'),
          where('inviteCode', '==', inviteCode.toUpperCase()),
          limit(1)
        );
        const snap = await getDocs(q);
        if (snap.empty) {
          setError('Group not found');
          setGroup(null);
        } else {
          setGroup(snap.docs[0].data() as GroupDocument);
        }
      } catch (e: any) {
        console.error('useGroupByInviteCode error:', e);
        setError(e.message || 'Failed to find group');
      }
      setLoading(false);
    })();
  }, [inviteCode]);

  return { group, loading, error };
}

// ==================== Match hooks ====================

export function useGroupMatches(groupId: string | undefined) {
  const [matches, setMatches] = useState<MatchDocument[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!groupId) { setLoading(false); return; }
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const q = query(
          collection(db, 'matches'),
          where('groupId', '==', groupId),
          orderBy('matchDate', 'desc')
        );
        const snap = await getDocs(q);
        setMatches(snap.docs.map((d) => d.data() as MatchDocument));
      } catch (e: any) {
        console.error('useGroupMatches error:', e);
        setError(e.message || 'Failed to load matches');
      }
      setLoading(false);
    })();
  }, [groupId]);

  return { matches, loading, error };
}

export function useMatchStats(matchId: string | undefined) {
  const [stats, setStats] = useState<PlayerStatDocument[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!matchId) { setLoading(false); return; }
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const snap = await getDocs(
          collection(db, 'matches', matchId, 'stats')
        );
        setStats(snap.docs.map((d) => d.data() as PlayerStatDocument));
      } catch (e: any) {
        console.error('useMatchStats error:', e);
        setError(e.message || 'Failed to load stats');
      }
      setLoading(false);
    })();
  }, [matchId]);

  return { stats, loading, error };
}

export function useFullMatch(matchId: string | undefined) {
  const [fullMatch, setFullMatch] = useState<FullMatch | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!matchId) { setLoading(false); return; }
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const matchDoc = await getDoc(doc(db, 'matches', matchId));
        if (!matchDoc.exists()) {
          setError('Match not found');
          setLoading(false);
          return;
        }

        const [statsSnap, partSnap, fowSnap, delSnap, impSnap] =
          await Promise.all([
            getDocs(collection(db, 'matches', matchId, 'stats')),
            getDocs(collection(db, 'matches', matchId, 'partnerships')),
            getDocs(collection(db, 'matches', matchId, 'fall_of_wickets')),
            getDocs(collection(db, 'matches', matchId, 'deliveries')),
            getDocs(collection(db, 'matches', matchId, 'impacts')),
          ]);

        setFullMatch({
          match: matchDoc.data() as MatchDocument,
          stats: statsSnap.docs.map((d) => d.data() as PlayerStatDocument),
          partnerships: partSnap.docs.map(
            (d) => d.data() as PartnershipDocument
          ),
          fallOfWickets: fowSnap.docs.map(
            (d) => d.data() as FallOfWicketDocument
          ),
          deliveries: delSnap.docs
            .map((d) => d.data() as DeliveryDocument)
            .sort(
              (a, b) =>
                a.inning - b.inning ||
                a.over - b.over ||
                a.ballInOver - b.ballInOver
            ),
          impacts: impSnap.docs.map(
            (d) => d.data() as PlayerImpactDocument
          ),
        });
      } catch (e: any) {
        console.error('useFullMatch error:', e);
        setError(e.message || 'Failed to load match');
      }
      setLoading(false);
    })();
  }, [matchId]);

  return { fullMatch, loading, error };
}

/**
 * Fetch all match stats for a group (batch load for rankings/stats).
 * Returns matches paired with their stats subcollections.
 */
export function useGroupMatchesWithStats(groupId: string | undefined) {
  const [data, setData] = useState<
    { match: MatchDocument; stats: PlayerStatDocument[] }[]
  >([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!groupId) { setLoading(false); return; }
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const q = query(
          collection(db, 'matches'),
          where('groupId', '==', groupId),
          orderBy('matchDate', 'desc')
        );
        const snap = await getDocs(q);
        const results = await Promise.all(
          snap.docs.map(async (matchDoc) => {
            const statsSnap = await getDocs(
              collection(db, 'matches', matchDoc.id, 'stats')
            );
            return {
              match: matchDoc.data() as MatchDocument,
              stats: statsSnap.docs.map(
                (d) => d.data() as PlayerStatDocument
              ),
            };
          })
        );
        setData(results);
      } catch (e: any) {
        console.error('useGroupMatchesWithStats error:', e);
        setError(e.message || 'Failed to load match data');
      }
      setLoading(false);
    })();
  }, [groupId]);

  return { data, loading, error };
}

// ==================== Live in-progress matches for a group ====================

/**
 * Real-time listener for all live (in-progress) matches belonging to a group.
 */
export function useGroupLiveMatches(groupId: string | undefined) {
  const [matches, setMatches] = useState<InProgressMatchDocument[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!groupId) {
      setLoading(false);
      setMatches([]);
      return;
    }

    setLoading(true);
    const q = query(
      collection(db, 'in_progress_matches'),
      where('groupId', '==', groupId)
    );

    const FOUR_HOURS_MS = 4 * 60 * 60 * 1000;

    const unsubscribe = onSnapshot(
      q,
      (snap) => {
        const now = Date.now();
        setMatches(
          snap.docs
            .map((d) => ({ ...d.data(), matchId: d.id } as InProgressMatchDocument))
            .filter((m) => now - m.startedAt < FOUR_HOURS_MS)
        );
        setLoading(false);
      },
      (err) => {
        console.error('useGroupLiveMatches error:', err);
        setLoading(false);
      }
    );

    return () => unsubscribe();
  }, [groupId]);

  return { matches, loading };
}

// ==================== Shared match (live share code) ====================

export async function resolveShareCode(
  code: string
): Promise<SharedMatchDocument | null> {
  const snap = await getDoc(doc(db, 'shared_matches', code));
  if (snap.exists()) return snap.data() as SharedMatchDocument;
  return null;
}
