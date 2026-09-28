import type { Metadata } from 'next';
import { ParentsPage } from '@/components/parents-page';

export const metadata: Metadata = {
  title: 'For parents — MerelyMeSMP',
  description: 'Safety, moderation and purchase information for MerelyMeSMP parents and guardians.',
  alternates: { canonical: '/parents', languages: { en: '/parents', 'pt-PT': '/pt/parents' } },
};

export default function Parents() { return <ParentsPage locale="en" />; }
