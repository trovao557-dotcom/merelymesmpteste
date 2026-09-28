import type { Metadata } from 'next';
import { SupportPage } from '@/components/support-page';

export const metadata: Metadata = {
  title: 'Suporte — MerelyMeSMP',
  description: 'Suporte privado, denúncias, recursos e ajuda com compras MerelyMeSMP.',
  alternates: { canonical: '/pt/support', languages: { en: '/support', 'pt-PT': '/pt/support' } },
};

export default function PortugueseSupport() { return <SupportPage locale="pt" />; }
