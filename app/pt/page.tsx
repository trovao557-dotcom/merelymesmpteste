import type { Metadata } from 'next';
import { StoreHome } from '@/components/store-home';

export const metadata: Metadata = {
  title: 'MerelyMeSMP — Mace PvP puro',
  description: 'Loja e comunidade oficial do MerelyMeSMP. Ranks, pontos e notícias.',
  alternates: { canonical: '/pt', languages: { en: '/', 'pt-PT': '/pt' } },
};

export default function PortugueseHome() {
  return <StoreHome locale="pt" />;
}
