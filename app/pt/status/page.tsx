import type { Metadata } from 'next';
import { StatusPage } from '@/components/status-page';

export const metadata: Metadata = {
  title: 'Estado dos serviços — MerelyMeSMP',
  description: 'Estado em direto do Minecraft, website, dados e loja MerelyMeSMP.',
  alternates: { canonical: '/pt/status', languages: { en: '/status', 'pt-PT': '/pt/status' } },
};

export default function PortugueseStatus() { return <StatusPage locale="pt" />; }
