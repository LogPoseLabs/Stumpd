import { useState, useRef, useEffect, useMemo } from 'react';

export type FilterPreset =
  | 'all'
  | 'last30days'
  | 'last3months'
  | 'thisYear'
  | 'custom'
  | string; // also supports 'date:YYYY-MM-DD' for specific match dates

export interface DateFilterState {
  preset: FilterPreset;
  startDate: string; // yyyy-MM-dd (for custom)
  endDate: string;   // yyyy-MM-dd (for custom)
}

/** Static presets in display order (dynamic match dates inserted after 'all'). */
const STATIC_PRESETS: { key: FilterPreset; label: string }[] = [
  { key: 'all', label: 'All Time' },
  // --- dynamic match-date entries will be injected here ---
  { key: 'last30days', label: 'Last 30 Days' },
  { key: 'last3months', label: 'Last 3 Months' },
  { key: 'thisYear', label: 'This Year' },
  { key: 'custom', label: 'Custom Range' },
];

interface Props {
  value: DateFilterState;
  onChange: (state: DateFilterState) => void;
  /** All match timestamps (epoch ms) — used to derive the 3 most recent dates. */
  matchDates: number[];
}

function toLocalDateStr(ts: number): string {
  const d = new Date(ts);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function formatNiceDate(dateStr: string): string {
  const d = new Date(dateStr + 'T00:00:00');
  return d.toLocaleDateString('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

export default function DateFilter({ value, onChange, matchDates }: Props) {
  const [open, setOpen] = useState(false);
  const [showCustom, setShowCustom] = useState(value.preset === 'custom');
  const dropdownRef = useRef<HTMLDivElement>(null);

  // Derive 3 most recent unique match dates
  const recentDates = useMemo(() => {
    const unique = [...new Set(matchDates.map(toLocalDateStr))];
    unique.sort((a, b) => b.localeCompare(a)); // newest first
    return unique.slice(0, 3);
  }, [matchDates]);

  // Build the full list of dropdown options
  const options = useMemo(() => {
    const result: { key: string; label: string; separator?: boolean }[] = [];
    // "All Time"
    result.push({ key: 'all', label: 'All Time' });
    // Dynamic match-date entries
    if (recentDates.length > 0) {
      result.push({ key: '__sep_dates', label: '', separator: true });
      for (const ds of recentDates) {
        result.push({ key: `date:${ds}`, label: formatNiceDate(ds) });
      }
    }
    // The rest of the static presets (skip 'all', already added)
    result.push({ key: '__sep_ranges', label: '', separator: true });
    for (const sp of STATIC_PRESETS) {
      if (sp.key === 'all') continue;
      result.push({ key: sp.key, label: sp.label });
    }
    return result;
  }, [recentDates]);

  // Close dropdown on outside click
  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    if (open) document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, [open]);

  const handleSelect = (preset: string) => {
    if (preset === 'custom') {
      setShowCustom(true);
      onChange({ ...value, preset: 'custom' });
    } else {
      setShowCustom(false);
      onChange({ preset, startDate: '', endDate: '' });
    }
    setOpen(false);
  };

  // Resolve display label
  const displayLabel = useMemo(() => {
    if (value.preset === 'custom' && value.startDate && value.endDate) {
      return `${formatNiceDate(value.startDate)} – ${formatNiceDate(value.endDate)}`;
    }
    if (value.preset.startsWith('date:')) {
      const ds = value.preset.replace('date:', '');
      return formatNiceDate(ds);
    }
    const found = STATIC_PRESETS.find((s) => s.key === value.preset);
    return found?.label ?? 'All Time';
  }, [value]);

  return (
    <div className="flex flex-wrap gap-3 items-start">
      {/* Dropdown */}
      <div className="relative" ref={dropdownRef}>
        <button
          onClick={() => setOpen(!open)}
          className="flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium transition-colors"
          style={{
            background: 'var(--color-surface)',
            border: '1px solid var(--color-border)',
            color: 'var(--color-text)',
          }}
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
            <line x1="16" y1="2" x2="16" y2="6" />
            <line x1="8" y1="2" x2="8" y2="6" />
            <line x1="3" y1="10" x2="21" y2="10" />
          </svg>
          {displayLabel}
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="6 9 12 15 18 9" />
          </svg>
        </button>

        {open && (
          <div
            className="absolute left-0 top-full mt-1 z-50 min-w-[200px] rounded-lg shadow-lg py-1"
            style={{
              background: 'var(--color-surface)',
              border: '1px solid var(--color-border)',
            }}
          >
            {options.map((opt) => {
              if (opt.separator) {
                return (
                  <div
                    key={opt.key}
                    className="my-1 mx-3"
                    style={{ borderTop: '1px solid var(--color-border)' }}
                  />
                );
              }
              const isActive = value.preset === opt.key;
              return (
                <button
                  key={opt.key}
                  onClick={() => handleSelect(opt.key)}
                  className="w-full text-left px-4 py-2.5 text-sm transition-colors hover:opacity-80 flex items-center gap-2"
                  style={{
                    background: isActive ? 'var(--color-primary)' : 'transparent',
                    color: isActive ? '#fff' : 'var(--color-text)',
                  }}
                >
                  {opt.label}
                </button>
              );
            })}
          </div>
        )}
      </div>

      {/* Custom date range inputs */}
      {showCustom && value.preset === 'custom' && (
        <div className="flex gap-2 items-center flex-wrap">
          <input
            type="date"
            value={value.startDate}
            onChange={(e) => onChange({ ...value, startDate: e.target.value })}
            className="px-2.5 py-2 rounded-lg text-sm outline-none"
            style={{
              background: 'var(--color-surface)',
              border: '1px solid var(--color-border)',
              color: 'var(--color-text)',
            }}
          />
          <span className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>to</span>
          <input
            type="date"
            value={value.endDate}
            onChange={(e) => onChange({ ...value, endDate: e.target.value })}
            className="px-2.5 py-2 rounded-lg text-sm outline-none"
            style={{
              background: 'var(--color-surface)',
              border: '1px solid var(--color-border)',
              color: 'var(--color-text)',
            }}
          />
        </div>
      )}
    </div>
  );
}

/**
 * Apply the date filter to a list of matches (sorted newest first).
 */
export function applyDateFilter<T extends { matchDate: number }>(
  matches: T[],
  filter: DateFilterState
): T[] {
  const now = new Date();

  // Handle specific match-date filter: "date:YYYY-MM-DD"
  if (filter.preset.startsWith('date:')) {
    const ds = filter.preset.replace('date:', '');
    const dayStart = new Date(ds + 'T00:00:00').getTime();
    const dayEnd = new Date(ds + 'T23:59:59.999').getTime();
    return matches.filter((m) => m.matchDate >= dayStart && m.matchDate <= dayEnd);
  }

  switch (filter.preset) {
    case 'last30days': {
      const cutoff = new Date(now);
      cutoff.setDate(cutoff.getDate() - 30);
      cutoff.setHours(0, 0, 0, 0);
      return matches.filter((m) => m.matchDate >= cutoff.getTime());
    }
    case 'last3months': {
      const cutoff = new Date(now);
      cutoff.setMonth(cutoff.getMonth() - 3);
      cutoff.setHours(0, 0, 0, 0);
      return matches.filter((m) => m.matchDate >= cutoff.getTime());
    }
    case 'thisYear': {
      const startOfYear = new Date(now.getFullYear(), 0, 1).getTime();
      return matches.filter((m) => m.matchDate >= startOfYear);
    }
    case 'custom': {
      const startMs = filter.startDate
        ? new Date(filter.startDate + 'T00:00:00').getTime()
        : 0;
      const endMs = filter.endDate
        ? new Date(filter.endDate + 'T23:59:59.999').getTime()
        : Infinity;
      return matches.filter(
        (m) => m.matchDate >= startMs && m.matchDate <= endMs
      );
    }
    case 'all':
    default:
      return matches;
  }
}
