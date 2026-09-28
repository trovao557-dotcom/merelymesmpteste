import type { Metadata } from 'next';
import { ParentsPage } from '@/components/parents-page';

export const metadata: Metadata = {
  title: 'Para pais e responsáveis — MerelyMeSMP',
  description: 'Informação de segurança, moderação e compras para pais e responsáveis do MerelyMeSMP.',
  alternates: { canonical: '/pt/parents', languages: { en: '/parents', 'pt-PT': '/pt/parents' } },
};

export default function PortugueseParents() { return <ParentsPage locale="pt" />; }
