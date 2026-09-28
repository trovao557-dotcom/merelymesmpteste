import type { Metadata } from 'next';
import { NewsArticle } from '@/components/news-pages';
import { fetchSiteContent } from '@/lib/site-content';

export const dynamic = 'force-dynamic';

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params;
  try {
    const post = (await fetchSiteContent()).news.find((entry) => entry.slug === slug && entry.status === 'published');
    if (post) {
      const title = `${post.title} — MerelyMeSMP`;
      return { title, description: post.summary, alternates: { canonical: `/news/${slug}`, languages: { en: `/news/${slug}`, 'pt-PT': `/pt/news/${slug}` } }, openGraph: { title, description: post.summary, type: 'article', publishedTime: post.publishedAt, modifiedTime: post.updatedAt, images: [] }, twitter: { card: 'summary', title, description: post.summary, images: [] } };
    }
  } catch {}
  return { title: 'News — MerelyMeSMP' };
}

export default async function NewsPostPage({ params }: { params: Promise<{ slug: string }> }) {
  return <NewsArticle locale="en" slug={(await params).slug} />;
}
