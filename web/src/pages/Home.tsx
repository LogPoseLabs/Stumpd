import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

export default function Home() {
  const [code, setCode] = useState('');
  const [mode, setMode] = useState<'group' | 'live'>('group');
  const navigate = useNavigate();

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = code.trim().toUpperCase();
    if (!trimmed) return;

    if (mode === 'group') {
      navigate(`/join/${trimmed}`);
    } else {
      navigate(`/live/${trimmed}`);
    }
  };

  return (
    <div className="flex flex-col items-center pt-12 md:pt-24">
      {/* Hero */}
      <div className="text-center mb-10">
        <h1 className="text-4xl md:text-5xl font-extrabold tracking-tight mb-3">
          Stumpd <span style={{ color: 'var(--color-primary)' }}>Viewer</span>
        </h1>
        <p className="text-lg" style={{ color: 'var(--color-text-secondary)' }}>
          View cricket stats, rankings, and live match scores
        </p>
      </div>

      {/* Code Entry Card */}
      <div className="card w-full max-w-md">
        {/* Mode Toggle */}
        <div className="flex rounded-lg overflow-hidden mb-5" style={{ border: '1px solid var(--color-border)' }}>
          <button
            onClick={() => setMode('group')}
            className={`flex-1 py-2.5 text-sm font-semibold transition-colors ${
              mode === 'group' ? 'text-white' : ''
            }`}
            style={{
              background: mode === 'group' ? 'var(--color-primary)' : 'transparent',
              color: mode === 'group' ? '#fff' : 'var(--color-text-secondary)',
            }}
          >
            Group Stats
          </button>
          <button
            onClick={() => setMode('live')}
            className={`flex-1 py-2.5 text-sm font-semibold transition-colors ${
              mode === 'live' ? 'text-white' : ''
            }`}
            style={{
              background: mode === 'live' ? 'var(--color-primary)' : 'transparent',
              color: mode === 'live' ? '#fff' : 'var(--color-text-secondary)',
            }}
          >
            Live Match
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <label
            className="block text-sm font-medium mb-2"
            style={{ color: 'var(--color-text-secondary)' }}
          >
            {mode === 'group'
              ? 'Enter group invite code'
              : 'Enter live match share code'}
          </label>
          <input
            type="text"
            value={code}
            onChange={(e) => setCode(e.target.value.toUpperCase())}
            placeholder={mode === 'group' ? 'e.g. ABC123' : 'e.g. 123456'}
            maxLength={12}
            className="w-full px-4 py-3 rounded-lg text-lg font-mono tracking-widest text-center outline-none transition-all"
            style={{
              background: 'var(--color-bg)',
              border: '2px solid var(--color-border)',
              color: 'var(--color-text)',
            }}
            onFocus={(e) =>
              (e.target.style.borderColor = 'var(--color-primary)')
            }
            onBlur={(e) =>
              (e.target.style.borderColor = 'var(--color-border)')
            }
          />
          <button
            type="submit"
            disabled={!code.trim()}
            className="w-full mt-4 py-3 rounded-lg font-semibold text-white transition-colors disabled:opacity-50"
            style={{ background: 'var(--color-primary)' }}
          >
            {mode === 'group' ? 'View Group' : 'Watch Live'}
          </button>
        </form>
      </div>

      {/* Footer hint */}
      <p
        className="mt-8 text-sm text-center max-w-sm"
        style={{ color: 'var(--color-text-secondary)' }}
      >
        {mode === 'group'
          ? 'Enter the 6-character invite code from the Stumpd app to view group stats and rankings.'
          : 'Enter the 6-digit share code from the scorer to watch the match live.'}
      </p>
    </div>
  );
}
