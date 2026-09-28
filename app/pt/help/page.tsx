import type { Metadata } from 'next';
import { HelpCentre } from '@/components/help-centre';

export const metadata: Metadata = {
  title: 'Centro de ajuda — MerelyMeSMP',
  description: 'Ajuda verificada para entrar no MerelyMeSMP, compras, segurança, moderação, pais e estado dos serviços.',
  alternates: { canonical: '/pt/help', languages: { en: '/help', 'pt-PT': '/pt/help' } },
};

export default function PortugueseHelpPage() {
  return <HelpCentre locale="pt" />;
}
