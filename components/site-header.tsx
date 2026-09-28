'use client';

import Image from 'next/image';
import Link from '@/components/site-link';
import { useEffect } from 'react';
import { Languages, Menu, ShoppingBag } from 'lucide-react';
import { track } from '@/lib/analytics';
import { localePath, type Locale } from '@/lib/site-content';

export function SiteHeader({
  locale = 'en',
  overlay = false,
  switchHref,
}: {
  locale?: Locale;
  overlay?: boolean;
  switchHref?: string;
}) {
  const pt = locale === 'pt';
  useEffect(() => {
    document.documentElement.lang = pt ? 'pt-PT' : 'en';
  }, [pt]);
  const links = [
    [localePath(locale, '/news'), pt ? 'Notícias' : 'News'],
    [localePath(locale, '/community'), pt ? 'Comunidade' : 'Community'],
    [localePath(locale, '/community#leaderboard'), pt ? 'Ranking' : 'Leaderboard'],
    [localePath(locale, '/bans'), 'Bans'],
    [localePath(locale, '/status'), pt ? 'Estado' : 'Status'],
    [localePath(locale, '/join'), pt ? 'Entrar' : 'Join'],
    [localePath(locale, '/help'), pt ? 'Ajuda' : 'Help'],
    [localePath(locale, '/support'), pt ? 'Suporte' : 'Support'],
  ];
  const alternateHref = switchHref || (pt ? '/' : '/pt');

  return (
    <header className={overlay ? 'site-header' : 'info-header'}>
      <Link className="brand" href={localePath(locale)} aria-label="MerelyMeSMP — home">
        <Image src="/images/logo.png" alt="" width={48} height={48} priority={overlay} />
        <span>MERELYME<strong>SMP</strong></span>
      </Link>
      <nav className="desktop-nav" aria-label={pt ? 'Navegação principal' : 'Main navigation'}>
        {links.map(([href, label]) => <Link href={href} key={href}>{label}</Link>)}
        <a href="/discord" target="_blank" rel="noreferrer" onClick={() => track('discord_click')}>Discord</a>
        <a className="store-account-link" href="https://merelymesmpstore.tip4serv.com/login" target="_blank" rel="noreferrer">
          <ShoppingBag size={15} aria-hidden="true" /> {pt ? 'Conta' : 'Account'}
        </a>
        <Link className="language-link" href={alternateHref} hrefLang={pt ? 'en' : 'pt'} onClick={() => track('language_switch')}>
          <Languages size={15} aria-hidden="true" /> {pt ? 'EN' : 'PT'}
        </Link>
      </nav>
      <details className="mobile-nav">
        <summary aria-label={pt ? 'Abrir menu' : 'Open menu'}><Menu size={20} aria-hidden="true" /></summary>
        <div>
          {links.map(([href, label]) => <Link href={href} key={href}>{label}</Link>)}
          <a href="/discord" target="_blank" rel="noreferrer" onClick={() => track('discord_click')}>Discord</a>
          <Link href={alternateHref} hrefLang={pt ? 'en' : 'pt'} onClick={() => track('language_switch')}>
            <Languages size={15} aria-hidden="true" /> {pt ? 'English' : 'Português'}
          </Link>
          <a href="https://merelymesmpstore.tip4serv.com/login" target="_blank" rel="noreferrer">
            <ShoppingBag size={15} aria-hidden="true" /> {pt ? 'Conta da loja' : 'Store account'}
          </a>
        </div>
      </details>
    </header>
  );
}
