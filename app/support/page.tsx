import type { Metadata } from 'next';
import { SupportPage } from '@/components/support-page';

export const metadata: Metadata = {
  title: 'Support — MerelyMeSMP',
  description: 'Private MerelyMeSMP support, player reports, appeals and purchase help.',
  alternates: { canonical: '/support', languages: { en: '/support', 'pt-PT': '/pt/support' } },
};

export default function Support() { return <SupportPage locale="en" />; }
