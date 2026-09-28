import type { MetadataRoute } from 'next';
import { fetchSiteContent } from '@/lib/site-content';

const base = 'https://merelymesmp.com';
const staticPaths = ['', '/community', '/bans', '/join', '/help', '/news', '/status', '/support', '/parents', '/terms', '/privacy', '/refunds', '/rules', '/contact'];

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const now = new Date();
  const entries: MetadataRoute.Sitemap = [];
  for (const path of staticPaths) {
    entries.push({ url: `${base}${path || '/'}`, lastModified: now, changeFrequency: path === '/status' ? 'hourly' : 'weekly', priority: path === '' ? 1 : 0.7, alternates: { languages: { en: `${base}${path || '/'}`, 'pt-PT': `${base}/pt${path}` } } });
    entries.push({ url: `${base}/pt${path}`, lastModified: now, changeFrequency: path === '/status' ? 'hourly' : 'weekly', priority: path === '' ? 0.9 : 0.7, alternates: { languages: { en: `${base}${path || '/'}`, 'pt-PT': `${base}/pt${path}` } } });
  }
  try {
    for (const post of (await fetchSiteContent()).news.filter((entry) => entry.status === 'published')) {
      const path = `/news/${post.slug}`;
      entries.push({ url: `${base}${path}`, lastModified: new Date(post.updatedAt), changeFrequency: 'monthly', priority: 0.65, alternates: { languages: { en: `${base}${path}`, 'pt-PT': `${base}/pt${path}` } } });
      entries.push({ url: `${base}/pt${path}`, lastModified: new Date(post.updatedAt), changeFrequency: 'monthly', priority: 0.65, alternates: { languages: { en: `${base}${path}`, 'pt-PT': `${base}/pt${path}` } } });
    }
  } catch {}
  return entries;
}
