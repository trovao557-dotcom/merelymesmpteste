import { communityApiUrl, normalisePublicStatus } from '@/lib/community';

const publicStatusUrl = 'https://api.mcsrvstat.us/3/merelymesmp.com';

export async function GET() {
  try {
    const response = await fetch(`${communityApiUrl}/api/status`, {
      headers: { Accept: 'application/json' },
      signal: AbortSignal.timeout(6_000),
    });
    if (response.ok) {
      const status = await response.json() as Record<string, unknown>;
      delete status.version;
      delete status.software;
      return Response.json(status, {
        headers: { 'Cache-Control': 'public, max-age=15, s-maxage=45, stale-while-revalidate=180' },
      });
    }
  } catch {}

  try {
    const response = await fetch(publicStatusUrl, {
      headers: { Accept: 'application/json' },
      signal: AbortSignal.timeout(8_000),
    });
    const status = response.ok ? normalisePublicStatus(await response.json()) : null;
    if (!status) throw new Error('Invalid server status');
    return Response.json(status, {
      headers: { 'Cache-Control': 'public, max-age=15, s-maxage=60, stale-while-revalidate=300' },
    });
  } catch {
    return Response.json(
      { error: 'Live server status is temporarily unavailable.' },
      { status: 502, headers: { 'Cache-Control': 'no-store' } },
    );
  }
}
