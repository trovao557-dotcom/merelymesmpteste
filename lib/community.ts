export type ServerPlayer = {
  name: string;
  uuid: string;
};

export type ServerStatus = {
  online: boolean;
  onlinePlayers: number;
  maxPlayers: number;
  players: ServerPlayer[];
  motd: string;
  checkedAt: string;
};

export type CommunityPlayer = ServerPlayer & {
  rank: number;
  firstSeenAt: string;
  lastSeenAt: string;
  observedMinutes: number;
  checksSeen: number;
  online: boolean;
};

export type CommunityPayload = {
  status: ServerStatus | null;
  season: {
    id: string;
    name: string;
    namePt: string;
    status: string;
    label: string;
    labelPt: string;
    description: string;
    descriptionPt: string;
  };
  events: Array<{
    id: string;
    title: string;
    titlePt: string;
    status: string;
    schedule: string;
    schedulePt: string;
    description: string;
    descriptionPt: string;
    startsAt: string | null;
  }>;
  leaderboard: CommunityPlayer[];
  summary: {
    trackedPlayers: number;
    uptimePercent: number | null;
    trackingStartedAt: string;
    lastUpdatedAt: string | null;
  };
  note: string;
};

export const communityApiUrl =
  process.env.MERELYME_API_URL?.trim().replace(/\/$/, '') ||
  'https://community-api-production-6935.up.railway.app';

export function normalisePublicStatus(payload: unknown): ServerStatus | null {
  if (!payload || typeof payload !== 'object') return null;
  const value = payload as Record<string, unknown>;
  if (typeof value.online !== 'boolean') return null;

  const playerInfo = value.players as Record<string, unknown> | undefined;
  const rawList = Array.isArray(playerInfo?.list) ? playerInfo.list : [];
  const players = rawList.flatMap((entry) => {
    if (!entry || typeof entry !== 'object') return [];
    const player = entry as Record<string, unknown>;
    return typeof player.name === 'string' && typeof player.uuid === 'string'
      ? [{ name: player.name, uuid: player.uuid }]
      : [];
  });
  const motd = value.motd as Record<string, unknown> | undefined;

  return {
    online: value.online,
    onlinePlayers: Number(playerInfo?.online) || 0,
    maxPlayers: Number(playerInfo?.max) || 0,
    players,
    motd: Array.isArray(motd?.clean) ? motd.clean.filter((line) => typeof line === 'string').join(' · ') : '',
    checkedAt: new Date().toISOString(),
  };
}

export function formatObservedMinutes(minutes: number) {
  if (minutes < 1) return '< 1 min';
  if (minutes < 60) return `${Math.floor(minutes)} min`;
  const hours = Math.floor(minutes / 60);
  const remaining = Math.floor(minutes % 60);
  return remaining ? `${hours}h ${remaining}m` : `${hours}h`;
}
