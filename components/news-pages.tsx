import Link from '@/components/site-link';
import { ArrowLeft, ArrowRight, CalendarDays, Newspaper } from 'lucide-react';
import { InfoHeader } from '@/components/info-header';
import { SiteFooter } from '@/components/site-footer';
import {
  fetchSiteContent,
  formatSiteDate,
  localePath,
  localised,
  type Locale,
  type NewsPost,
} from '@/lib/site-content';

function published(posts: NewsPost[]) {
  return posts.filter((post) => post.status === 'published');
}

export async function NewsIndex({ locale }: { locale: Locale }) {
  const pt = locale === 'pt';
  let posts: NewsPost[] = [];
  try { posts = published((await fetchSiteContent()).news); } catch {}

  return (
    <>
      <InfoHeader locale={locale} switchHref={pt ? '/news' : '/pt/news'} />
      <main className="info-shell news-page" lang={pt ? 'pt-PT' : 'en'}>
        <section className="info-hero">
          <p className="eyebrow">{pt ? 'Centro oficial de atualizações' : 'Official update centre'}</p>
          <h1>{pt ? 'Notícias' : 'News'}</h1>
          <p>{pt ? 'As notícias oficiais do MerelyMeSMP.' : 'Official news from MerelyMeSMP.'}</p>
        </section>
        <section className="news-grid" aria-label={pt ? 'Notícias publicadas' : 'Published news'}>
          {posts.map((post, index) => (
            <article className={index === 0 ? 'news-card featured-news' : 'news-card'} key={post.id}>
              <div><span>{localised(locale, post.category, post.categoryPt)}</span><time dateTime={post.publishedAt}>{formatSiteDate(post.publishedAt, locale)}</time></div>
              <Newspaper size={index === 0 ? 30 : 23} aria-hidden="true" />
              <h2>{localised(locale, post.title, post.titlePt)}</h2>
              <p>{localised(locale, post.summary, post.summaryPt)}</p>
              <Link href={localePath(locale, `/news/${post.slug}`)}>{pt ? 'Ler notícia' : 'Read update'} <ArrowRight size={15} aria-hidden="true" /></Link>
            </article>
          ))}
          {!posts.length && <p className="empty-state">{pt ? 'As notícias estão temporariamente indisponíveis.' : 'News is temporarily unavailable.'}</p>}
        </section>
      </main>
      <SiteFooter locale={locale} />
    </>
  );
}

export async function NewsArticle({ locale, slug }: { locale: Locale; slug: string }) {
  const pt = locale === 'pt';
  let post: NewsPost | undefined;
  try { post = published((await fetchSiteContent()).news).find((entry) => entry.slug === slug); } catch {}

  if (!post) {
    return (
      <>
        <InfoHeader locale={locale} switchHref={pt ? `/news/${slug}` : `/pt/news/${slug}`} />
        <main className="info-shell article-missing">
          <Newspaper size={32} aria-hidden="true" />
          <h1>{pt ? 'Notícia não encontrada' : 'Update not found'}</h1>
          <p>{pt ? 'A publicação pode ter sido retirada ou estar temporariamente indisponível.' : 'The post may have been removed or is temporarily unavailable.'}</p>
          <Link className="button button-primary" href={localePath(locale, '/news')}>{pt ? 'Voltar às notícias' : 'Back to news'}</Link>
        </main>
        <SiteFooter locale={locale} />
      </>
    );
  }

  const body = localised(locale, post.body, post.bodyPt).split(/\n\s*\n/).filter(Boolean);
  const structuredData = {
    '@context': 'https://schema.org',
    '@type': 'NewsArticle',
    headline: localised(locale, post.title, post.titlePt),
    description: localised(locale, post.summary, post.summaryPt),
    datePublished: post.publishedAt,
    dateModified: post.updatedAt,
    inLanguage: pt ? 'pt-PT' : 'en',
    mainEntityOfPage: `https://merelymesmp.com${localePath(locale, `/news/${post.slug}`)}`,
    author: { '@type': 'Organization', name: 'MerelyMeSMP' },
    publisher: { '@type': 'Organization', name: 'MerelyMeSMP', logo: { '@type': 'ImageObject', url: 'https://merelymesmp.com/images/logo.png' } },
    image: 'https://merelymesmp.com/images/logo.png',
  };
  return (
    <>
      <InfoHeader locale={locale} switchHref={pt ? `/news/${slug}` : `/pt/news/${slug}`} />
      <main className="info-shell article-page" lang={pt ? 'pt-PT' : 'en'}>
        <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(structuredData).replace(/</g, '\\u003c') }} />
        <Link className="article-back" href={localePath(locale, '/news')}><ArrowLeft size={15} aria-hidden="true" /> {pt ? 'Todas as notícias' : 'All news'}</Link>
        <article>
          <header>
            <p className="eyebrow">{localised(locale, post.category, post.categoryPt)}</p>
            <h1>{localised(locale, post.title, post.titlePt)}</h1>
            <div><CalendarDays size={15} aria-hidden="true" /><time dateTime={post.publishedAt}>{formatSiteDate(post.publishedAt, locale)}</time></div>
          </header>
          <p className="article-summary">{localised(locale, post.summary, post.summaryPt)}</p>
          <div className="article-body">{body.map((paragraph) => <p key={paragraph}>{paragraph}</p>)}</div>
        </article>
      </main>
      <SiteFooter locale={locale} />
    </>
  );
}
