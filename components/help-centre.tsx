import Link from '@/components/site-link';
import {
  BookOpenCheck,
  CreditCard,
  ExternalLink,
  LifeBuoy,
  LockKeyhole,
  Server,
  ShieldCheck,
  UserRoundCheck,
  Wifi,
} from 'lucide-react';
import { InfoHeader } from '@/components/info-header';
import { SiteFooter } from '@/components/site-footer';
import { localePath, type Locale } from '@/lib/site-content';

const storeAccountUrl = 'https://merelymesmpstore.tip4serv.com/login';

type HelpLink = { label: string; href: string; external?: boolean };
type HelpTopic = {
  id: string;
  icon: typeof Server;
  title: string;
  body: string;
  steps: string[];
  links: HelpLink[];
};

export function HelpCentre({ locale }: { locale: Locale }) {
  const pt = locale === 'pt';
  const path = (value: string) => localePath(locale, value);
  const quickLinks = [
    { icon: Server, label: pt ? 'Como entrar' : 'How to join', href: path('/join') },
    { icon: Wifi, label: pt ? 'Estado em direto' : 'Live status', href: path('/status') },
    { icon: CreditCard, label: pt ? 'Conta da loja' : 'Store account', href: storeAccountUrl, external: true },
    { icon: LifeBuoy, label: pt ? 'Contactar suporte' : 'Contact support', href: path('/support') },
  ];

  const topics: HelpTopic[] = pt ? [
    {
      id: 'join', icon: Server, title: 'Entrar e resolver ligações',
      body: 'O MerelyMeSMP usa Minecraft: Java Edition. O domínio público trata da porta especial do servidor.',
      steps: ['Abre o Minecraft: Java Edition 1.21 ou superior e seleciona Multiplayer.', 'Usa MerelyMeSMP como nome e merelymesmp.com como endereço.', 'Confirma que estás na versão +1.21 e verifica o estado em direto antes de reportar um erro.'],
      links: [{ label: 'Guia completo', href: path('/join') }, { label: 'Ver estado', href: path('/status') }],
    },
    {
      id: 'purchases', icon: CreditCard, title: 'Compras e entrega',
      body: 'Os preços finais e métodos disponíveis aparecem no checkout oficial Tip4Serv. A transação é processada pela Tip4Serv e respetivos prestadores de pagamento.',
      steps: ['Confirma o produto, preço e nome Minecraft antes de pagar.', 'A entrega no jogo começa depois da confirmação do pagamento.', 'Se faltar uma entrega, guarda a referência e envia um pedido de compra.'],
      links: [{ label: 'Abrir conta da loja', href: storeAccountUrl, external: true }, { label: 'Ajuda com compra', href: path('/support') }, { label: 'Reembolsos', href: path('/refunds') }],
    },
    {
      id: 'security', icon: LockKeyhole, title: 'Conta e segurança',
      body: 'Usa apenas merelymesmp.com e a loja merelymesmpstore.tip4serv.com. A equipa não precisa das tuas credenciais para ajudar.',
      steps: ['Nunca envies passwords, códigos de autenticação ou tokens.', 'Nunca envies o número completo do cartão, acesso bancário ou código de segurança.', 'Reporta links falsos ou tentativas de imitação através do suporte privado.'],
      links: [{ label: 'Política de privacidade', href: path('/privacy') }, { label: 'Reportar problema', href: path('/support') }],
    },
    {
      id: 'moderation', icon: ShieldCheck, title: 'Regras, denúncias e recursos',
      body: 'As decisões de moderação são separadas das compras. A lista pública de bans só receberá dados verificados da futura API de leitura do Minecraft.',
      steps: ['Lê as regras antes de jogar.', 'Numa denúncia, indica nome, data, hora e prova relevante.', 'Num recurso, envia um único pedido conciso e aguarda a análise.'],
      links: [{ label: 'Regras', href: path('/rules') }, { label: 'Bans e recursos', href: path('/bans') }, { label: 'Enviar pedido', href: path('/support') }],
    },
    {
      id: 'parents', icon: UserRoundCheck, title: 'Pais, responsáveis e privacidade',
      body: 'As compras são opcionais e devem ser autorizadas pelo titular do método de pagamento. Há páginas próprias para segurança, privacidade e direitos do consumidor.',
      steps: ['Confirma o produto e o nome Minecraft com o jogador.', 'Guarda apenas a referência da encomenda para suporte.', 'Usa o contacto privado para questões de segurança ou dados pessoais.'],
      links: [{ label: 'Informação para pais', href: path('/parents') }, { label: 'Termos', href: path('/terms') }, { label: 'Privacidade', href: path('/privacy') }],
    },
    {
      id: 'data', icon: BookOpenCheck, title: 'Estado, rankings e dados reais',
      body: 'O estado público é atualizado automaticamente. O ranking atual mede apenas tempo online observado; estatísticas de combate não são inventadas.',
      steps: ['Consulta o estado antes de reportar uma interrupção.', 'Vê o período observado e a nota explicativa do ranking.', 'Dados de plugins só aparecem depois de uma API de leitura ser verificada.'],
      links: [{ label: 'Estado dos serviços', href: path('/status') }, { label: 'Ranking', href: path('/community#leaderboard') }, { label: 'Notícias', href: path('/news') }],
    },
  ] : [
    {
      id: 'join', icon: Server, title: 'Joining and connection help',
      body: 'MerelyMeSMP uses Minecraft: Java Edition. The public domain handles the server’s non-standard port.',
      steps: ['Open Minecraft: Java Edition 1.21 or later and select Multiplayer.', 'Use MerelyMeSMP as the name and merelymesmp.com as the address.', 'Make sure you are using version +1.21 and check the live status before reporting an error.'],
      links: [{ label: 'Full guide', href: path('/join') }, { label: 'View status', href: path('/status') }],
    },
    {
      id: 'purchases', icon: CreditCard, title: 'Purchases and delivery',
      body: 'Final prices and available payment methods appear in the official Tip4Serv checkout. Tip4Serv and its payment providers process the transaction.',
      steps: ['Check the product, price and Minecraft username before paying.', 'In-game delivery starts after payment confirmation.', 'If delivery is missing, keep the reference and submit a purchase request.'],
      links: [{ label: 'Open store account', href: storeAccountUrl, external: true }, { label: 'Purchase help', href: path('/support') }, { label: 'Refunds', href: path('/refunds') }],
    },
    {
      id: 'security', icon: LockKeyhole, title: 'Account and security',
      body: 'Use only merelymesmp.com and merelymesmpstore.tip4serv.com. Staff do not need your credentials to help.',
      steps: ['Never send passwords, authentication codes or tokens.', 'Never send a full card number, bank login or security code.', 'Report fake links or impersonation attempts through private support.'],
      links: [{ label: 'Privacy policy', href: path('/privacy') }, { label: 'Report a problem', href: path('/support') }],
    },
    {
      id: 'moderation', icon: ShieldCheck, title: 'Rules, reports and appeals',
      body: 'Moderation decisions are separate from purchases. The public ban feed will only receive verified data from the future read-only Minecraft API.',
      steps: ['Read the rules before playing.', 'For a report, include the name, date, time and relevant evidence.', 'For an appeal, submit one concise request and wait for review.'],
      links: [{ label: 'Rules', href: path('/rules') }, { label: 'Bans and appeals', href: path('/bans') }, { label: 'Submit request', href: path('/support') }],
    },
    {
      id: 'parents', icon: UserRoundCheck, title: 'Parents, guardians and privacy',
      body: 'Purchases are optional and must be authorised by the payment method holder. Dedicated pages cover safety, privacy and consumer rights.',
      steps: ['Confirm the product and Minecraft name with the player.', 'Keep only the order reference for support.', 'Use private support for safety or personal-data questions.'],
      links: [{ label: 'Information for parents', href: path('/parents') }, { label: 'Terms', href: path('/terms') }, { label: 'Privacy', href: path('/privacy') }],
    },
    {
      id: 'data', icon: BookOpenCheck, title: 'Status, rankings and real data',
      body: 'The public status refreshes automatically. The current leaderboard measures observed online time only; combat statistics are never invented.',
      steps: ['Check status before reporting an interruption.', 'Review the observed period and ranking note.', 'Plugin data appears only after a read-only API has been verified.'],
      links: [{ label: 'Service status', href: path('/status') }, { label: 'Leaderboard', href: path('/community#leaderboard') }, { label: 'News', href: path('/news') }],
    },
  ];

  function HelpAnchor({ link }: { link: HelpLink }) {
    const content = <>{link.label}{link.external && <ExternalLink size={14} aria-hidden="true" />}</>;
    return link.external
      ? <a href={link.href} target="_blank" rel="noreferrer">{content}</a>
      : <Link href={link.href}>{content}</Link>;
  }

  return (
    <>
      <InfoHeader locale={locale} switchHref={pt ? '/help' : '/pt/help'} />
      <main className="info-shell help-centre-page" lang={pt ? 'pt-PT' : 'en'}>
        <section className="help-centre-hero">
          <div>
            <p className="eyebrow">{pt ? 'Respostas verificadas' : 'Verified answers'}</p>
            <h1>{pt ? 'Centro de ajuda' : 'Help centre'}</h1>
            <p>{pt ? 'Entrar, comprar, proteger a conta e pedir ajuda sem depender de informação espalhada.' : 'Join, buy, protect your account and get help without relying on scattered information.'}</p>
          </div>
          <div className="help-quick-actions" aria-label={pt ? 'Ações rápidas' : 'Quick actions'}>
            {quickLinks.map(({ icon: Icon, label, href, external }) => external
              ? <a href={href} target="_blank" rel="noreferrer" key={label}><Icon size={20} aria-hidden="true" /><span>{label}</span><ExternalLink size={14} aria-hidden="true" /></a>
              : <Link href={href} key={label}><Icon size={20} aria-hidden="true" /><span>{label}</span></Link>)}
          </div>
        </section>

        <section className="help-centre-grid" aria-label={pt ? 'Tópicos de ajuda' : 'Help topics'}>
          {topics.map(({ id, icon: Icon, title, body, steps, links }) => (
            <article className="help-topic" id={id} key={id}>
              <header><Icon size={23} aria-hidden="true" /><h2>{title}</h2></header>
              <p>{body}</p>
              <ol>{steps.map((step) => <li key={step}>{step}</li>)}</ol>
              <div className="help-topic-links">{links.map((link) => <HelpAnchor link={link} key={link.href} />)}</div>
            </article>
          ))}
        </section>

        <section className="help-safety">
          <ShieldCheck size={28} aria-hidden="true" />
          <div><h2>{pt ? 'Não encontres a resposta?' : 'Still need help?'}</h2><p>{pt ? 'Envia um pedido privado com os factos essenciais. Nunca incluas passwords, códigos ou dados completos de pagamento.' : 'Send a private request with the essential facts. Never include passwords, codes or complete payment details.'}</p></div>
          <Link className="button button-primary" href={path('/support')}>{pt ? 'Abrir suporte' : 'Open support'}</Link>
        </section>
      </main>
      <SiteFooter locale={locale} />
    </>
  );
}
