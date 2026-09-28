import { communityApiUrl, type ServerStatus } from '@/lib/community';

export type Locale = 'en' | 'pt';

export type SiteEvent = {
  id: string;
  title: string;
  titlePt: string;
  status: string;
  schedule: string;
  schedulePt: string;
  description: string;
  descriptionPt: string;
  startsAt: string | null;
};

export type NewsPost = {
  id: string;
  slug: string;
  status: 'draft' | 'published';
  category: string;
  categoryPt: string;
  title: string;
  titlePt: string;
  summary: string;
  summaryPt: string;
  body: string;
  bodyPt: string;
  publishedAt: string;
  updatedAt: string;
};

export type SiteContent = {
  announcement: {
    enabled: boolean;
    title: string;
    titlePt: string;
    message: string;
    messagePt: string;
    ctaLabel: string;
    ctaLabelPt: string;
    ctaHref: string;
  };
  season: {
    id: string;
    name: string;
    namePt: string;
    status: string;
    label: string;
    labelPt: string;
    description: string;
    descriptionPt: string;
  };
  events: SiteEvent[];
  news: NewsPost[];
  faqs: Array<{
    id: string;
    question: string;
    questionPt: string;
    answer: string;
    answerPt: string;
  }>;
  discord: { inviteUrl: string; guildId: string };
};

export type StatusPagePayload = {
  minecraft: ServerStatus | null;
  api: { online: boolean; checkedAt: string };
  backup?: { lastSuccessfulDay: string | null; retentionDays: number; healthy: boolean };
  days: Array<{ date: string; uptimePercent: number | null; checks: number }>;
  incidents: Array<{
    id: string;
    service: string;
    title: string;
    status: string;
    startedAt: string;
    endedAt: string | null;
  }>;
  lastUpdatedAt: string | null;
};

export const prefix = (locale: Locale) => (locale === 'pt' ? '/pt' : '');

export function localePath(locale: Locale, path = '/') {
  if (/^https:\/\//i.test(path)) return path;
  if (locale === 'en') return path;
  return path === '/' ? '/pt' : `/pt${path}`;
}

export function localised(locale: Locale, english: string, portuguese: string) {
  return locale === 'pt' ? portuguese || english : english;
}

export function formatSiteDate(value: string, locale: Locale, includeTime = false) {
  return new Intl.DateTimeFormat(locale === 'pt' ? 'pt-PT' : 'en-GB', {
    dateStyle: 'medium',
    ...(includeTime ? { timeStyle: 'short' as const } : {}),
    timeZone: 'Europe/Lisbon',
  }).format(new Date(value));
}

export async function fetchSiteContent(): Promise<SiteContent> {
  const { defaultContent } = await import('@/railway/content.mjs');
  return defaultContent as SiteContent;
}
