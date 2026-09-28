import type { Metadata } from 'next';
import { HelpCentre } from '@/components/help-centre';

export const metadata: Metadata = {
  title: 'Help centre — MerelyMeSMP',
  description: 'Verified help for joining MerelyMeSMP, purchases, security, moderation, parents and live service data.',
  alternates: { canonical: '/help', languages: { en: '/help', 'pt-PT': '/pt/help' } },
};

export default function HelpPage() {
  return <HelpCentre locale="en" />;
}
