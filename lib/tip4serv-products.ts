export type Tip4ServPrice = {
  price: number;
  oldPrice: number | null;
  percentOff: number;
};

export type Tip4ServPrices = Record<string, Tip4ServPrice>;

type ProductPayload = {
  slug?: unknown;
  price?: unknown;
  old_price?: unknown;
  percent_off?: unknown;
};

function finiteNumber(value: unknown) {
  const number = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(number) ? number : null;
}

export function normaliseTip4ServPrices(payload: unknown): Tip4ServPrices {
  if (!payload || typeof payload !== 'object') return {};

  const products = (payload as { products?: unknown }).products;
  if (!Array.isArray(products)) return {};

  return products.reduce<Tip4ServPrices>((prices, product: ProductPayload) => {
    if (!product || typeof product !== 'object' || typeof product.slug !== 'string') return prices;

    const price = finiteNumber(product.price);
    if (price === null || price < 0) return prices;

    const oldPrice = finiteNumber(product.old_price);
    const percentOff = finiteNumber(product.percent_off);

    prices[product.slug] = {
      price,
      oldPrice: oldPrice !== null && oldPrice > price ? oldPrice : null,
      percentOff: percentOff !== null && percentOff > 0 ? Math.round(percentOff) : 0,
    };
    return prices;
  }, {});
}
