import { communityApiUrl } from '@/lib/community';

export async function POST(request: Request) {
  try {
    const length = Number(request.headers.get('content-length')) || 0;
    if (length > 64_000) return Response.json({ error: 'Request is too large.' }, { status: 413 });
    const payload = await request.json();
    const response = await fetch(`${communityApiUrl}/api/submissions`, {
      method: 'POST',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
      signal: AbortSignal.timeout(10_000),
    });
    return Response.json(await response.json(), {
      status: response.status,
      headers: { 'Cache-Control': 'no-store' },
    });
  } catch {
    return Response.json({ error: 'Support is temporarily unavailable.' }, { status: 502 });
  }
}
