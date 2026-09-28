'use client';

import Link from '@/components/site-link';
import { FormEvent, useState } from 'react';
import { AlertTriangle, CheckCircle2, FileText, LifeBuoy, Mail, ShieldAlert, ShoppingCart, Users } from 'lucide-react';
import { InfoHeader } from '@/components/info-header';
import { SiteFooter } from '@/components/site-footer';
import { track } from '@/lib/analytics';
import { localePath, type Locale } from '@/lib/site-content';

export function SupportPage({ locale }: { locale: Locale }) {
  const pt = locale === 'pt';
  const [type, setType] = useState('general');
  const [state, setState] = useState<'idle' | 'sending' | 'sent' | 'error'>('idle');
  const [message, setMessage] = useState('');
  const [reference, setReference] = useState('');

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setState('sending');
    setMessage('');
    const form = event.currentTarget;
    const payload = Object.fromEntries(new FormData(form));
    payload.consent = form.elements.namedItem('consent') instanceof HTMLInputElement && (form.elements.namedItem('consent') as HTMLInputElement).checked ? 'true' : 'false';
    const response = await fetch('/api/support', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...payload, consent: payload.consent === 'true' }),
    }).catch(() => null);
    const result = response ? await response.json().catch(() => ({})) as { error?: string; reference?: string } : {};
    if (response?.ok) {
      setReference(result.reference || 'received');
      setState('sent');
      track('support_submit');
      form.reset();
      setType('general');
    } else {
      setMessage(result.error || (pt ? 'Não foi possível enviar. Tenta novamente.' : 'Could not send your request. Please try again.'));
      setState('error');
    }
  }

  const categories = [
    { icon: ShieldAlert, title: pt ? 'Denunciar jogador' : 'Report a player', text: pt ? 'Comportamento, fraude, assédio ou infrações às regras.' : 'Behaviour, scams, harassment or rule violations.' },
    { icon: FileText, title: pt ? 'Recorrer de punição' : 'Appeal a sanction', text: pt ? 'Explica a situação de forma breve e respeitosa.' : 'Explain the situation briefly and respectfully.' },
    { icon: ShoppingCart, title: pt ? 'Ajuda com compra' : 'Purchase help', text: pt ? 'Entrega em falta, duplicação ou produto incorreto.' : 'Missing delivery, duplicate charge or wrong product.' },
    { icon: Users, title: pt ? 'Candidatura a staff' : 'Staff application', text: pt ? 'Apresenta a tua experiência e disponibilidade.' : 'Tell us about your experience and availability.' },
  ];

  return (
    <>
      <InfoHeader locale={locale} switchHref={pt ? '/support' : '/pt/support'} />
      <main className="info-shell support-page" lang={pt ? 'pt-PT' : 'en'}>
        <section className="support-hero">
          <div><p className="eyebrow">{pt ? 'Centro de suporte' : 'Support centre'}</p><h1>{pt ? 'Como podemos ajudar?' : 'How can we help?'}</h1><p>{pt ? 'Envia um pedido privado à equipa. Para tua segurança, nunca incluas passwords, códigos, dados bancários ou números completos de cartão.' : 'Send a private request to the team. For your safety, never include passwords, codes, bank credentials or full card numbers.'}</p></div>
          <LifeBuoy size={78} aria-hidden="true" />
        </section>

        <section className="support-categories" aria-label={pt ? 'Tipos de suporte' : 'Support types'}>
          {categories.map(({ icon: Icon, title, text }) => <article key={title}><Icon size={21} aria-hidden="true" /><h2>{title}</h2><p>{text}</p></article>)}
        </section>

        <section className="support-layout">
          <div className="support-guidance">
            <p className="eyebrow">{pt ? 'Antes de enviar' : 'Before sending'}</p>
            <h2>{pt ? 'Dá-nos os factos essenciais.' : 'Give us the essential facts.'}</h2>
            <ul>
              <li>{pt ? 'Usa o teu nome Minecraft exato.' : 'Use your exact Minecraft username.'}</li>
              <li>{pt ? 'Indica a data, hora e o que aconteceu.' : 'Include the date, time and what happened.'}</li>
              <li>{pt ? 'Para compras, inclui apenas a referência Tip4Serv.' : 'For purchases, include only the Tip4Serv reference.'}</li>
              <li>{pt ? 'Podes acrescentar uma ligação HTTPS para prova.' : 'You may add an HTTPS evidence link.'}</li>
            </ul>
            <div className="support-warning"><AlertTriangle size={19} aria-hidden="true" /><p>{pt ? 'Os pedidos falsos ou abusivos podem ser ignorados. Uma compra nunca influencia uma decisão de moderação.' : 'False or abusive reports may be ignored. A purchase never affects a moderation decision.'}</p></div>
            <a href="mailto:support@merelymesmp.com"><Mail size={16} aria-hidden="true" /> support@merelymesmp.com</a>
          </div>

          <form className="support-form" onSubmit={submit}>
            <label>{pt ? 'Tipo de pedido' : 'Request type'}
              <select name="type" value={type} onChange={(event) => setType(event.target.value)} required>
                <option value="general">{pt ? 'Questão geral' : 'General question'}</option>
                <option value="player-report">{pt ? 'Denunciar jogador' : 'Report a player'}</option>
                <option value="appeal">{pt ? 'Recorrer de punição' : 'Appeal a sanction'}</option>
                <option value="purchase">{pt ? 'Problema com compra' : 'Purchase problem'}</option>
                <option value="technical">{pt ? 'Problema técnico' : 'Technical problem'}</option>
                <option value="staff">{pt ? 'Candidatura a staff' : 'Staff application'}</option>
              </select>
            </label>
            <div className="form-row">
              <label>{pt ? 'Nome Minecraft' : 'Minecraft username'}<input name="playerName" autoComplete="off" minLength={2} maxLength={32} pattern="[A-Za-z0-9_]{2,32}" required /></label>
              <label>Email<input name="email" type="email" autoComplete="email" maxLength={180} required /></label>
            </div>
            {type === 'purchase' && <label>{pt ? 'Referência Tip4Serv' : 'Tip4Serv reference'}<input name="orderReference" maxLength={120} required /></label>}
            <label>{pt ? 'Assunto' : 'Subject'}<input name="subject" maxLength={180} required /></label>
            <label>{pt ? 'Mensagem' : 'Message'}<textarea name="message" minLength={20} maxLength={5000} rows={8} required /></label>
            <label>{pt ? 'Ligação para prova (opcional)' : 'Evidence link (optional)'}<input name="evidenceUrl" type="url" inputMode="url" placeholder="https://" maxLength={500} /></label>
            <label className="honeypot" aria-hidden="true">Website<input name="website" tabIndex={-1} autoComplete="off" /></label>
            <label className="consent-field"><input name="consent" type="checkbox" required /> <span>{pt ? 'Autorizo o uso destes dados apenas para analisar e responder ao pedido, de acordo com a ' : 'I allow these details to be used only to review and answer this request, under the '}<Link href={localePath(locale, '/privacy')}>{pt ? 'política de privacidade' : 'privacy policy'}</Link>.</span></label>
            <button className="button button-primary" type="submit" disabled={state === 'sending'}>{state === 'sending' ? (pt ? 'A enviar…' : 'Sending…') : (pt ? 'Enviar pedido' : 'Send request')}</button>
            <div className={`form-feedback ${state}`} aria-live="polite">
              {state === 'sent' && <><CheckCircle2 size={19} aria-hidden="true" /><span>{pt ? 'Pedido recebido. Guarda a referência:' : 'Request received. Keep this reference:'} <strong>{reference}</strong></span></>}
              {state === 'error' && <><AlertTriangle size={19} aria-hidden="true" /><span>{message}</span></>}
            </div>
          </form>
        </section>
      </main>
      <SiteFooter locale={locale} />
    </>
  );
}
