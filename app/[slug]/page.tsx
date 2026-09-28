import type { Metadata } from 'next';
import Link from '@/components/site-link';
import { notFound } from 'next/navigation';
import { ExternalLink, Mail, Server, ShieldCheck } from 'lucide-react';
import { CopyServerButton } from '@/components/copy-server-button';
import { InfoHeader } from '@/components/info-header';
import { LiveServerStatus } from '@/components/live-server-status';
import { SiteFooter } from '@/components/site-footer';

type Section = { title: string; paragraphs?: string[]; bullets?: string[] };
type PublicPage = { title: string; eyebrow: string; intro: string; updated?: string; sections: Section[] };

const pages: Record<string, PublicPage> = {
  terms: {
    title: 'Terms of service',
    eyebrow: 'Clear expectations',
    intro: 'These terms govern access to MerelyMeSMP and purchases made for use on the server.',
    updated: '30 August 2026',
    sections: [
      {
        title: '1. Operator and scope',
        paragraphs: [
          'MerelyMeSMP is an independent Minecraft server and digital service operated from Portugal under the MerelyMeSMP name. Questions can be sent to support@merelymesmp.com.',
          'Minecraft, Mojang and Microsoft do not operate, approve or sponsor this service.',
        ],
      },
      {
        title: '2. Accounts and server access',
        bullets: [
          'You need a valid Minecraft account and must follow the server rules.',
          'Keep your account secure. Actions performed through an account may be treated as actions of its holder.',
          'Access may be limited or removed for cheating, fraud, abuse, security threats or serious rule violations.',
          'A purchase never exempts a player from moderation or guarantees permanent access after a valid sanction.',
        ],
      },
      {
        title: '3. Purchases and delivery',
        paragraphs: [
          'Products, final prices and payment methods are shown before payment in the official MerelyMeSMP Tip4Serv checkout. Tip4Serv and its payment providers process the transaction; MerelyMeSMP does not receive complete card details.',
          'Digital items are normally delivered automatically to the Minecraft username supplied at checkout. Contact support with the order reference if delivery fails. Never send a password, full card number or security code.',
        ],
        bullets: [
          'Digital items are limited to MerelyMeSMP, have no cash value and cannot be redeemed for money.',
          'Products cannot be transferred unless the product description expressly allows it.',
          'The checkout price and description shown before payment take precedence over an outdated cached display.',
        ],
      },
      {
        title: '4. Fair use and availability',
        paragraphs: [
          'We may correct errors, rebalance content and update the server to protect fair play and compatibility. Material purchase changes will be handled fairly and mandatory consumer rights remain unaffected.',
          'Maintenance, attacks, provider outages and game updates may cause temporary interruption. We will use reasonable care but cannot promise uninterrupted availability.',
        ],
      },
      {
        title: '5. Law and changes',
        paragraphs: [
          'Portuguese law applies, without removing mandatory protections available to consumers in their country. These terms may be updated for legal, security or service changes; the date above identifies the current version.',
        ],
      },
    ],
  },
  privacy: {
    title: 'Privacy policy',
    eyebrow: 'Data without surprises',
    intro: 'This page explains what MerelyMeSMP handles, why it is needed and what choices players have.',
    updated: '30 August 2026',
    sections: [
      {
        title: '1. Who is responsible',
        paragraphs: [
          'MerelyMeSMP is responsible for data used to operate this website and Minecraft server. Privacy requests can be sent to support@merelymesmp.com.',
        ],
      },
      {
        title: '2. Data we handle',
        bullets: [
          'Public Minecraft identity data such as username and UUID.',
          'Server activity needed for profiles, moderation and statistics, including observed online time, first and last seen times and gameplay records supplied by the server.',
          'Technical security records such as IP address, request time, browser information and server logs.',
          'Support messages, contact email, Minecraft username and the information you choose to include.',
          'Order reference, Minecraft username, product and delivery status when needed to support a purchase. Full payment card data is not handled by this website.',
          'Aggregated counts of actions such as copying the server address, opening the store or changing language. These counts do not use advertising cookies or build individual visitor profiles.',
        ],
      },
      {
        title: '3. Why and how we use it',
        paragraphs: [
          'Data is used to provide the server and purchases, keep the service secure, enforce rules, answer support requests and publish fair community statistics. The legal basis may be performance of a contract, legitimate operational and security interests, legal obligations or consent where required.',
        ],
      },
      {
        title: '4. Providers and retention',
        paragraphs: [
          'Hosting and monitoring providers, including Railway, process technical data for us. Tip4Serv and its payment providers process checkout data under their own notices. Data is kept only for as long as needed for the purpose, security, disputes and applicable legal obligations.',
          'The site uses no advertising or behavioural analytics cookies. Product-use measurements are stored only as daily aggregate counts. If this changes, this policy and any required consent controls will be updated first.',
        ],
      },
      {
        title: '5. Your choices',
        paragraphs: [
          'Depending on applicable law, you may request access, correction, deletion, restriction, portability or object to processing. Contact us from an address that allows us to verify the request. You may also complain to the competent data protection authority.',
          'Players under the age required by their country should involve a parent or guardian. Do not send sensitive personal information through Minecraft chat or support.',
        ],
      },
    ],
  },
  refunds: {
    title: 'Refund policy',
    eyebrow: 'Purchase support',
    intro: 'We resolve failed, duplicated or incorrect digital deliveries fairly and preserve mandatory consumer rights.',
    updated: '30 August 2026',
    sections: [
      {
        title: 'Ask for help first',
        paragraphs: [
          'Email support@merelymesmp.com with the order reference, Minecraft username and a short description. Do not send passwords, card numbers or security codes.',
        ],
        bullets: [
          'Duplicate charge or duplicate order.',
          'Item not delivered after payment confirmation and a reasonable delivery period.',
          'Item materially different from the description shown at purchase.',
          'Purchase made without the account holder’s authorisation, subject to reasonable verification.',
          'Any refund required by applicable consumer law.',
        ],
      },
      {
        title: 'Digital content and withdrawal',
        paragraphs: [
          'EU consumers normally have a 14-day withdrawal right for distance contracts. For digital content supplied immediately, that right may end only where the consumer expressly requested immediate supply and acknowledged the resulting loss of the withdrawal right, as required by law. We assess each request using the checkout and delivery records; mandatory rights are never excluded by this policy.',
        ],
      },
      {
        title: 'Abuse and chargebacks',
        paragraphs: [
          'Fraudulent claims, deliberate chargeback abuse or attempts to retain refunded digital benefits may lead to removal of those benefits and account restrictions. This does not limit a genuine right to dispute an unauthorised or incorrect payment.',
        ],
      },
    ],
  },
  rules: {
    title: 'Server rules',
    eyebrow: 'Compete hard. Play fair.',
    intro: 'These rules apply in-game and in official MerelyMeSMP community spaces.',
    updated: '30 August 2026',
    sections: [
      {
        title: 'Fair play',
        bullets: [
          'No hacked clients, combat automation, x-ray, macros that provide an unfair advantage or prohibited modifications.',
          'Do not exploit bugs, duplicate items, bypass restrictions or conceal an exploit. Report serious issues privately.',
          'No bots, denial-of-service attempts, malicious traffic or actions intended to damage server performance.',
          'Alternative accounts may not be used to evade a sanction, manipulate rewards or gain an unfair advantage.',
        ],
      },
      {
        title: 'Respect and safety',
        bullets: [
          'No threats, harassment, hate speech, sexual content, doxxing or sharing another person’s private information.',
          'Keep usernames, skins, builds and messages suitable for a broad audience, including minors.',
          'No impersonation of staff, misleading giveaways, scams, phishing or requests for passwords and payment codes.',
          'No spam, disruptive advertising or real-money trading outside the official store.',
        ],
      },
      {
        title: 'Moderation and appeals',
        paragraphs: [
          'Staff may use warnings, temporary restrictions, rollbacks, mutes or bans proportionate to severity, history and risk. Serious security, fraud or safety incidents may result in immediate action.',
          'To appeal, email support@merelymesmp.com with your username, sanction date and a concise explanation. Be respectful and submit one appeal; purchases do not influence moderation decisions.',
        ],
      },
    ],
  },
  bans: {
    title: 'Bans and appeals',
    eyebrow: 'Transparent moderation',
    intro: 'Understand how sanctions work and use the official channel to appeal a decision.',
    updated: '31 August 2026',
    sections: [
      {
        title: 'Public ban records',
        paragraphs: [
          'The public ban feed is not connected yet. We will only show verified records supplied through a read-only server integration; an absent name does not prove that no sanction exists.',
        ],
        bullets: [
          'Public fields: Minecraft name, broad reason category, issue date, expiry date where applicable and current status.',
          'Private fields: IP addresses, email, staff notes, report evidence and security details will not be published.',
          'Records may be delayed or withheld for safety, privacy or an active investigation.',
        ],
      },
      {
        title: 'Appeal a sanction',
        paragraphs: [
          'Use the support centre with your Minecraft username, the approximate sanction date and a concise explanation. Never send a password, login code or authentication token.',
        ],
        bullets: [
          'Submit one complete appeal and wait for the team to review it.',
          'Include relevant evidence without exposing another player’s private information.',
          'Purchases never influence moderation decisions.',
        ],
      },
      {
        title: 'Fair moderation',
        paragraphs: [
          'Warnings, mutes, restrictions and bans should be proportionate to severity, history and risk. Public records will avoid sensitive personal information and may be withheld where safety, privacy or an active investigation requires it.',
        ],
      },
    ],
  },
  contact: {
    title: 'Contact support',
    eyebrow: 'We are here to help',
    intro: 'Use the official address below for purchases, technical problems, privacy requests and moderation appeals.',
    sections: [
      {
        title: 'Email',
        paragraphs: [
          'support@merelymesmp.com',
          'Include your Minecraft username and, for a purchase, the Tip4Serv order reference. Never include a password, full card number, bank login, security code or authentication token.',
        ],
      },
      {
        title: 'What to include',
        bullets: [
          'Purchase: order reference, username, product and what went wrong.',
          'Technical issue: username, time, Java Edition and exact error message.',
          'Appeal: username, sanction date and a concise explanation.',
          'Privacy request: the request and enough information to verify the affected account.',
        ],
      },
    ],
  },
};

export function generateStaticParams() {
  return [...Object.keys(pages), 'join'].map((slug) => ({ slug }));
}

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params;
  if (slug === 'join') return { title: 'How to join — MerelyMeSMP', description: 'Join MerelyMeSMP on Minecraft Java Edition.', alternates: { canonical: '/join', languages: { en: '/join', 'pt-PT': '/pt/join' } } };
  const page = pages[slug];
  return page ? { title: `${page.title} — MerelyMeSMP`, description: page.intro, alternates: { canonical: `/${slug}`, languages: { en: `/${slug}`, 'pt-PT': `/pt/${slug}` } } } : {};
}

function JoinPage() {
  return (
    <>
      <InfoHeader switchHref="/pt/join" />
      <main className="info-shell join-guide">
        <section className="info-hero">
          <p className="eyebrow">Minecraft Java Edition · +1.21</p>
          <h1>Join the fight.</h1>
          <p>Use the domain below — the non-standard GPORTAL port is configured automatically through DNS.</p>
          <div className="join-guide-actions">
            <CopyServerButton />
            <Link className="button button-ghost" href="/community">View community</Link>
          </div>
          <LiveServerStatus />
        </section>
        <section className="steps-grid" aria-label="How to join">
          {[
            ['01', 'Open Minecraft', 'Launch Minecraft: Java Edition 1.21 or later.'],
            ['02', 'Add server', 'Choose Multiplayer, then Add Server. Use MerelyMeSMP as the name.'],
            ['03', 'Paste the address', 'Enter merelymesmp.com exactly. You do not need to type the numerical port.'],
            ['04', 'Join', 'Save the server and connect. Read the rules before your first fight.'],
          ].map(([number, title, text]) => (
            <article key={number}><span>{number}</span><h2>{title}</h2><p>{text}</p></article>
          ))}
        </section>
        <section className="help-card">
          <Server size={24} aria-hidden="true" />
          <div><h2>Connection problem?</h2><p>Confirm you are using Java Edition. If the server is online and the error continues, contact support with the exact message.</p></div>
          <a href="mailto:support@merelymesmp.com">Email support <Mail size={15} aria-hidden="true" /></a>
        </section>
      </main>
      <SiteFooter />
    </>
  );
}

export default async function PublicInfoPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  if (slug === 'join') return <JoinPage />;
  const page = pages[slug];
  if (!page) notFound();

  return (
    <>
      <InfoHeader switchHref={`/pt/${slug}`} />
      <main className="info-shell legal-page">
        <section className="info-hero">
          <p className="eyebrow">{page.eyebrow}</p>
          <h1>{page.title}</h1>
          <p>{page.intro}</p>
          {page.updated && <span>Last updated: {page.updated}</span>}
        </section>
        <div className="legal-layout">
          <aside>
            <ShieldCheck size={22} aria-hidden="true" />
            <strong>MerelyMeSMP</strong>
            <span>Independent server operated from Portugal</span>
            <a href="mailto:support@merelymesmp.com">support@merelymesmp.com</a>
          </aside>
          <article className="legal-content">
            {page.sections.map((section) => (
              <section key={section.title}>
                <h2>{section.title}</h2>
                {section.paragraphs?.map((paragraph) => <p key={paragraph}>{paragraph}</p>)}
                {section.bullets && <ul>{section.bullets.map((bullet) => <li key={bullet}>{bullet}</li>)}</ul>}
              </section>
            ))}
            {slug === 'contact' && (
              <a className="button button-primary contact-button" href="mailto:support@merelymesmp.com">
                Email support <ExternalLink size={16} aria-hidden="true" />
              </a>
            )}
            {slug === 'bans' && (
              <Link className="button button-primary contact-button" href="/support">
                Open an appeal <ExternalLink size={16} aria-hidden="true" />
              </Link>
            )}
          </article>
        </div>
      </main>
      <SiteFooter />
    </>
  );
}
