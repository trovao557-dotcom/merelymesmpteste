import type { Metadata } from 'next';
import { NewsArticle } from '@/components/news-pages';
import { fetchSiteContent } from '@/lib/site-content';

export const dynamic = 'force-dynamic';

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params;
  try {
    const post = (await fetchSiteContent()).news.find((entry) => entry.slug === slug && entry.status === 'published');
    if (post) {
      const title = `${post.titlePt || post.title} — MerelyMeSMP`;
      const description = post.summaryPt || post.summary;
      return { title, description, alternates: { canonical: `/pt/news/${slug}`, languages: { en: `/news/${slug}`, 'pt-PT': `/pt/news/${slug}` } }, openGraph: { title, description, type: 'article', publishedTime: post.publishedAt, modifiedTime: post.updatedAt, images: [] }, twitter: { card: 'summary', title, description, images: [] } };
    }
  } catch {}
  return { title: 'Notícias — MerelyMeSMP' };
}

export default async function PortugueseNewsPostPage({ params }: { params: Promise<{ slug: string }> }) {
  return <NewsArticle locale="pt" slug={(await params).slug} />;
}
