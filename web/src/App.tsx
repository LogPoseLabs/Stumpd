import { useEffect, useState } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { ensureAuth } from './firebase';
import Home from './pages/Home';
import GroupDashboard from './pages/GroupDashboard';
import MatchScorecard from './pages/MatchScorecard';
import LiveMatch from './pages/LiveMatch';

function App() {
  const [authReady, setAuthReady] = useState(false);

  useEffect(() => {
    ensureAuth().then(() => setAuthReady(true));
  }, []);

  if (!authReady) {
    return (
      <div className="min-h-screen flex items-center justify-center" style={{ background: 'var(--color-bg)' }}>
        <div className="text-center">
          <div className="w-10 h-10 border-4 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto mb-4" />
          <p style={{ color: 'var(--color-text-secondary)' }}>Loading Stumpd...</p>
        </div>
      </div>
    );
  }

  return (
    <BrowserRouter>
      <div className="min-h-screen" style={{ background: 'var(--color-bg)', color: 'var(--color-text)' }}>
        {/* Header */}
        <header
          className="sticky top-0 z-50 border-b backdrop-blur-md"
          style={{
            background: 'rgba(var(--color-surface-rgb, 255,255,255), 0.85)',
            borderColor: 'var(--color-border)',
          }}
        >
          <div className="max-w-5xl mx-auto px-4 h-14 flex items-center justify-between">
            <a href="/" className="flex items-center gap-2 no-underline" style={{ color: 'var(--color-text)' }}>
              <span className="text-xl font-bold tracking-tight">Stumpd</span>
              <span
                className="text-xs font-medium px-2 py-0.5 rounded-full"
                style={{ background: 'var(--color-primary)', color: '#fff' }}
              >
                VIEWER
              </span>
            </a>
          </div>
        </header>

        {/* Routes */}
        <main className="max-w-5xl mx-auto px-4 py-6">
          <Routes>
            <Route path="/" element={<Home />} />
            <Route path="/group/:groupId" element={<GroupDashboard />} />
            <Route path="/join/:inviteCode" element={<GroupDashboard />} />
            <Route path="/match/:matchId" element={<MatchScorecard />} />
            <Route path="/live/:matchId" element={<LiveMatch />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  );
}

export default App;
