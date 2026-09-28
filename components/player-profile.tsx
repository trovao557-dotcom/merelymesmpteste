'use client';

import Link from '@/components/site-link';
import { useParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import { Activity, CalendarDays, Clock3, Eye, Trophy } from 'lucide-react';
import { InfoHeader } from '@/components/info-header';
import { SiteFooter } from '@/components/site-footer';
import type { CommunityPlayer } from '@/lib/community';
import { formatObservedMinutes } from '@/lib/community';
import { localePath, localised, type Locale } from '@/lib/site-content';

type ProfilePayload = {
  player: CommunityPlayer;
  season: { name: string; namePt: string; label: string; labelPt: string };
  trackingStartedAt: string;
  note: string;
};

export function PlayerProfile({ locale }: { locale: Locale }) {
  const pt = locale === 'pt';
  const params = useParams<{ uuid: string }>();
  const [profile, setProfile] = useState<ProfilePayload | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    fetch(`/api/players/${encodeURIComponent(params.uuid)}`, { signal: controller.signal })
      .then((response) => response.ok ? response.json() as Promise<ProfilePayload> : Promise.reject())
      .then(setProfile)
      .catch((error) => { if (error?.name !== 'AbortError') setFailed(true); });
    return () => controller.abort();
  }, [params.uuid]);

  return (
    <>
      <InfoHeader locale={locale} switchHref={pt ? `/players/${params.uuid}` : `/pt/players/${params.uuid}`} />
      <main className="info-shell profile-page" lang={pt ? 'pt-PT' : 'en'}>
        {!profile && !failed && <p className="empty-state">{pt ? 'A carregar o perfil…' : 'Loading player profile…'}</p>}
        {failed && <div className="profile-missing"><h1>{pt ? 'Jogador não encontrado' : 'Player not found'}</h1><p>{pt ? 'Este jogador ainda não foi observado pelo monitor público.' : 'This player has not been observed by the public monitor yet.'}</p><Link className="button button-primary" href={localePath(locale, '/community')}>{pt ? 'Voltar ao ranking' : 'Back to leaderboard'}</Link></div>}
        {profile && (
          <>
            <section className="profile-hero">
              <div className="player-avatar" aria-hidden="true">{profile.player.name.slice(0, 2).toUpperCase()}</div>
              <div><p className="eyebrow">{localised(locale, profile.season.label, profile.season.labelPt)}</p><h1>{profile.player.name}</h1><span className={profile.player.online ? 'player-online' : ''}>{profile.player.online ? (pt ? 'Online agora' : 'Online now') : 'Offline'}</span></div>
              <strong>#{profile.player.rank || '—'}</strong>
            </section>
            <section className="profile-stats">
              <article><Clock3 aria-hidden="true" /><span>{pt ? 'Tempo observado' : 'Observed online'}</span><strong>{formatObservedMinutes(profile.player.observedMinutes)}</strong></article>
              <article><Eye aria-hidden="true" /><span>{pt ? 'Verificações' : 'Checks seen'}</span><strong>{profile.player.checksSeen}</strong></article>
              <article><CalendarDays aria-hidden="true" /><span>{pt ? 'Visto primeiro em' : 'First seen'}</span><strong>{new Date(profile.player.firstSeenAt).toLocaleDateString(pt ? 'pt-PT' : 'en-GB')}</strong></article>
              <article><Activity aria-hidden="true" /><span>{pt ? 'Visto pela última vez' : 'Last seen'}</span><strong>{new Date(profile.player.lastSeenAt).toLocaleString(pt ? 'pt-PT' : 'en-GB')}</strong></article>
            </section>
            <section className="combat-coming">
              <Trophy size={25} aria-hidden="true" />
              <div><h2>{pt ? 'Estatísticas de combate verificadas mais tarde' : 'Verified combat statistics next'}</h2><p>{pt ? 'Kills, mortes, K/D e recordes com maça só serão apresentados depois de o coletor do servidor Paper ser instalado e verificado. Nunca mostramos números falsos.' : 'Kills, deaths, K/D and mace records will appear only after the Paper server collector is installed and verified. No mock numbers are shown.'}</p></div>
            </section>
            <p className="data-note">{pt ? 'A atividade é observada através de pings públicos. Em servidores maiores, a lista visível pode ser apenas uma amostra.' : profile.note}</p>
          </>
        )}
      </main>
      <SiteFooter locale={locale} />
    </>
  );
}
