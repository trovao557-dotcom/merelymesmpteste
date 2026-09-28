import type { Metadata } from 'next';
import { CommunityHub } from '@/components/community-hub';

export const metadata: Metadata = {
  title: 'Comunidade — MerelyMeSMP',
  description: 'Atividade, perfis de jogadores e comunidade Discord do MerelyMeSMP.',
  alternates: { canonical: '/pt/community', languages: { en: '/community', 'pt-PT': '/pt/community' } },
};

export default function PortugueseCommunityPage() { return <CommunityHub locale="pt" />; }
