import type { Metadata } from 'next';
import { Geist, Geist_Mono } from 'next/font/google';
import './globals.css';

const geist = Geist({ variable: '--font-geist', subsets: ['latin'] });
const geistMono = Geist_Mono({ variable: '--font-geist-mono', subsets: ['latin'] });

export const metadata: Metadata = {
  metadataBase: new URL('https://merelymesmp.com'),
  title: { default: 'MerelyMeSMP', template: '%s' },
  description: 'Official MerelyMeSMP hub: live Minecraft status, join guide, community activity, server rules and Tip4Serv store.',
  manifest: '/site.webmanifest',
  icons: {
    icon: [
      { url: '/merelysmp-favicon.ico', sizes: 'any' },
      { url: '/merelysmp-icon-16.png', sizes: '16x16', type: 'image/png' },
      { url: '/merelysmp-icon-32.png', sizes: '32x32', type: 'image/png' },
    ],
    shortcut: '/merelysmp-favicon.ico',
    apple: [{ url: '/merelysmp-apple-touch-icon.png', sizes: '180x180', type: 'image/png' }],
  },
  applicationName: 'MerelyMeSMP',
  category: 'gaming',
  alternates: { canonical: '/', languages: { en: '/', 'pt-PT': '/pt' } },
  robots: { index: true, follow: true },
  openGraph: {
    title: 'MerelyMeSMP',
    description: 'Join MerelyMeSMP, check live status, follow community activity and use the official Tip4Serv store.',
    url: '/',
    siteName: 'MerelyMeSMP',
    type: 'website',
    images: [{ url: '/images/logo.png', width: 2000, height: 2000, alt: 'Merely SMP' }],
  },
  twitter: {
    card: 'summary',
    title: 'MerelyMeSMP',
    description: 'Enter, fight and leave your mark.',
    images: ['/images/logo.png'],
  },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  const structuredData = {
    '@context': 'https://schema.org',
    '@type': 'WebSite',
    name: 'MerelyMeSMP',
    alternateName: 'MerelyMe SMP',
    url: 'https://merelymesmp.com',
    inLanguage: ['en', 'pt-PT'],
  };
  return (
    <html lang="en">
      <head>
        <script dangerouslySetInnerHTML={{ __html: "document.documentElement.lang=(location.pathname==='/pt'||location.pathname.startsWith('/pt/'))?'pt-PT':'en'" }} />
      </head>
      <body className={`${geist.variable} ${geistMono.variable}`}>
        <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(structuredData).replace(/</g, '\\u003c') }} />
        {children}
      </body>
    </html>
  );
}
