import { communityApiUrl } from '@/lib/community';

export async function POST(request: Request) {
  try {
    const payload = await request.json();
    const response = await fetch(`${communityApiUrl}/api/analytics`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ event: payload.event }),
      signal: AbortSignal.timeout(4_000),
    });
    return new Response(null, { status: response.ok ? 204 : response.status });
  } catch {
    return new Response(null, { status: 204 });
  }
}
