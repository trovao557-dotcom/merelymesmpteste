import assert from 'node:assert/strict';
import test from 'node:test';
import { normaliseTip4ServPrices } from '../lib/tip4serv-products.ts';

test('normalises prices and ignores malformed products', () => {
  assert.deepEqual(
    normaliseTip4ServPrices({
      products: [
        { slug: 'prime-kit', price: 17.99, old_price: 19.99, percent_off: 10 },
        { slug: 'points', price: '4.99', old_price: null, percent_off: 0 },
        { slug: 'broken', price: 'not-a-number' },
      ],
    }),
    {
      'prime-kit': { price: 17.99, oldPrice: 19.99, percentOff: 10 },
      points: { price: 4.99, oldPrice: null, percentOff: 0 },
    },
  );
});
