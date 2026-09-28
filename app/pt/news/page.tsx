import type { Metadata } from 'next';
import { NewsIndex } from '@/components/news-pages';

export const dynamic = 'force-dynamic';
export const metadata: Metadata = {
  title: 'Notícias — MerelyMeSMP',
  description: 'Atualizações e manutenções oficiais do MerelyMeSMP.',
  alternates: { canonical: '/pt/news', languages: { en: '/news', 'pt-PT': '/pt/news' } },
};

export default function PortugueseNewsPage() { return <NewsIndex locale="pt" />; }
