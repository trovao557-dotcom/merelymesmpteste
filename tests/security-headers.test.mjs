import assert from 'node:assert/strict';
import test from 'node:test';
import nextConfig from '../next.config.ts';

const requiredHeaders = [
  'Content-Security-Policy',
  'Permissions-Policy',
  'Referrer-Policy',
  'Strict-Transport-Security',
  'X-Content-Type-Options',
  'X-Frame-Options',
];

test('applies the required security headers to every route', async () => {
  const rules = await nextConfig.headers();
  assert.deepEqual(rules.map((rule) => rule.source), ['/', '/:path*']);

  for (const rule of rules) {
    const headers = Object.fromEntries(rule.headers.map(({ key, value }) => [key, value]));
    for (const name of requiredHeaders) {
      assert.ok(headers[name], `${name} is missing from ${rule.source}`);
    }

    assert.match(headers['Content-Security-Policy'], /default-src 'self'/);
    assert.match(headers['Content-Security-Policy'], /frame-ancestors 'none'/);
    assert.match(headers['Content-Security-Policy'], /object-src 'none'/);
  }
});
