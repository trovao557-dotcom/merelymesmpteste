import type { Metadata } from 'next';
import { CommunityHub } from '@/components/community-hub';

export const metadata: Metadata = {
  title: 'Community — MerelyMeSMP',
  description: 'MerelyMeSMP live activity, player profiles and Discord community.',
  alternates: { canonical: '/community', languages: { en: '/community', 'pt-PT': '/pt/community' } },
};

export default function CommunityPage() { return <CommunityHub locale="en" />; }
