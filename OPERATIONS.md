# MerelyMeSMP operations

## What is protected automatically

- The website source is versioned in Git. A previous commit can be rebuilt and deployed if a release causes a regression.
- The Railway community service stores status and activity data on the persistent `/data` volume.
- Railway stores status history, privacy-safe aggregate analytics and private support requests on the same persistent volume.
- Railway creates one JSON snapshot per day in `/data/backups` and keeps the latest 14 snapshots. Support submissions older than 180 days are removed automatically.
- The Railway community service checks Minecraft reachability every minute and records outages and recoveries. No separate Codex automation is used.

## Recovery order

1. Confirm whether the incident affects the website, Railway API, or Minecraft server.
2. Preserve the current state before replacing or restoring anything.
3. For the website, deploy a previously validated Sites version or revert the responsible Git commit.
4. For Railway data, select the latest valid `/data/backups/community-YYYY-MM-DD.json`, copy it to `/data/community.json`, and restart only the `community-api` service.
5. Verify `/health`, `/api/status`, and `/api/community` before declaring recovery complete.

## Publishing content

- Announcements, season text, events, FAQs, the Discord invite and news live in `railway/content.mjs` and are versioned in Git.
- The website bundles this public content so a website deployment always contains its matching announcements and news. Deploy the website after editing it.
- Deploy the `railway` directory separately, after running the Railway tests, when the public content API must also be synchronised for other clients.
- Never invent dates, rewards, Discord invitations or server statistics. Leave unknown values empty or marked as coming soon.

## Support and measurement

- Public forms write to the Railway volume; no support content is placed in browser storage or public logs.
- The internal support endpoint requires the Railway `ADMIN_TOKEN` and is intended for authorised agents, not a public dashboard.
- Website measurements store daily counts only for approved actions. They do not use advertising cookies, IP profiles or cross-site identifiers.

## Minecraft world backups

World saves belong to GPORTAL and are separate from the Railway snapshots. Enable scheduled GPORTAL world backups and verify that a recent restore point exists. Do not test a restore against the live world: stop the server, take a fresh snapshot, verify the selected backup timestamp, and use a test slot when one is available.

## Pending integrations

- Discord: add the official invite URL and guild ID only after the server owner provides them.
- Advanced player statistics: wait for a documented read-only HTTP API from the Minecraft plugins; never invent missing values.
- GPORTAL world backups: verify the schedule and a recent restore point before describing them as active.

## Public checks

- Website: `https://merelymesmp.com`
- Community service health: `https://community-api-production-6935.up.railway.app/health`
- Minecraft address: `merelymesmp.com`

Never put Stripe, Tip4Serv, Railway, GPORTAL, or SSH secrets in Git, screenshots, chat messages, or support tickets.
