import { communityApiUrl } from '@/lib/community';
import { defaultContent } from '@/railway/content.mjs';

export async function GET() {
  try {
    const response = await fetch(`${communityApiUrl}/api/community`, {
      headers: { Accept: 'application/json' },
      signal: AbortSignal.timeout(8_000),
    });
    if (!response.ok) throw new Error(`Community API returned ${response.status}`);
    const community = await response.json() as Record<string, unknown>;
    return Response.json({ ...community, season: defaultContent.season, events: [] }, {
      headers: { 'Cache-Control': 'public, max-age=20, s-maxage=45, stale-while-revalidate=180' },
    });
  } catch {
    return Response.json(
      { error: 'Community statistics are temporarily unavailable.' },
      { status: 502, headers: { 'Cache-Control': 'no-store' } },
    );
  }
}
