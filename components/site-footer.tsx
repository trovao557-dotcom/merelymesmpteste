import Image from 'next/image';
import Link from '@/components/site-link';
import { localePath, type Locale } from '@/lib/site-content';

export function SiteFooter({ locale = 'en' }: { locale?: Locale }) {
  const pt = locale === 'pt';
  const links = [
    ['/help', pt ? 'Ajuda' : 'Help'],
    ['/news', pt ? 'Notícias' : 'News'],
    ['/community', pt ? 'Comunidade' : 'Community'],
    ['/community#leaderboard', pt ? 'Ranking' : 'Leaderboard'],
    ['/bans', 'Bans'],
    ['/status', pt ? 'Estado' : 'Status'],
    ['/join', pt ? 'Entrar' : 'Join'],
    ['/support', pt ? 'Suporte' : 'Support'],
    ['/parents', pt ? 'Para pais' : 'For parents'],
    ['/terms', pt ? 'Termos' : 'Terms'],
    ['/privacy', pt ? 'Privacidade' : 'Privacy'],
    ['/refunds', pt ? 'Reembolsos' : 'Refunds'],
    ['/rules', pt ? 'Regras' : 'Rules'],
  ];

  return (
    <footer>
      <div className="footer-brand">
        <Image src="/images/logo.png" alt="" width={42} height={42} />
        <div><strong>MerelyMeSMP</strong></div>
      </div>
      <p className="minecraft-disclaimer">
        NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.
      </p>
      <div className="footer-links">
        <a href="/discord" target="_blank" rel="noreferrer">Discord</a>
        {links.map(([href, label]) => <Link href={localePath(locale, href)} key={href}>{label}</Link>)}
        <a href="https://merelymesmpstore.tip4serv.com/login" target="_blank" rel="noreferrer">{pt ? 'Conta da loja' : 'Store account'}</a>
      </div>
      <p className="copyright">© {new Date().getFullYear()} MerelyMeSMP</p>
    </footer>
  );
}
