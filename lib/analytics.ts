'use client';

export type AnalyticsEvent =
  | 'copy_ip'
  | 'store_click'
  | 'discord_click'
  | 'support_submit'
  | 'language_switch'
  | 'join_guide';

export function track(event: AnalyticsEvent) {
  try {
    void fetch('/api/analytics', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ event }),
      keepalive: true,
    });
  } catch {}
}
