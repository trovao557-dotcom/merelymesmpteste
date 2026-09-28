'use client';

import Link from '@/components/site-link';
import { useEffect, useState } from 'react';
import { Activity, ArrowRight, Clock3, MessageCircle, Trophy, Users } from 'lucide-react';
import { InfoHeader } from '@/components/info-header';
import { SiteFooter } from '@/components/site-footer';
import { track } from '@/lib/analytics';
import type { CommunityPayload } from '@/lib/community';
import { formatObservedMinutes } from '@/lib/community';
import { localePath, localised, type Locale } from '@/lib/site-content';

type DiscordPayload = { inviteUrl: string; guildId: string; name: string | null; online: number | null };

export function CommunityHub({ locale }: { locale: Locale }) {
  const pt = locale === 'pt';
  const [data, setData] = useState<CommunityPayload | null>(null);
  const [discord, setDiscord] = useState<DiscordPayload | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    async function refresh() {
      const [communityResult, discordResult] = await Promise.allSettled([
        fetch('/api/community', { signal: controller.signal }),
        fetch('/api/discord', { signal: controller.signal }),
      ]);
      if (communityResult.status === 'fulfilled' && communityResult.value.ok) {
        setData(await communityResult.value.json() as CommunityPayload);
        setFailed(false);
      } else if (!(communityResult.status === 'rejected' && communityResult.reason?.name === 'AbortError')) setFailed(true);
      if (discordResult.status === 'fulfilled' && discordResult.value.ok) setDiscord(await discordResult.value.json() as DiscordPayload);
    }
    void refresh();
    const timer = window.setInterval(refresh, 60_000);
    return () => { controller.abort(); window.clearInterval(timer); };
  }, []);

  return (
    <>
      <InfoHeader locale={locale} switchHref={pt ? '/community' : '/pt/community'} />
      <main className="info-shell community-page" lang={pt ? 'pt-PT' : 'en'}>
        <section className="community-hero">
          <div>
            <p className="eyebrow">{data ? localised(locale, data.season.label, data.season.labelPt) : (pt ? 'Primeira temporada · UE' : 'First Season · EU')}</p>
            <h1>{data ? localised(locale, data.season.name, data.season.namePt) : (pt ? 'Temporada 1' : 'Season 1')}</h1>
            <p>{data ? localised(locale, data.season.description, data.season.descriptionPt) : (pt ? 'A carregar dados reais da comunidade…' : 'Loading live community data…')}</p>
          </div>
          <div className={`community-status ${data?.status?.online ? 'online' : ''}`}>
            <span className="status-dot" />
            <div><small>{pt ? 'Servidor em direto' : 'Live server'}</small><strong>{data?.status?.online ? 'Online' : failed ? (pt ? 'Indisponível' : 'Unavailable') : (pt ? 'A verificar…' : 'Checking…')}</strong></div>
            {data?.status && <p>{data.status.onlinePlayers}<span>/ {data.status.maxPlayers} {pt ? 'jogadores' : 'players'}</span></p>}
          </div>
        </section>

        {data && (
          <section className="community-summary" aria-label={pt ? 'Resumo da comunidade' : 'Community summary'}>
            <article><Users size={20} aria-hidden="true" /><span>{pt ? 'Jogadores registados' : 'Tracked players'}</span><strong>{data.summary.trackedPlayers}</strong></article>
            <article><Activity size={20} aria-hidden="true" /><span>{pt ? 'Disponibilidade observada' : 'Observed uptime'}</span><strong>{data.summary.uptimePercent === null ? '—' : `${data.summary.uptimePercent}%`}</strong></article>
            <article><Clock3 size={20} aria-hidden="true" /><span>{pt ? 'Monitorização desde' : 'Tracking since'}</span><strong>{new Date(data.summary.trackingStartedAt).toLocaleDateString(pt ? 'pt-PT' : 'en-GB')}</strong></article>
          </section>
        )}

        <section className="leaderboard-section" id="leaderboard">
          <div className="section-heading">
            <div><p className="eyebrow">{pt ? 'Registos reais' : 'Live records'}</p><h2>{pt ? 'Ranking de atividade' : 'Activity leaderboard'}</h2></div>
            <p>{pt ? 'Tempo online observado desde o início da monitorização. Estatísticas de combate só aparecem depois da recolha no servidor ser verificada.' : 'Observed online time since monitoring began. Combat rankings only appear after verified server-side collection is active.'}</p>
          </div>
          <div className="leaderboard-card">
            {!data && !failed && <p className="empty-state">{pt ? 'A carregar os primeiros registos…' : 'Loading the first records…'}</p>}
            {failed && !data && <p className="empty-state">{pt ? 'As estatísticas estão temporariamente indisponíveis.' : 'Community statistics are temporarily unavailable.'}</p>}
            {data?.leaderboard.length === 0 && <p className="empty-state">{pt ? 'Ainda não foi observado nenhum jogador. Sê o primeiro.' : 'No players have been observed yet. Be the first to join.'}</p>}
            {data && data.leaderboard.length > 0 && (
              <div className="leaderboard-table" aria-label={pt ? 'Ranking de atividade' : 'Activity leaderboard'}>
                <div className="leaderboard-row heading"><span>#</span><span>{pt ? 'Jogador' : 'Player'}</span><span>{pt ? 'Observado' : 'Observed'}</span><span>{pt ? 'Estado' : 'Status'}</span></div>
                {data.leaderboard.map((player) => (
                  <Link className="leaderboard-row" href={localePath(locale, `/players/${player.uuid}`)} key={player.uuid}>
                    <strong>{player.rank <= 3 ? <Trophy size={16} aria-label={`Rank ${player.rank}`} /> : player.rank}</strong>
                    <span>{player.name}</span><span>{formatObservedMinutes(player.observedMinutes)}</span>
                    <span className={player.online ? 'player-online' : ''}>{player.online ? 'Online' : 'Offline'}</span>
                  </Link>
                ))}
              </div>
            )}
          </div>
          {data && <p className="data-note">{pt ? 'O ranking conta o tempo observado online; não é um ranking de combate.' : data.note}</p>}
        </section>

        <section className="discord-section" id="discord">
          <div><MessageCircle size={34} aria-hidden="true" /><p className="eyebrow">Discord</p><h2>{pt ? 'A comunidade continua fora do jogo.' : 'The community continues outside the game.'}</h2><p>{pt ? 'Anúncios, suporte e conversas da comunidade no espaço oficial.' : 'Announcements, support and community conversations in the official space.'}</p></div>
          <div className="discord-card">
            <span>{discord?.name || 'MerelyMeSMP Discord'}</span>
            {typeof discord?.online === 'number' && <strong><i />{discord.online} {pt ? 'membros online' : 'members online'}</strong>}
            {discord?.inviteUrl ? (
              <a className="button button-primary" href="/discord" target="_blank" rel="noreferrer" onClick={() => track('discord_click')}>{pt ? 'Entrar no Discord' : 'Join Discord'} <ArrowRight size={17} aria-hidden="true" /></a>
            ) : <p>{pt ? 'O convite oficial será publicado aqui assim que for disponibilizado.' : 'The official invite will appear here as soon as it is available.'}</p>}
          </div>
        </section>

        <section className="community-news-cta">
          <div><p className="eyebrow">{pt ? 'Atualizações oficiais' : 'Official updates'}</p><h2>{pt ? 'Não percas uma mudança.' : 'Never miss a change.'}</h2></div>
          <Link className="button button-ghost" href={localePath(locale, '/news')}>{pt ? 'Ver notícias' : 'View news'} <ArrowRight size={17} aria-hidden="true" /></Link>
        </section>
      </main>
      <SiteFooter locale={locale} />
    </>
  );
}
