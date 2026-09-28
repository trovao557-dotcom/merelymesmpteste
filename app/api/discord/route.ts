import { communityApiUrl } from '@/lib/community';
import { defaultContent } from '@/railway/content.mjs';

export async function GET() {
  try {
    const response = await fetch(`${communityApiUrl}/api/discord`, {
      headers: { Accept: 'application/json' },
      signal: AbortSignal.timeout(8_000),
    });
    if (!response.ok) throw new Error(`Discord API returned ${response.status}`);
    const discord = await response.json() as Record<string, unknown>;
    return Response.json({ ...discord, ...defaultContent.discord }, {
      headers: { 'Cache-Control': 'public, max-age=60, s-maxage=120, stale-while-revalidate=300' },
    });
  } catch {
    return Response.json({ ...defaultContent.discord, name: null, online: null }, { status: 200 });
  }
}
