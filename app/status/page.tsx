import type { Metadata } from 'next';
import { StatusPage } from '@/components/status-page';

export const metadata: Metadata = {
  title: 'Service status — MerelyMeSMP',
  description: 'Live MerelyMeSMP Minecraft, website, data and store status.',
  alternates: { canonical: '/status', languages: { en: '/status', 'pt-PT': '/pt/status' } },
};

export default function Status() { return <StatusPage locale="en" />; }
