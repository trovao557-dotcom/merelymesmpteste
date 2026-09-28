'use client';

import Image from 'next/image';
import Link from '@/components/site-link';
import { useEffect, useState } from 'react';
import {
  ArrowRight,
  BookOpenCheck,
  CalendarDays,
  Check,
  CheckCheck,
  Coins,
  Copy,
  ExternalLink,
  Gift,
  LockKeyhole,
  LifeBuoy,
  Server,
  ShieldCheck,
  ShoppingBag,
  Swords,
  Trophy,
  UserRoundCheck,
  Zap,
} from 'lucide-react';
import { track } from '@/lib/analytics';
import { localePath, localised, type Locale, type SiteContent } from '@/lib/site-content';
import type { Tip4ServPrice, Tip4ServPrices } from '@/lib/tip4serv-products';
import { LiveServerStatus } from '@/components/live-server-status';
import { SiteFooter } from '@/components/site-footer';
import { SiteHeader } from '@/components/site-header';

const serverIp = 'merelymesmp.com';

const ranks = [
  {
    name: 'Knight', slug: 'knight-rank', price: 4.99, image: '/images/rank-knight.webp', tone: 'knight',
    href: 'https://merelymesmpstore.tip4serv.com/product/knight-rank',
    tagline: ['Start with an edge', 'Começa com vantagem'],
    perks: [
      ['2 virtual vaults', '2 cofres virtuais'], ['3 homes', '3 casas'],
      ['14 auction listings', '14 anúncios no leilão'], ['Exclusive kit and tag', 'Kit e tag exclusivos'],
    ],
  },
  {
    name: 'Warrior', slug: 'warrior-rank', price: 8.99, image: '/images/rank-warrior.webp', tone: 'warrior',
    href: 'https://merelymesmpstore.tip4serv.com/product/warrior-rank',
    tagline: ['More room to grow', 'Mais espaço para crescer'],
    perks: [
      ['3 virtual vaults', '3 cofres virtuais'], ['4 homes', '4 casas'],
      ['18 auction listings', '18 anúncios no leilão'], ['Exclusive kit and tag', 'Kit e tag exclusivos'],
    ],
  },
  {
    name: 'Macer', slug: 'macer-rank', price: 14.99, image: '/images/rank-macer.webp', tone: 'macer',
    href: 'https://merelymesmpstore.tip4serv.com/product/macer-rank',
    tagline: ['Built to dominate', 'Criado para dominar'],
    perks: [
      ['2× more keys', '2× mais chaves'], ['4 virtual vaults', '4 cofres virtuais'],
      ['5 homes', '5 casas'], ['22 auction listings', '22 anúncios no leilão'],
    ],
  },
  {
    name: 'Prime', slug: 'prime-kit', price: 19.99, image: '/images/rank-prime.webp', tone: 'prime', featured: true,
    href: 'https://merelymesmpstore.tip4serv.com/product/prime-kit',
    tagline: ['The full experience', 'A experiência completa'],
    perks: [
      ['2× more keys', '2× mais chaves'], ['5 virtual vaults', '5 cofres virtuais'],
      ['7 homes', '7 casas'], ['30 auction listings', '30 anúncios no leilão'],
    ],
  },
];

const pointPacks = [
  { amount: '2 000', slug: '2000-points', price: 1.5, href: 'https://merelymesmpstore.tip4serv.com/product/2000-points' },
  { amount: '10 000', slug: '10000-points', price: 4.99, href: 'https://merelymesmpstore.tip4serv.com/product/10000-points' },
  { amount: '20 000', slug: '10000-points-1', price: 9.97, href: 'https://merelymesmpstore.tip4serv.com/product/10000-points-1' },
  { amount: '30 000', bonus: ['+5,000 bonus', '+5 000 de bónus'], slug: '35000-points-5k-points', price: 14.96, href: 'https://merelymesmpstore.tip4serv.com/product/35000-points-5k-points' },
];

const keyAll = { slug: 'keyall-to-everyone', price: 2.99 };

const features = [
  { icon: Swords, number: '01', title: ['No-nonsense Mace PvP', 'Mace PvP sem distrações'], text: ['Fast, competitive combat where every hit, decision and second counts.', 'Combate rápido e competitivo onde cada golpe, decisão e segundo contam.'] },
  { icon: Gift, number: '02', title: ['Free spawn rewards', 'Recompensas grátis no spawn'], text: ['Head to /spawn, find the Black Shulker and pick a free reward.', 'Vai a /spawn, encontra o Black Shulker e escolhe uma recompensa grátis.'] },
  { icon: Coins, number: '03', title: ['Dynamic economy', 'Economia dinâmica'], text: ['Spend points on spawners, tags, chat colors, resources and special keys.', 'Usa pontos em spawners, tags, cores no chat, recursos e chaves especiais.'] },
];

function choose(locale: Locale, pair: readonly string[]) {
  return locale === 'pt' ? pair[1] : pair[0];
}

export function StoreHome({ locale }: { locale: Locale }) {
  const pt = locale === 'pt';
  const money = new Intl.NumberFormat(pt ? 'pt-PT' : 'en-IE', { style: 'currency', currency: 'EUR' });
  const [copied, setCopied] = useState(false);
  const [livePrices, setLivePrices] = useState<Tip4ServPrices>({});
  const [content, setContent] = useState<SiteContent | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    function load() {
      void Promise.allSettled([
        fetch('/api/tip4serv-products', { signal: controller.signal })
          .then((response) => response.ok ? response.json() : Promise.reject())
          .then((data: { prices?: Tip4ServPrices }) => setLivePrices(data.prices ?? {})),
        fetch('/api/content', { signal: controller.signal })
          .then((response) => response.ok ? response.json() : Promise.reject())
          .then((data: SiteContent) => setContent(data)),
      ]);
    }
    load();
    const interval = window.setInterval(load, 5 * 60_000);
    return () => { window.clearInterval(interval); controller.abort(); };
  }, []);

  function productPrice(slug: string, fallback: number): Tip4ServPrice {
    return livePrices[slug] ?? { price: fallback, oldPrice: null, percentOff: 0 };
  }

  async function copyServerIp() {
    try {
      await navigator.clipboard.writeText(serverIp);
      setCopied(true);
      track('copy_ip');
      window.setTimeout(() => setCopied(false), 1800);
    } catch {
      window.prompt(pt ? 'Copia o IP do servidor:' : 'Copy the server IP:', serverIp);
    }
  }

  const keyAllPrice = productPrice(keyAll.slug, keyAll.price);
  const announcement = content?.announcement;
  const faq = content?.faqs || [];
  const essentialLinks = [
    { icon: BookOpenCheck, title: pt ? 'Centro de ajuda' : 'Help centre', text: pt ? 'Respostas verificadas num só lugar.' : 'Verified answers in one place.', href: localePath(locale, '/help') },
    { icon: Server, title: pt ? 'Estado dos serviços' : 'Service status', text: pt ? 'Minecraft, website, dados e loja.' : 'Minecraft, website, data and store.', href: localePath(locale, '/status') },
    { icon: ShieldCheck, title: pt ? 'Regras do servidor' : 'Server rules', text: pt ? 'Jogo justo, segurança e moderação.' : 'Fair play, safety and moderation.', href: localePath(locale, '/rules') },
    { icon: UserRoundCheck, title: pt ? 'Informação para pais' : 'Information for parents', text: pt ? 'Compras, segurança e privacidade.' : 'Purchases, safety and privacy.', href: localePath(locale, '/parents') },
    { icon: LifeBuoy, title: pt ? 'Suporte privado' : 'Private support', text: pt ? 'Compras, denúncias e recursos.' : 'Purchases, reports and appeals.', href: localePath(locale, '/support') },
    { icon: ShoppingBag, title: pt ? 'Conta da loja' : 'Store account', text: pt ? 'Consulta a informação da tua conta Tip4Serv.' : 'Review your Tip4Serv account information.', href: 'https://merelymesmpstore.tip4serv.com/login', external: true },
  ];

  return (
    <main lang={pt ? 'pt-PT' : 'en'}>
      <SiteHeader locale={locale} overlay switchHref={pt ? '/' : '/pt'} />

      <section className="hero" id="top">
        <div className="hero-backdrop" aria-hidden="true" />
        <div className="hero-content">
          {announcement?.enabled && (
            <Link className="announcement-chip" href={localePath(locale, announcement.ctaHref)}>
              <strong>{localised(locale, announcement.title, announcement.titlePt)}</strong>
              <span>{localised(locale, announcement.message, announcement.messagePt)}</span>
              <ArrowRight size={15} aria-hidden="true" />
            </Link>
          )}
          <p className="eyebrow"><span /> Minecraft Java · +1.21</p>
          <h1>{pt ? 'Entra. Luta.' : 'Enter. Fight.'}<br /><em>{pt ? 'Deixa a tua marca.' : 'Leave your mark.'}</em></h1>
          <p className="hero-copy">
            {pt ? 'PvP direto, recompensas grátis e uma economia criada para jogadores que querem evoluir depressa.' : 'No-nonsense PvP, free rewards and an economy built for players who want to level up fast.'}
          </p>
          <div className="hero-actions">
            <a className="button button-primary" href="#ranks">
              {pt ? 'Explorar a loja' : 'Explore the store'} <ArrowRight size={18} aria-hidden="true" />
            </a>
            <button className="button button-ghost" type="button" onClick={copyServerIp}>
              {copied ? <CheckCheck size={18} aria-hidden="true" /> : <Copy size={18} aria-hidden="true" />}
              {copied ? (pt ? 'IP copiado' : 'IP copied') : (pt ? 'Copiar IP' : 'Copy IP')}
            </button>
          </div>
          <div className="server-address" id="server">
            <span><Server size={13} aria-hidden="true" /> {pt ? 'IP DO SERVIDOR' : 'SERVER IP'}</span>
            <strong>{serverIp}</strong>
          </div>
          <output className="copy-status" aria-live="polite">{copied ? (pt ? 'Endereço copiado.' : 'Server address copied.') : ''}</output>
          <LiveServerStatus compact locale={locale} />
        </div>
        <div className="hero-mark" aria-hidden="true">
          <div className="mark-glow" />
          <Image src="/images/logo.png" alt="" width={300} height={300} priority />
        </div>
      </section>

      <section className="trust-bar" aria-label={pt ? 'Vantagens da loja' : 'Store benefits'}>
        <p><Zap size={18} aria-hidden="true" /><span><strong>{pt ? 'Entregue no jogo' : 'Delivered in-game'}</strong>{pt ? ' depois da confirmação' : ' after confirmation'}</span></p>
        <p><ShieldCheck size={18} aria-hidden="true" /><span><strong>{pt ? 'Pagamento seguro' : 'Secure checkout'}</strong>{pt ? ' através da Tip4Serv' : ' powered by Tip4Serv'}</span></p>
        <p><LockKeyhole size={18} aria-hidden="true" /><span><strong>{pt ? 'Discord e conta opcionais' : 'Discord and account optional'}</strong>{pt ? ' no checkout' : ' at checkout'}</span></p>
      </section>

      <section className="shop-section" id="ranks">
        <div className="section-heading">
          <div><p className="eyebrow">{pt ? 'Escolhe o teu nível' : 'Choose your level'}</p><h2>{pt ? 'Ranks do servidor' : 'Server ranks'}</h2></div>
          <p>{pt ? 'Vantagens permanentes que acompanham o teu progresso. Os preços correspondem ao checkout oficial da Tip4Serv.' : 'Permanent perks that grow with your progress. Prices match the official Tip4Serv checkout.'}</p>
        </div>
        <div className="rank-grid">
          {ranks.map((rank) => {
            const price = productPrice(rank.slug, rank.price);
            return (
              <article className={`rank-card ${rank.tone}${rank.featured ? ' featured' : ''}`} key={rank.name}>
                {rank.featured && <span className="popular">{pt ? 'Melhores vantagens' : 'Best perks'}</span>}
                {price.percentOff > 0 && <span className="discount-pill">−{price.percentOff}%</span>}
                <div className="rank-art"><Image src={rank.image} alt={`${rank.name} rank`} width={400} height={220} /></div>
                <div className="rank-body">
                  <p>{choose(locale, rank.tagline)}</p><h3>{rank.name}</h3>
                  <ul>{rank.perks.map((perk) => <li key={perk[0]}><Check size={14} aria-hidden="true" />{choose(locale, perk)}</li>)}</ul>
                  <div className="rank-footer">
                    <p>{price.oldPrice !== null && <del>{money.format(price.oldPrice)}</del>}<strong>{money.format(price.price)}</strong></p>
                    <a href={rank.href} target="_blank" rel="noreferrer" onClick={() => track('store_click')}>
                      {pt ? 'Escolher' : 'Choose'} <ExternalLink size={14} aria-hidden="true" />
                    </a>
                  </div>
                </div>
              </article>
            );
          })}
        </div>
        <p className="checkout-note"><ShieldCheck size={15} aria-hidden="true" /> {pt ? 'As compras abrem na loja Tip4Serv oficial para manter o pagamento e a entrega protegidos.' : 'Purchases open in the official Tip4Serv store to keep payment and delivery protected.'}</p>
      </section>

      <section className="points-section" id="points">
        <div className="points-inner">
          <div className="section-heading">
            <div><p className="eyebrow">{pt ? 'A tua moeda, as tuas escolhas' : 'Your currency, your choices'}</p><h2>{pt ? 'Carrega pontos' : 'Load up on points'}</h2></div>
            <p>{pt ? 'Usa-os no jogo em spawners, tags, cores no chat, recursos e chaves.' : 'Spend them in-game on spawners, tags, chat colors, resources and keys.'}</p>
          </div>
          <div className="points-layout">
            <div className="point-grid">
              {pointPacks.map((pack) => {
                const price = productPrice(pack.slug, pack.price);
                return (
                  <a className="point-card" href={pack.href} target="_blank" rel="noreferrer" key={pack.amount} onClick={() => track('store_click')}>
                    {price.percentOff > 0 && <span className="discount-pill">−{price.percentOff}%</span>}
                    <div className="point-image" aria-hidden="true"><Image src="/images/points.png" alt="" width={86} height={86} /></div>
                    <div><span>{pt ? 'Pontos' : 'Points'}</span><h3>{pack.amount}</h3>{pack.bonus && <mark>{choose(locale, pack.bonus)}</mark>}</div>
                    <p>{price.oldPrice !== null && <del>{money.format(price.oldPrice)}</del>}<strong>{money.format(price.price)}</strong></p>
                    <ArrowRight size={19} aria-hidden="true" />
                  </a>
                );
              })}
            </div>
            <article className="key-card">
              <span className="key-label">{pt ? 'Recompensa global' : 'Global reward'}</span>
              {keyAllPrice.percentOff > 0 && <span className="discount-pill">−{keyAllPrice.percentOff}%</span>}
              <Image src="/images/keyall.webp" alt="Global server key" width={330} height={330} />
              <div>
                <p className="eyebrow">{pt ? 'Uma chave para todos' : 'One key for everyone'}</p><h3>KeyAll</h3>
                <p>{pt ? 'Ativa uma recompensa global para todos os jogadores. No spawn, abre o Black Shulker e escolhe o teu prémio.' : 'Trigger a global reward for every player. At spawn, open the Black Shulker and choose your prize.'}</p>
                <div className="key-buy">
                  <p>{keyAllPrice.oldPrice !== null && <del>{money.format(keyAllPrice.oldPrice)}</del>}<strong>{money.format(keyAllPrice.price)}</strong></p>
                  <a href="https://merelymesmpstore.tip4serv.com/product/keyall-to-everyone" target="_blank" rel="noreferrer" onClick={() => track('store_click')}>
                    {pt ? 'Comprar' : 'Buy now'} <ExternalLink size={14} aria-hidden="true" />
                  </a>
                </div>
              </div>
            </article>
          </div>
        </div>
      </section>

      <section className="community-preview">
        <div>
          <p className="eyebrow">{pt ? 'Lançamento · 07/09/2026' : 'Launch · 7 September 2026'}</p>
          <h2>{pt ? 'Todos os nomes começam do zero.' : 'Every name starts at zero.'}</h2>
          <p>{pt ? 'Acompanha o estado do servidor, o ranking de atividade, perfis e notícias oficiais.' : 'Follow the live server, activity leaderboard, player profiles and official news.'}</p>
          <Link className="button button-primary" href={localePath(locale, '/community')}>
            {pt ? 'Abrir a comunidade' : 'Open the community hub'} <ArrowRight size={18} aria-hidden="true" />
          </Link>
        </div>
        <div className="community-preview-cards">
          <article><Trophy size={23} aria-hidden="true" /><span>{pt ? 'Registos reais' : 'Live records'}</span><strong>{pt ? 'Ranking e perfis' : 'Leaderboard & profiles'}</strong></article>
          <article><CalendarDays size={23} aria-hidden="true" /><span>{pt ? 'Atualizações oficiais' : 'Official updates'}</span><strong>{pt ? 'Notícias' : 'News'}</strong></article>
        </div>
      </section>

      <section className="experience-section">
        <div className="experience-intro">
          <p className="eyebrow">{pt ? 'PvP puro. Sem excessos.' : 'Pure PvP. No excess.'}</p>
          <h2>{pt ? 'O servidor não espera por ninguém.' : 'The server waits for no one.'}</h2>
          <p>{pt ? 'Entra num SMP focado em ação, estratégia e progressão. Sem distrações — apenas perícia e vontade de vencer.' : 'Join an SMP focused on action, strategy and progression. No distractions — just skill and the drive to win.'}</p>
        </div>
        <div className="feature-grid">
          {features.map(({ icon: Icon, number, title, text }) => (
            <article key={number}><div><Icon size={24} aria-hidden="true" /><span>{number}</span></div><h3>{choose(locale, title)}</h3><p>{choose(locale, text)}</p></article>
          ))}
        </div>
      </section>

      <section className="faq-section">
        <div><p className="eyebrow">{pt ? 'Antes de comprar' : 'Before you buy'}</p><h2>{pt ? 'Respostas rápidas' : 'Quick answers'}</h2></div>
        <div className="faq-list">
          {faq.map((item) => <details key={item.id}><summary>{localised(locale, item.question, item.questionPt)}</summary><p>{localised(locale, item.answer, item.answerPt)}</p></details>)}
          {!faq.length && <p className="empty-state">{pt ? 'A carregar respostas…' : 'Loading answers…'}</p>}
        </div>
      </section>

      <section className="home-help">
        <div className="section-heading">
          <div><p className="eyebrow">{pt ? 'Tudo o que precisas' : 'Everything you need'}</p><h2>{pt ? 'Joga com confiança' : 'Play with confidence'}</h2></div>
          <p>{pt ? 'Atalhos oficiais para entrar, verificar o serviço, comprar com segurança e pedir ajuda.' : 'Official shortcuts to join, check the service, purchase safely and get help.'}</p>
        </div>
        <div className="home-help-grid">
          {essentialLinks.map(({ icon: Icon, title, text, href, external }) => {
            const content = <><Icon size={22} aria-hidden="true" /><span><strong>{title}</strong><small>{text}</small></span>{external && <ExternalLink size={14} aria-hidden="true" />}</>;
            return external
              ? <a href={href} target="_blank" rel="noreferrer" key={title}>{content}</a>
              : <Link href={href} key={title}>{content}</Link>;
          })}
        </div>
      </section>

      <section className="join-section">
        <div><p className="eyebrow">{pt ? 'Pronto para entrar?' : 'Ready to join?'}</p><h2>{pt ? 'A tua próxima luta começa agora.' : 'Your next fight starts now.'}</h2></div>
        <button className="button button-primary" type="button" onClick={copyServerIp}>
          {copied ? <CheckCheck size={18} aria-hidden="true" /> : <Copy size={18} aria-hidden="true" />}{copied ? (pt ? 'IP copiado' : 'IP copied') : serverIp}
        </button>
        <Link className="join-help-link" href={localePath(locale, '/join')} onClick={() => track('join_guide')}>{pt ? 'Como entrar' : 'How to join'}</Link>
      </section>

      <SiteFooter locale={locale} />
    </main>
  );
}
