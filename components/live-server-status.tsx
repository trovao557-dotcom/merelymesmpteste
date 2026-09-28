'use client';

import { useEffect, useState } from 'react';
import { Activity, Users } from 'lucide-react';
import type { ServerStatus } from '@/lib/community';
import type { Locale } from '@/lib/site-content';

export function LiveServerStatus({ compact = false, locale = 'en' }: { compact?: boolean; locale?: Locale }) {
  const [status, setStatus] = useState<ServerStatus | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    async function refresh() {
      try {
        const response = await fetch('/api/server-status', { signal: controller.signal });
        if (!response.ok) throw new Error('Unavailable');
        setStatus((await response.json()) as ServerStatus);
        setFailed(false);
      } catch (error) {
        if ((error as Error).name !== 'AbortError') setFailed(true);
      }
    }
    void refresh();
    const timer = window.setInterval(refresh, 60_000);
    return () => {
      controller.abort();
      window.clearInterval(timer);
    };
  }, []);

  const state = failed ? 'unknown' : status?.online ? 'online' : status ? 'offline' : 'loading';
  const pt = locale === 'pt';
  return (
    <div className={`live-status ${compact ? 'compact' : ''}`} aria-live="polite">
      <span className={`status-dot ${state}`} aria-hidden="true" />
      <div>
        <strong>
          {state === 'online' && (pt ? 'Servidor online' : 'Server online')}
          {state === 'offline' && (pt ? 'Servidor offline' : 'Server offline')}
          {state === 'loading' && (pt ? 'A verificar o servidor…' : 'Checking server…')}
          {state === 'unknown' && (pt ? 'Estado indisponível' : 'Status unavailable')}
        </strong>
        {status && (
          <span>
            <Users size={13} aria-hidden="true" /> {status.onlinePlayers}/{status.maxPlayers} {pt ? 'jogadores' : 'players'}
          </span>
        )}
      </div>
      {!compact && <Activity size={20} aria-hidden="true" />}
    </div>
  );
}
