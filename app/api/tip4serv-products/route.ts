import { normaliseTip4ServPrices } from '@/lib/tip4serv-products';

const productsUrl = 'https://api.tip4serv.com/v1/store/products?max_page=50&only_enabled=true&currency=EUR';

export async function GET() {
  const apiKey = process.env.TIP4SERV_API_KEY?.trim();

  if (!apiKey) {
    return Response.json(
      { error: 'Price sync is not configured.' },
      { status: 503, headers: { 'Cache-Control': 'no-store' } },
    );
  }

  try {
    const response = await fetch(productsUrl, {
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${apiKey}`,
      },
      signal: AbortSignal.timeout(8_000),
    });

    if (!response.ok) throw new Error(`Tip4Serv returned ${response.status}`);

    const prices = normaliseTip4ServPrices(await response.json());
    return Response.json(
      { currency: 'EUR', prices },
      {
        headers: {
          'Cache-Control': 'public, max-age=60, s-maxage=300, stale-while-revalidate=3600',
        },
      },
    );
  } catch {
    return Response.json(
      { error: 'Live prices are temporarily unavailable.' },
      { status: 502, headers: { 'Cache-Control': 'no-store' } },
    );
  }
}
