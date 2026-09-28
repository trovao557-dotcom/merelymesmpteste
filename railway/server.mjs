import { randomUUID } from 'node:crypto';
import { createServer } from 'node:http';
import { mkdir, readFile, readdir, rename, unlink, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { pathToFileURL } from 'node:url';
import nodemailer from 'nodemailer';
import { defaultContent } from './content.mjs';

const address = process.env.MINECRAFT_ADDRESS?.trim() || 'merelymesmp.com';
const pollInterval = Math.max(30_000, Number(process.env.POLL_INTERVAL_MS) || 60_000);
const dataDirectory = process.env.DATA_DIR?.trim() || '.data';
const stateFile = join(dataDirectory, 'community.json');
const adminToken = process.env.ADMIN_TOKEN?.trim() || '';
const smtpHost = process.env.SMTP_HOST?.trim() || '';
const smtpPort = Math.max(1, Number(process.env.SMTP_PORT) || 465);
const smtpUser = process.env.SMTP_USER?.trim() || '';
const smtpPass = process.env.SMTP_PASS || '';
const smtpFrom = process.env.SMTP_FROM?.trim() || smtpUser;
const supportNotifyTo = process.env.SUPPORT_NOTIFY_TO?.trim() || '';
const allowedOrigins = new Set(
  (process.env.ALLOWED_ORIGINS || 'https://merelymesmp.com,http://localhost:3000')
    .split(',')
    .map((origin) => origin.trim())
    .filter(Boolean),
);

const eventStatuses = new Set(['available', 'coming-soon', 'registration-open', 'completed']);
const newsStatuses = new Set(['draft', 'published']);
const submissionStatuses = new Set(['new', 'in-progress', 'resolved']);
const submissionTypes = new Set(['appeal', 'player-report', 'purchase', 'staff', 'technical', 'general']);
const analyticsEvents = new Set([
  'copy_ip',
  'store_click',
  'discord_click',
  'support_submit',
  'language_switch',
  'join_guide',
]);
const supportRetentionMs = 180 * 24 * 60 * 60_000;
const backupRetentionDays = 30;

function text(value, maximum, fallback = '') {
  return typeof value === 'string' ? value.trim().slice(0, maximum) : fallback;
}

const supportMailer = smtpHost && smtpUser && smtpPass && smtpFrom && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(supportNotifyTo)
  ? nodemailer.createTransport({
      host: smtpHost,
      port: smtpPort,
      secure: smtpPort === 465,
      requireTLS: smtpPort !== 465,
      auth: { user: smtpUser, pass: smtpPass },
      connectionTimeout: 5_000,
      greetingTimeout: 5_000,
      socketTimeout: 10_000,
    })
  : null;

export function supportNotificationFromSubmission(submission) {
  const reference = text(submission?.id, 80, 'unknown');
  const type = text(submission?.type, 40, 'general').replace(/[\r\n]+/g, ' ');
  const playerName = text(submission?.playerName, 32, 'unknown').replace(/[\r\n]+/g, ' ');
  const subject = text(submission?.subject, 180, type).replace(/[\r\n]+/g, ' ');
  const createdAt = text(submission?.createdAt, 40, new Date().toISOString());

  return {
    subject: `[MerelyMeSMP] New ${type} request — ${reference}`,
    text: [
      'A new private support request was saved.',
      '',
      `Reference: ${reference}`,
      `Type: ${type}`,
      `Minecraft player: ${playerName}`,
      `Subject: ${subject}`,
      `Received: ${createdAt}`,
      '',
      'Open the private administration data to read the complete request.',
      'This notification deliberately excludes the message and evidence link.',
    ].join('\n'),
  };
}

async function sendSupportNotification(submission) {
  if (!supportMailer) return false;
  const notification = supportNotificationFromSubmission(submission);
  await supportMailer.sendMail({
    from: smtpFrom,
    to: supportNotifyTo,
    subject: notification.subject,
    text: notification.text,
  });
  return true;
}

function isoDate(value, fallback = null) {
  const parsed = typeof value === 'string' ? Date.parse(value) : NaN;
  return Number.isFinite(parsed) ? new Date(parsed).toISOString() : fallback;
}

function slug(value, fallback = 'item') {
  return (
    text(value, 100)
      .normalize('NFKD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/(^-|-$)/g, '')
      .slice(0, 80) || fallback
  );
}

function safeHref(value, fallback = '/') {
  const candidate = text(value, 300, fallback);
  return candidate.startsWith('/') || /^https:\/\//i.test(candidate) ? candidate : fallback;
}

function normaliseDiscordInvite(value) {
  const candidate = text(value, 300);
  return /^https:\/\/(discord\.gg|discord\.com\/invite)\/[a-z0-9-]+\/?$/i.test(candidate)
    ? candidate
    : '';
}

export function normaliseContent(payload) {
  const source = payload && typeof payload === 'object' ? payload : {};
  const announcementSource = source.announcement && typeof source.announcement === 'object'
    ? source.announcement
    : {};
  const seasonSource = source.season && typeof source.season === 'object' ? source.season : {};
  const discordSource = source.discord && typeof source.discord === 'object' ? source.discord : {};

  const events = (Array.isArray(source.events) ? source.events : defaultContent.events)
    .slice(0, 20)
    .flatMap((entry, index) => {
      if (!entry || typeof entry !== 'object') return [];
      const title = text(entry.title, 100);
      if (!title) return [];
      return [{
        id: slug(entry.id || title, `event-${index + 1}`),
        title,
        titlePt: text(entry.titlePt, 100, title),
        status: eventStatuses.has(entry.status) ? entry.status : 'coming-soon',
        schedule: text(entry.schedule, 120, 'Date to be announced'),
        schedulePt: text(entry.schedulePt, 120, 'Data a anunciar'),
        description: text(entry.description, 600),
        descriptionPt: text(entry.descriptionPt, 600, text(entry.description, 600)),
        startsAt: isoDate(entry.startsAt),
      }];
    });

  const news = (Array.isArray(source.news) ? source.news : defaultContent.news)
    .slice(0, 60)
    .flatMap((entry, index) => {
      if (!entry || typeof entry !== 'object') return [];
      const title = text(entry.title, 160);
      if (!title) return [];
      const publishedAt = isoDate(entry.publishedAt, new Date().toISOString());
      return [{
        id: slug(entry.id || entry.slug || title, `news-${index + 1}`),
        slug: slug(entry.slug || title, `news-${index + 1}`),
        status: newsStatuses.has(entry.status) ? entry.status : 'draft',
        category: text(entry.category, 60, 'Update'),
        categoryPt: text(entry.categoryPt, 60, 'Atualização'),
        title,
        titlePt: text(entry.titlePt, 160, title),
        summary: text(entry.summary, 500),
        summaryPt: text(entry.summaryPt, 500, text(entry.summary, 500)),
        body: text(entry.body, 12_000),
        bodyPt: text(entry.bodyPt, 12_000, text(entry.body, 12_000)),
        publishedAt,
        updatedAt: isoDate(entry.updatedAt, publishedAt),
      }];
    })
    .sort((a, b) => Date.parse(b.publishedAt) - Date.parse(a.publishedAt));

  const faqs = (Array.isArray(source.faqs) ? source.faqs : defaultContent.faqs)
    .slice(0, 30)
    .flatMap((entry, index) => {
      if (!entry || typeof entry !== 'object') return [];
      const question = text(entry.question, 180);
      const answer = text(entry.answer, 1_200);
      if (!question || !answer) return [];
      return [{
        id: slug(entry.id || question, `faq-${index + 1}`),
        question,
        questionPt: text(entry.questionPt, 180, question),
        answer,
        answerPt: text(entry.answerPt, 1_200, answer),
      }];
    });

  return {
    announcement: {
      enabled: announcementSource.enabled !== false,
      title: text(announcementSource.title, 120, defaultContent.announcement.title),
      titlePt: text(announcementSource.titlePt, 120, defaultContent.announcement.titlePt),
      message: text(announcementSource.message, 400, defaultContent.announcement.message),
      messagePt: text(announcementSource.messagePt, 400, defaultContent.announcement.messagePt),
      ctaLabel: text(announcementSource.ctaLabel, 60, defaultContent.announcement.ctaLabel),
      ctaLabelPt: text(announcementSource.ctaLabelPt, 60, defaultContent.announcement.ctaLabelPt),
      ctaHref: safeHref(announcementSource.ctaHref, '/community'),
    },
    season: {
      id: slug(seasonSource.id || seasonSource.name, defaultContent.season.id),
      name: text(seasonSource.name, 100, defaultContent.season.name),
      namePt: text(seasonSource.namePt, 100, defaultContent.season.namePt),
      status: ['live', 'upcoming', 'ended'].includes(seasonSource.status) ? seasonSource.status : 'live',
      label: text(seasonSource.label, 120, defaultContent.season.label),
      labelPt: text(seasonSource.labelPt, 120, defaultContent.season.labelPt),
      description: text(seasonSource.description, 800, defaultContent.season.description),
      descriptionPt: text(seasonSource.descriptionPt, 800, defaultContent.season.descriptionPt),
    },
    events,
    news,
    faqs,
    discord: {
      inviteUrl: normaliseDiscordInvite(discordSource.inviteUrl),
      guildId: /^\d{16,22}$/.test(text(discordSource.guildId, 22)) ? text(discordSource.guildId, 22) : '',
    },
  };
}

const configuredContent = normaliseContent(defaultContent);

export function normaliseSubmission(payload, now = new Date().toISOString(), id = randomUUID()) {
  if (!payload || typeof payload !== 'object') throw new TypeError('Invalid submission.');
  if (text(payload.website, 200)) throw new TypeError('Invalid submission.');

  const type = submissionTypes.has(payload.type) ? payload.type : 'general';
  const playerName = text(payload.playerName, 32);
  const email = text(payload.email, 180).toLowerCase();
  const message = text(payload.message, 5_000);
  if (!/^[a-z0-9_]{2,32}$/i.test(playerName)) throw new TypeError('Enter a valid Minecraft username.');
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) throw new TypeError('Enter a valid email address.');
  if (message.length < 20) throw new TypeError('The message must contain at least 20 characters.');
  if (payload.consent !== true) throw new TypeError('Consent is required.');

  const evidenceUrl = text(payload.evidenceUrl, 500);
  if (evidenceUrl && !/^https:\/\/[^\s]+$/i.test(evidenceUrl)) {
    throw new TypeError('Evidence must use a valid HTTPS link.');
  }

  return {
    id,
    type,
    status: 'new',
    playerName,
    email,
    orderReference: text(payload.orderReference, 120),
    subject: text(payload.subject, 180, type.replace('-', ' ')),
    message,
    evidenceUrl,
    createdAt: now,
    updatedAt: now,
  };
}

export function normaliseStatus(payload, checkedAt = new Date().toISOString()) {
  if (!payload || typeof payload !== 'object' || typeof payload.online !== 'boolean') return null;

  const players = Array.isArray(payload.players?.list)
    ? payload.players.list
        .filter((player) => player && typeof player.name === 'string' && typeof player.uuid === 'string')
        .map((player) => ({ name: player.name, uuid: player.uuid }))
    : [];

  return {
    online: payload.online,
    onlinePlayers: Number(payload.players?.online) || 0,
    maxPlayers: Number(payload.players?.max) || 0,
    players,
    motd: Array.isArray(payload.motd?.clean) ? payload.motd.clean.join(' · ') : '',
    checkedAt,
  };
}

function publicStatus(status) {
  if (!status || typeof status !== 'object') return null;
  const value = { ...status };
  delete value.version;
  delete value.software;
  return value;
}

export function emptyState(now = new Date().toISOString()) {
  return {
    version: 2,
    trackingStartedAt: now,
    lastPollAt: null,
    lastSuccessfulPollAt: null,
    checks: { total: 0, online: 0 },
    status: null,
    players: {},
    dailyStatus: {},
    incidents: [],
    submissions: [],
    analytics: { daily: {} },
    lastBackupDay: null,
  };
}

function migrateState(persisted) {
  const base = emptyState(persisted?.trackingStartedAt || new Date().toISOString());
  const migrated = {
    ...base,
    ...(persisted && typeof persisted === 'object' && persisted),
    version: 2,
    status: publicStatus(persisted?.status),
    checks: { ...base.checks, ...persisted?.checks },
    players: persisted?.players && typeof persisted.players === 'object' ? persisted.players : {},
    dailyStatus: persisted?.dailyStatus && typeof persisted.dailyStatus === 'object' ? persisted.dailyStatus : {},
    incidents: Array.isArray(persisted?.incidents) ? persisted.incidents.slice(-50) : [],
    submissions: Array.isArray(persisted?.submissions)
      ? persisted.submissions.filter((entry) => Date.parse(entry.createdAt) > Date.now() - supportRetentionMs).slice(-1_000)
      : [],
    analytics: {
      daily: persisted?.analytics?.daily && typeof persisted.analytics.daily === 'object'
        ? persisted.analytics.daily
        : {},
    },
  };
  delete migrated.content;
  return migrated;
}

export function applyObservation(state, status, now = new Date().toISOString()) {
  const next = structuredClone(state);
  const previouslyOnlinePlayers = new Set(
    Object.values(next.players)
      .filter((player) => player.online)
      .map((player) => player.uuid),
  );
  const previousServerOnline = next.status?.online;
  const previousPoll = next.lastPollAt ? Date.parse(next.lastPollAt) : NaN;
  const elapsedMinutes = Number.isFinite(previousPoll)
    ? Math.min(5, Math.max(0, (Date.parse(now) - previousPoll) / 60_000))
    : 0;

  for (const player of Object.values(next.players)) player.online = false;

  for (const player of status.players) {
    const previous = next.players[player.uuid];
    next.players[player.uuid] = {
      uuid: player.uuid,
      name: player.name,
      firstSeenAt: previous?.firstSeenAt || now,
      lastSeenAt: now,
      observedMinutes:
        Math.round(
          ((previous?.observedMinutes || 0) + (previouslyOnlinePlayers.has(player.uuid) ? elapsedMinutes : 0)) * 10,
        ) / 10,
      checksSeen: (previous?.checksSeen || 0) + 1,
      online: true,
    };
  }

  const day = now.slice(0, 10);
  const dayStatus = next.dailyStatus[day] || { total: 0, online: 0 };
  next.dailyStatus[day] = {
    total: dayStatus.total + 1,
    online: dayStatus.online + (status.online ? 1 : 0),
  };
  for (const oldDay of Object.keys(next.dailyStatus).sort().slice(0, -30)) delete next.dailyStatus[oldDay];

  if (previousServerOnline === true && status.online === false) {
    next.incidents.push({
      id: randomUUID(),
      service: 'minecraft',
      title: 'Minecraft server interruption',
      status: 'investigating',
      startedAt: now,
      endedAt: null,
    });
  }
  if (previousServerOnline === false && status.online === true) {
    const incident = [...next.incidents].reverse().find((entry) => entry.service === 'minecraft' && !entry.endedAt);
    if (incident) {
      incident.status = 'resolved';
      incident.endedAt = now;
    }
  }
  next.incidents = next.incidents.slice(-50);

  next.lastPollAt = now;
  next.lastSuccessfulPollAt = now;
  next.status = status;
  next.checks.total += 1;
  if (status.online) next.checks.online += 1;
  return next;
}

export function leaderboardFromState(state, limit = 25) {
  return Object.values(state.players)
    .sort((a, b) => b.observedMinutes - a.observedMinutes || b.checksSeen - a.checksSeen)
    .slice(0, limit)
    .map((player, index) => ({ ...player, rank: index + 1 }));
}

export function statusPageFromState(state, now = new Date().toISOString()) {
  const days = Object.entries(state.dailyStatus)
    .sort(([a], [b]) => a.localeCompare(b))
    .slice(-14)
    .map(([date, value]) => ({
      date,
      uptimePercent: value.total ? Math.round((value.online / value.total) * 10_000) / 100 : null,
      checks: value.total,
    }));
  return {
    minecraft: publicStatus(state.status),
    api: { online: true, checkedAt: now },
    backup: {
      lastSuccessfulDay: state.lastBackupDay,
      retentionDays: backupRetentionDays,
      healthy: Boolean(state.lastBackupDay) && Date.parse(now) - Date.parse(`${state.lastBackupDay}T00:00:00.000Z`) < 48 * 60 * 60_000,
    },
    days,
    incidents: state.incidents.slice(-20).reverse(),
    lastUpdatedAt: state.lastSuccessfulPollAt,
  };
}

function profileFromState(state, uuid) {
  const player = state.players[uuid];
  if (!player) return null;
  const rankedPlayer = leaderboardFromState(state, Object.keys(state.players).length).find(
    (entry) => entry.uuid === uuid,
  );
  return {
    player: rankedPlayer || player,
    season: configuredContent.season,
    trackingStartedAt: state.trackingStartedAt,
    note: 'Activity is observed from public server pings. On larger servers the visible player list may be a sample.',
  };
}

function communityFromState(state) {
  const uptime = state.checks.total ? Math.round((state.checks.online / state.checks.total) * 10_000) / 100 : null;
  return {
    status: publicStatus(state.status),
    season: configuredContent.season,
    events: configuredContent.events,
    leaderboard: leaderboardFromState(state),
    summary: {
      trackedPlayers: Object.keys(state.players).length,
      uptimePercent: uptime,
      trackingStartedAt: state.trackingStartedAt,
      lastUpdatedAt: state.lastSuccessfulPollAt,
    },
    note: 'The activity ranking counts time observed online since monitoring began; it is not a combat ranking.',
  };
}

function analyticsSummary(state) {
  const days = Object.entries(state.analytics.daily)
    .sort(([a], [b]) => a.localeCompare(b))
    .slice(-30)
    .map(([date, events]) => ({ date, events }));
  const totals = {};
  for (const day of days) {
    for (const [event, count] of Object.entries(day.events)) totals[event] = (totals[event] || 0) + count;
  }
  return { days, totals };
}

async function loadState() {
  try {
    return migrateState(JSON.parse(await readFile(stateFile, 'utf8')));
  } catch (error) {
    if (error?.code === 'ENOENT') return emptyState();
    throw error;
  }
}

async function saveState(state) {
  await mkdir(dirname(stateFile), { recursive: true });
  const temporary = `${stateFile}.tmp`;
  await writeFile(temporary, JSON.stringify(state, null, 2));
  await rename(temporary, stateFile);
}

async function backUpState(state) {
  const day = new Date().toISOString().slice(0, 10);
  if (state.lastBackupDay === day) return state;

  const backupDirectory = join(dataDirectory, 'backups');
  await mkdir(backupDirectory, { recursive: true });
  await writeFile(join(backupDirectory, `community-${day}.json`), JSON.stringify(state, null, 2));

  const backups = (await readdir(backupDirectory))
    .filter((file) => /^community-\d{4}-\d{2}-\d{2}\.json$/.test(file))
    .sort();
  for (const oldBackup of backups.slice(0, -backupRetentionDays)) await unlink(join(backupDirectory, oldBackup));

  return { ...state, lastBackupDay: day };
}

async function fetchStatus() {
  const response = await fetch(`https://api.mcsrvstat.us/3/${encodeURIComponent(address)}`, {
    headers: { Accept: 'application/json', 'User-Agent': 'MerelyMeSMP-Monitor/2.0' },
    signal: AbortSignal.timeout(10_000),
  });
  if (!response.ok) throw new Error(`Status provider returned ${response.status}`);

  const status = normaliseStatus(await response.json());
  if (!status) throw new Error('Status provider returned an invalid response');
  return status;
}

async function readJson(request, limit = 64_000) {
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > limit) throw new RangeError('Request body is too large.');
    chunks.push(chunk);
  }
  if (!chunks.length) return {};
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}

function isAdmin(request) {
  if (adminToken.length < 24) return false;
  return request.headers.authorization === `Bearer ${adminToken}`;
}

function setCors(request, response) {
  const origin = request.headers.origin;
  if (origin && allowedOrigins.has(origin)) {
    response.setHeader('Access-Control-Allow-Origin', origin);
    response.setHeader('Vary', 'Origin');
  }
  response.setHeader('Access-Control-Allow-Methods', 'GET, POST, PATCH, DELETE, OPTIONS');
  response.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  response.setHeader('X-Content-Type-Options', 'nosniff');
  response.setHeader('Referrer-Policy', 'no-referrer');
}

function sendJson(response, statusCode, body, cache = 'no-store') {
  response.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': cache,
  });
  response.end(JSON.stringify(body));
}

export async function startServer() {
  let state = await loadState();
  let polling = false;
  let saveQueue = Promise.resolve();
  const submissionTimes = [];

  function persist() {
    const snapshot = structuredClone(state);
    saveQueue = saveQueue.then(() => saveState(snapshot));
    return saveQueue;
  }

  async function poll() {
    if (polling) return;
    polling = true;
    try {
      const status = await fetchStatus();
      const wasOnline = state.status?.online;
      state = applyObservation(state, status);
      state = await backUpState(state);
      await persist();
      if (wasOnline !== undefined && wasOnline !== status.online) {
        console.log(`[monitor] Server changed to ${status.online ? 'online' : 'offline'} at ${status.checkedAt}`);
      }
    } catch (error) {
      console.error(`[monitor] Status check failed: ${error.message}`);
    } finally {
      polling = false;
    }
  }

  async function handle(request, response) {
    setCors(request, response);
    if (request.method === 'OPTIONS') return response.writeHead(204).end();

    const url = new URL(request.url || '/', 'http://localhost');
    if (request.method === 'GET' && url.pathname === '/health') {
      return sendJson(response, 200, {
        ok: true,
        service: 'merelymesmp-community-api',
        version: 2,
        lastSuccessfulPollAt: state.lastSuccessfulPollAt,
      });
    }
    if (request.method === 'GET' && url.pathname === '/api/status') {
      return state.status
        ? sendJson(response, 200, publicStatus(state.status), 'public, max-age=15, stale-while-revalidate=60')
        : sendJson(response, 503, { error: 'Server status is not available yet.' });
    }
    if (request.method === 'GET' && url.pathname === '/api/status-page') {
      return sendJson(response, 200, statusPageFromState(state), 'public, max-age=30, stale-while-revalidate=120');
    }
    if (request.method === 'GET' && url.pathname === '/api/content') {
      return sendJson(response, 200, configuredContent, 'public, max-age=30, stale-while-revalidate=120');
    }
    if (request.method === 'GET' && url.pathname === '/api/discord') {
      const configured = configuredContent.discord;
      if (!configured.guildId) {
        return sendJson(response, 200, { ...configured, name: null, online: null }, 'public, max-age=60');
      }
      try {
        const discordResponse = await fetch(`https://discord.com/api/guilds/${configured.guildId}/widget.json`, {
          headers: { Accept: 'application/json' },
          signal: AbortSignal.timeout(6_000),
        });
        if (!discordResponse.ok) throw new Error(`Discord returned ${discordResponse.status}`);
        const widget = await discordResponse.json();
        return sendJson(response, 200, {
          ...configured,
          inviteUrl: configured.inviteUrl || text(widget.instant_invite, 300),
          name: text(widget.name, 120) || null,
          online: Number(widget.presence_count) || 0,
        }, 'public, max-age=60, stale-while-revalidate=180');
      } catch {
        return sendJson(response, 200, { ...configured, name: null, online: null }, 'public, max-age=30');
      }
    }
    if (request.method === 'GET' && url.pathname === '/api/community') {
      return sendJson(response, 200, communityFromState(state), 'public, max-age=30, stale-while-revalidate=120');
    }
    if (request.method === 'GET' && url.pathname.startsWith('/api/players/')) {
      const uuid = decodeURIComponent(url.pathname.slice('/api/players/'.length));
      const profile = profileFromState(state, uuid);
      return profile
        ? sendJson(response, 200, profile, 'public, max-age=30, stale-while-revalidate=120')
        : sendJson(response, 404, { error: 'Player has not been observed yet.' });
    }
    if (request.method === 'POST' && url.pathname === '/api/submissions') {
      const now = Date.now();
      while (submissionTimes[0] < now - 60 * 60_000) submissionTimes.shift();
      if (submissionTimes.length >= 50) return sendJson(response, 429, { error: 'Please try again later.' });
      try {
        const submission = normaliseSubmission(await readJson(request));
        submissionTimes.push(now);
        state.submissions.push(submission);
        state.submissions = state.submissions
          .filter((entry) => Date.parse(entry.createdAt) > now - supportRetentionMs)
          .slice(-1_000);
        await persist();
        void sendSupportNotification(submission).catch((error) => {
          console.error(`[support] Notification failed: ${error.message}`);
        });
        return sendJson(response, 201, { ok: true, reference: submission.id });
      } catch (error) {
        const status = error instanceof RangeError ? 413 : 400;
        return sendJson(response, status, { error: error.message || 'Invalid submission.' });
      }
    }
    if (request.method === 'POST' && url.pathname === '/api/analytics') {
      try {
        const payload = await readJson(request, 4_000);
        if (!analyticsEvents.has(payload.event)) return sendJson(response, 400, { error: 'Invalid event.' });
        const day = new Date().toISOString().slice(0, 10);
        const daily = state.analytics.daily[day] || {};
        daily[payload.event] = (Number(daily[payload.event]) || 0) + 1;
        state.analytics.daily[day] = daily;
        for (const oldDay of Object.keys(state.analytics.daily).sort().slice(0, -90)) {
          delete state.analytics.daily[oldDay];
        }
        await persist();
        return sendJson(response, 202, { ok: true });
      } catch {
        return sendJson(response, 400, { error: 'Invalid event.' });
      }
    }

    if (url.pathname.startsWith('/api/admin/')) {
      if (!isAdmin(request)) return sendJson(response, 401, { error: 'Unauthorized.' });
      if (request.method === 'GET' && url.pathname === '/api/admin/dashboard') {
        return sendJson(response, 200, {
          submissions: state.submissions.slice(-250).reverse(),
          analytics: analyticsSummary(state),
          status: statusPageFromState(state),
        });
      }
      const submissionMatch = url.pathname.match(/^\/api\/admin\/submissions\/([a-f0-9-]+)$/i);
      if (request.method === 'DELETE' && submissionMatch) {
        const index = state.submissions.findIndex((entry) => entry.id === submissionMatch[1]);
        if (index === -1) return sendJson(response, 404, { error: 'Submission not found.' });
        state.submissions.splice(index, 1);
        await persist();
        return sendJson(response, 200, { ok: true });
      }
      if (request.method === 'PATCH' && submissionMatch) {
        const submission = state.submissions.find((entry) => entry.id === submissionMatch[1]);
        if (!submission) return sendJson(response, 404, { error: 'Submission not found.' });
        try {
          const payload = await readJson(request, 4_000);
          if (!submissionStatuses.has(payload.status)) return sendJson(response, 400, { error: 'Invalid status.' });
          submission.status = payload.status;
          submission.updatedAt = new Date().toISOString();
          await persist();
          return sendJson(response, 200, { ok: true, submission });
        } catch {
          return sendJson(response, 400, { error: 'Invalid status.' });
        }
      }
    }

    return sendJson(response, 404, { error: 'Not found.' });
  }

  const server = createServer((request, response) => {
    handle(request, response).catch((error) => {
      console.error(`[api] Request failed: ${error.message}`);
      if (!response.headersSent) sendJson(response, 500, { error: 'Internal server error.' });
      else response.end();
    });
  });

  const port = Number(process.env.PORT) || 3001;
  server.listen(port, '0.0.0.0', () => console.log(`[api] Listening on ${port}; monitoring ${address}`));
  await poll();
  const timer = setInterval(poll, pollInterval);
  timer.unref();
  return server;
}

if (process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url) {
  startServer().catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
}
