import { defaultContent } from '@/railway/content.mjs';

export async function GET(request: Request) {
  let destination = new URL('/community#discord', request.url);
  try {
    const { discord } = defaultContent;
    const invite = new URL(discord.inviteUrl);
    const isOfficialInvite = invite.protocol === 'https:' && !invite.username && !invite.password && !invite.port && (
      (invite.hostname === 'discord.gg' && /^\/[A-Za-z0-9-]+\/?$/.test(invite.pathname)) ||
      (invite.hostname === 'discord.com' && /^\/invite\/[A-Za-z0-9-]+\/?$/.test(invite.pathname))
    );
    if (isOfficialInvite) destination = invite;
  } catch {
    // Keep the link useful when the invite is missing or the content API is down.
  }
  return new Response(null, {
    status: 302,
    headers: { Location: destination.href, 'Cache-Control': 'no-store' },
  });
}
