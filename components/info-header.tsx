import { SiteHeader } from '@/components/site-header';
import type { Locale } from '@/lib/site-content';

export function InfoHeader({ locale = 'en', switchHref }: { locale?: Locale; switchHref?: string }) {
  return <SiteHeader locale={locale} switchHref={switchHref} />;
}
