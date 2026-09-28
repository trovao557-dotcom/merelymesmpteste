import { communityApiUrl } from '@/lib/community';

export async function GET(_request: Request, context: { params: Promise<{ uuid: string }> }) {
  const { uuid } = await context.params;
  if (!/^[0-9a-f-]{32,36}$/i.test(uuid)) {
    return Response.json({ error: 'Invalid player identifier.' }, { status: 400 });
  }

  try {
    const response = await fetch(`${communityApiUrl}/api/players/${encodeURIComponent(uuid)}`, {
      headers: { Accept: 'application/json' },
      signal: AbortSignal.timeout(8_000),
    });
    return Response.json(await response.json(), {
      status: response.status,
      headers: { 'Cache-Control': response.ok ? 'public, max-age=30, s-maxage=60' : 'no-store' },
    });
  } catch {
    return Response.json({ error: 'Player profile is temporarily unavailable.' }, { status: 502 });
  }
}
