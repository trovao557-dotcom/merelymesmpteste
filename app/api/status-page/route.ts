import { communityApiUrl } from '@/lib/community';

export async function GET() {
  try {
    const response = await fetch(`${communityApiUrl}/api/status-page`, {
      headers: { Accept: 'application/json' },
      signal: AbortSignal.timeout(8_000),
    });
    if (!response.ok) throw new Error(`Status API returned ${response.status}`);
    return Response.json(await response.json(), {
      headers: { 'Cache-Control': 'public, max-age=20, s-maxage=45, stale-while-revalidate=180' },
    });
  } catch {
    return Response.json({ error: 'Status history is temporarily unavailable.' }, { status: 502 });
  }
}
