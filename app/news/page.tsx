import type { Metadata } from 'next';
import { NewsIndex } from '@/components/news-pages';

export const dynamic = 'force-dynamic';
export const metadata: Metadata = {
  title: 'News — MerelyMeSMP',
  description: 'Official MerelyMeSMP updates and maintenance news.',
  alternates: { canonical: '/news', languages: { en: '/news', 'pt-PT': '/pt/news' } },
};

export default function NewsPage() { return <NewsIndex locale="en" />; }
