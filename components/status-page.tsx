'use client';

import { useEffect, useState } from 'react';
import { Activity, ArchiveRestore, CheckCircle2, Clock3, Cloud, Server, ShoppingCart, TriangleAlert } from 'lucide-react';
import { InfoHeader } from '@/components/info-header';
import { SiteFooter } from '@/components/site-footer';
import { formatSiteDate, type Locale, type StatusPagePayload } from '@/lib/site-content';

type StoreState = 'checking' | 'operational' | 'unavailable';

export function StatusPage({ locale }: { locale: Locale }) {
  const pt = locale === 'pt';
  const [data, setData] = useState<StatusPagePayload | null>(null);
  const [store, setStore] = useState<StoreState>('checking');
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    async function refresh() {
      const [statusResult, storeResult] = await Promise.allSettled([
        fetch('/api/status-page', { signal: controller.signal }),
        fetch('/api/tip4serv-products', { signal: controller.signal }),
      ]);
      if (statusResult.status === 'fulfilled' && statusResult.value.ok) {
        setData(await statusResult.value.json() as StatusPagePayload);
        setFailed(false);
      } else if (!(statusResult.status === 'rejected' && statusResult.reason?.name === 'AbortError')) setFailed(true);
      setStore(storeResult.status === 'fulfilled' && storeResult.value.ok ? 'operational' : 'unavailable');
    }
    void refresh();
    const timer = window.setInterval(refresh, 60_000);
    return () => { controller.abort(); window.clearInterval(timer); };
  }, []);

  const minecraftState = data?.minecraft?.online ? 'operational' : data?.minecraft ? 'unavailable' : 'checking';
  const backupState: StoreState = !data ? 'checking' : data.backup?.healthy ? 'operational' : 'unavailable';
  const allOperational = minecraftState === 'operational' && store === 'operational' && backupState === 'operational' && !failed;
  const stateLabel = (state: StoreState) => state === 'checking'
    ? (pt ? 'A verificar' : 'Checking')
    : state === 'operational'
      ? (pt ? 'Operacional' : 'Operational')
      : (pt ? 'Indisponível' : 'Unavailable');

  const services = [
    { icon: Cloud, name: pt ? 'Website' : 'Website', state: 'operational' as StoreState, detail: pt ? 'Página pública e domínio' : 'Public pages and domain' },
    { icon: Activity, name: pt ? 'Dados da comunidade' : 'Community data', state: failed ? 'unavailable' as StoreState : data ? 'operational' as StoreState : 'checking' as StoreState, detail: pt ? 'Rankings, perfis e notícias' : 'Leaderboards, profiles and news' },
    { icon: ArchiveRestore, name: pt ? 'Recuperação dos dados' : 'Data recovery', state: backupState, detail: data?.backup?.lastSuccessfulDay ? `${pt ? 'Snapshot diário' : 'Daily snapshot'} · ${data.backup.retentionDays} ${pt ? 'dias' : 'days'} · ${data.backup.lastSuccessfulDay}` : (pt ? 'A verificar a última cópia' : 'Checking the latest snapshot') },
    { icon: Server, name: 'Minecraft Java', state: minecraftState as StoreState, detail: data?.minecraft ? `${data.minecraft.onlinePlayers}/${data.minecraft.maxPlayers} ${pt ? 'jogadores' : 'players'}` : serverFallback(pt) },
    { icon: ShoppingCart, name: pt ? 'Loja e preços' : 'Store and prices', state: store, detail: 'Tip4Serv' },
  ];

  return (
    <>
      <InfoHeader locale={locale} switchHref={pt ? '/status' : '/pt/status'} />
      <main className="info-shell status-page" lang={pt ? 'pt-PT' : 'en'}>
        <section className="status-hero">
          <div>
            <p className="eyebrow">{pt ? 'Transparência operacional' : 'Operational transparency'}</p>
            <h1>{pt ? 'Estado dos serviços' : 'Service status'}</h1>
            <p>{pt ? 'Estado atual do Minecraft, website, dados da comunidade e loja oficial.' : 'Current health of Minecraft, the website, community data and the official store.'}</p>
          </div>
          <div className={`overall-status ${allOperational ? 'online' : ''}`}>
            {allOperational ? <CheckCircle2 size={25} aria-hidden="true" /> : <TriangleAlert size={25} aria-hidden="true" />}
            <div><strong>{allOperational ? (pt ? 'Todos os serviços operacionais' : 'All systems operational') : (pt ? 'Alguns serviços precisam de atenção' : 'Some services need attention')}</strong><span>{pt ? 'Atualização automática a cada minuto' : 'Automatically refreshed every minute'}</span></div>
          </div>
        </section>

        <section className="service-list" aria-label={pt ? 'Serviços' : 'Services'}>
          {services.map(({ icon: Icon, name, state, detail }) => (
            <article key={name}>
              <Icon size={21} aria-hidden="true" /><div><strong>{name}</strong><span>{detail}</span></div>
              <span className={`service-state ${state}`}><i />{stateLabel(state)}</span>
            </article>
          ))}
        </section>

        <section className="uptime-section">
          <div className="section-heading">
            <div><p className="eyebrow">{pt ? 'Últimos 14 dias' : 'Last 14 days'}</p><h2>{pt ? 'Disponibilidade Minecraft' : 'Minecraft uptime'}</h2></div>
            <p>{pt ? 'Calculada a partir das verificações públicas reais do servidor.' : 'Calculated from real public checks of the server.'}</p>
          </div>
          <div className="uptime-chart" aria-label={pt ? 'Disponibilidade diária' : 'Daily uptime'}>
            {data?.days.map((day) => (
              <div key={day.date} title={`${day.date}: ${day.uptimePercent ?? 0}%`}>
                <span style={{ height: `${Math.max(4, day.uptimePercent ?? 0)}%` }} />
                <small>{new Date(`${day.date}T12:00:00Z`).toLocaleDateString(pt ? 'pt-PT' : 'en-GB', { day: '2-digit', month: 'short' })}</small>
              </div>
            ))}
            {!data?.days.length && <p className="empty-state">{pt ? 'A recolher histórico…' : 'Collecting history…'}</p>}
          </div>
          {data?.lastUpdatedAt && <p className="status-freshness"><Clock3 size={14} aria-hidden="true" /> {pt ? 'Última verificação real:' : 'Latest real check:'} {formatSiteDate(data.lastUpdatedAt, locale, true)}. {pt ? 'Não preenchemos períodos sem dados.' : 'Periods without data are not backfilled.'}</p>}
        </section>

        <section className="incident-section">
          <div className="section-heading">
            <div><p className="eyebrow">{pt ? 'Histórico' : 'History'}</p><h2>{pt ? 'Incidentes' : 'Incidents'}</h2></div>
            <p>{pt ? 'Interrupções detetadas e respetiva recuperação.' : 'Detected interruptions and their recovery.'}</p>
          </div>
          <div className="incident-list">
            {data?.incidents.map((incident) => (
              <article key={incident.id}>
                <span className={incident.endedAt ? 'resolved' : 'investigating'}>{incident.endedAt ? (pt ? 'Resolvido' : 'Resolved') : (pt ? 'Em análise' : 'Investigating')}</span>
                <div><strong>{pt ? 'Interrupção do servidor Minecraft' : incident.title}</strong><p><Clock3 size={14} aria-hidden="true" /> {formatSiteDate(incident.startedAt, locale, true)}{incident.endedAt ? ` — ${formatSiteDate(incident.endedAt, locale, true)}` : ''}</p></div>
              </article>
            ))}
            {data && !data.incidents.length && <article className="no-incidents"><CheckCircle2 size={20} aria-hidden="true" /><strong>{pt ? 'Nenhum incidente registado.' : 'No incidents recorded.'}</strong></article>}
            {!data && <p className="empty-state">{pt ? 'A carregar o histórico…' : 'Loading history…'}</p>}
          </div>
        </section>
      </main>
      <SiteFooter locale={locale} />
    </>
  );
}

function serverFallback(pt: boolean) {
  return pt ? 'A verificar o servidor' : 'Checking the server';
}
