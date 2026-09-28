'use client';

import { useState } from 'react';
import { CheckCheck, Copy } from 'lucide-react';
import { track } from '@/lib/analytics';
import type { Locale } from '@/lib/site-content';

const serverIp = 'merelymesmp.com';

export function CopyServerButton({ className = 'button button-primary', locale = 'en' }: { className?: string; locale?: Locale }) {
  const [copied, setCopied] = useState(false);
  const pt = locale === 'pt';

  async function copy() {
    try {
      await navigator.clipboard.writeText(serverIp);
      setCopied(true);
      track('copy_ip');
      window.setTimeout(() => setCopied(false), 1800);
    } catch {
      window.prompt(pt ? 'Copia o IP do servidor:' : 'Copy the server IP:', serverIp);
    }
  }

  return (
    <button className={className} type="button" onClick={copy}>
      {copied ? <CheckCheck size={18} aria-hidden="true" /> : <Copy size={18} aria-hidden="true" />}
      {copied ? (pt ? 'IP copiado' : 'IP copied') : serverIp}
      <span className="sr-only" aria-live="polite">{copied ? (pt ? 'Endereço do servidor copiado.' : 'Server address copied.') : ''}</span>
    </button>
  );
}
