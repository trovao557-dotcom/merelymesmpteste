import { defaultContent } from '@/railway/content.mjs';

export async function GET() {
  return Response.json(defaultContent, {
    headers: { 'Cache-Control': 'public, max-age=30, s-maxage=60, stale-while-revalidate=300' },
  });
}
