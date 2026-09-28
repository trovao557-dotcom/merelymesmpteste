import Link from '@/components/site-link';
import { CreditCard, LockKeyhole, MessageCircleWarning, ShieldCheck, UserRoundCheck } from 'lucide-react';
import { InfoHeader } from '@/components/info-header';
import { SiteFooter } from '@/components/site-footer';
import { localePath, type Locale } from '@/lib/site-content';

export function ParentsPage({ locale }: { locale: Locale }) {
  const pt = locale === 'pt';
  const cards = pt ? [
    [ShieldCheck, 'O que é o MerelyMeSMP?', 'Um servidor independente de Minecraft Java centrado em survival e Mace PvP. Não é um produto oficial da Mojang ou Microsoft.'],
    [UserRoundCheck, 'Moderação', 'As regras abrangem o jogo e os espaços oficiais da comunidade. A equipa pode advertir, silenciar ou banir de acordo com a gravidade.'],
    [CreditCard, 'Compras opcionais', 'Ranks e pontos são opcionais. O checkout é feito na Tip4Serv através da Stripe; o titular do método de pagamento deve autorizar a compra.'],
    [LockKeyhole, 'Proteção de contas', 'A equipa nunca pede passwords, códigos de autenticação, dados bancários ou números completos de cartão.'],
  ] : [
    [ShieldCheck, 'What is MerelyMeSMP?', 'An independent Minecraft Java server focused on survival and Mace PvP. It is not an official Mojang or Microsoft product.'],
    [UserRoundCheck, 'Moderation', 'The rules cover the game and official community spaces. Staff may warn, mute or ban according to severity.'],
    [CreditCard, 'Optional purchases', 'Ranks and points are optional. Checkout runs through Tip4Serv and Stripe; the payment method owner must authorise the purchase.'],
    [LockKeyhole, 'Account protection', 'Staff never ask for passwords, authentication codes, bank credentials or full card numbers.'],
  ] as const;

  return (
    <>
      <InfoHeader locale={locale} switchHref={pt ? '/parents' : '/pt/parents'} />
      <main className="info-shell parents-page" lang={pt ? 'pt-PT' : 'en'}>
        <section className="parents-hero">
          <div><p className="eyebrow">{pt ? 'Informação clara para adultos responsáveis' : 'Clear information for responsible adults'}</p><h1>{pt ? 'Para pais e responsáveis' : 'For parents and guardians'}</h1><p>{pt ? 'Como funciona o servidor, quem trata dos pagamentos e o que fazer perante um problema de segurança, moderação ou compra.' : 'How the server works, who handles payments and what to do about a safety, moderation or purchase concern.'}</p></div>
          <ShieldCheck size={92} aria-hidden="true" />
        </section>
        <section className="parents-grid">
          {cards.map(([Icon, title, body]) => <article key={title}><Icon size={24} aria-hidden="true" /><h2>{title}</h2><p>{body}</p></article>)}
        </section>
        <section className="parent-actions">
          <MessageCircleWarning size={28} aria-hidden="true" />
          <div><h2>{pt ? 'Existe uma preocupação?' : 'Have a concern?'}</h2><p>{pt ? 'Guarda referências de encomenda e capturas relevantes, mas não envies informação financeira ou credenciais. Os pedidos de segurança e moderação são tratados separadamente das compras.' : 'Keep order references and relevant screenshots, but do not send financial information or credentials. Safety and moderation requests are handled separately from purchases.'}</p></div>
          <Link className="button button-primary" href={localePath(locale, '/support')}>{pt ? 'Contactar suporte' : 'Contact support'}</Link>
        </section>
        <section className="parent-links">
          <h2>{pt ? 'Documentos importantes' : 'Important documents'}</h2>
          <div>
            <Link href={localePath(locale, '/rules')}>{pt ? 'Regras do servidor' : 'Server rules'}</Link>
            <Link href={localePath(locale, '/refunds')}>{pt ? 'Política de reembolsos' : 'Refund policy'}</Link>
            <Link href={localePath(locale, '/privacy')}>{pt ? 'Política de privacidade' : 'Privacy policy'}</Link>
            <Link href={localePath(locale, '/terms')}>{pt ? 'Termos de utilização' : 'Terms of use'}</Link>
          </div>
        </section>
      </main>
      <SiteFooter locale={locale} />
    </>
  );
}
