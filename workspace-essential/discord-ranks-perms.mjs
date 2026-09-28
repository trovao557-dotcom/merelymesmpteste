/**
 * MerelyMe SMP Discord: ranks, yellow owner, bots role, member for all, channel perms/slowmode
 */
const GUILD = "1534136666984419348";

const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab =
  list.find((t) => t.url && t.url.includes("discord.com/channels/153413")) ||
  list.find((t) => t.url && t.url.includes("discord.com"));
if (!tab) throw new Error("no discord tab");
const ws = new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r, j) => {
  ws.onopen = r;
  ws.onerror = j;
});
let id = 1;
const pending = new Map();
ws.onmessage = (ev) => {
  const m = JSON.parse(ev.data);
  if (m.id == null) return;
  const p = pending.get(m.id);
  if (!p) return;
  pending.delete(m.id);
  m.error ? p.reject(Error(m.error.message)) : p.resolve(m.result);
};
const send = (method, params = {}) =>
  new Promise((resolve, reject) => {
    const i = id++;
    const t = setTimeout(() => {
      if (pending.has(i)) {
        pending.delete(i);
        reject(Error("timeout " + method));
      }
    }, 45000);
    pending.set(i, {
      resolve: (v) => {
        clearTimeout(t);
        resolve(v);
      },
      reject: (e) => {
        clearTimeout(t);
        reject(e);
      },
    });
    ws.send(JSON.stringify({ id: i, method, params }));
  });
await send("Page.bringToFront").catch(() => {});
const tok = (
  await send("Runtime.evaluate", {
    expression: `(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,
    returnByValue: true,
  })
).result.value;
if (!tok) throw new Error("no discord token");

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function api(path, opts = {}) {
  const res = await fetch("https://discord.com/api/v9" + path, {
    ...opts,
    headers: {
      Authorization: tok,
      "Content-Type": "application/json",
      ...(opts.headers || {}),
    },
  });
  const text = await res.text();
  let body;
  try {
    body = JSON.parse(text);
  } catch {
    body = text;
  }
  if (!res.ok) {
    console.log("ERR", opts.method || "GET", path, res.status, JSON.stringify(body).slice(0, 250));
  }
  return { status: res.status, body };
}

const P = {
  VIEW_CHANNEL: 1n << 10n,
  SEND_MESSAGES: 1n << 11n,
  SEND_TTS: 1n << 12n,
  MANAGE_MESSAGES: 1n << 13n,
  EMBED_LINKS: 1n << 14n,
  ATTACH_FILES: 1n << 15n,
  READ_MESSAGE_HISTORY: 1n << 16n,
  MENTION_EVERYONE: 1n << 17n,
  USE_EXTERNAL_EMOJIS: 1n << 18n,
  ADD_REACTIONS: 1n << 6n,
  CONNECT: 1n << 20n,
  SPEAK: 1n << 21n,
  USE_VAD: 1n << 25n,
  USE_APPLICATION_COMMANDS: 1n << 31n,
  CREATE_PUBLIC_THREADS: 1n << 35n,
  CREATE_PRIVATE_THREADS: 1n << 36n,
  SEND_MESSAGES_IN_THREADS: 1n << 38n,
};

function bit(...keys) {
  return keys.reduce((a, k) => a | P[k], 0n).toString();
}

// --- roles ---
let roles = (await api(`/guilds/${GUILD}/roles`)).body;
const byName = (re) => roles.find((r) => re.test(r.name));

async function ensureRole({ name, color, hoist = true, mentionable = false }) {
  const existing = roles.find((r) => r.name === name || r.name.replace(/\s/g, "") === name.replace(/\s/g, ""));
  if (existing) {
    const patch = await api(`/guilds/${GUILD}/roles/${existing.id}`, {
      method: "PATCH",
      body: JSON.stringify({ name, color, hoist, mentionable }),
    });
    console.log("update role", name, patch.status);
    await sleep(350);
    roles = (await api(`/guilds/${GUILD}/roles`)).body;
    return patch.body?.id || existing.id;
  }
  const created = await api(`/guilds/${GUILD}/roles`, {
    method: "POST",
    body: JSON.stringify({ name, color, hoist, mentionable, permissions: "0" }),
  });
  console.log("create role", name, created.status, created.body?.id);
  await sleep(400);
  roles = (await api(`/guilds/${GUILD}/roles`)).body;
  return created.body?.id;
}

const knightId = await ensureRole({ name: "⚔️┃Knight", color: 0x57f287 }); // green
const warriorId = await ensureRole({ name: "🗡️┃Warrior", color: 0xed4245 }); // red
const macerId = await ensureRole({ name: "🔨┃Macer", color: 0x5865f2 }); // blurple
const clipperId = await ensureRole({ name: "🎬┃Clipper", color: 0x1abc9c }); // teal
const botsId = await ensureRole({ name: "🤖┃Bots", color: 0x99aab5, hoist: true, mentionable: false });

// Owner yellow
const owner = byName(/Owner/);
if (owner) {
  const o = await api(`/guilds/${GUILD}/roles/${owner.id}`, {
    method: "PATCH",
    body: JSON.stringify({ color: 0xfee75c, hoist: true, mentionable: true }),
  });
  console.log("owner yellow", o.status);
  await sleep(300);
}

roles = (await api(`/guilds/${GUILD}/roles`)).body;
const memberRole = byName(/Member/);
const admin = byName(/Admin/);
const mod = byName(/Mod/);
const helper = byName(/Helper/);
const ticketStaff = byName(/Ticket Staff/);
const media = byName(/Media/);
const prime = byName(/Prime/);
const carl = roles.find((r) => /^carl-bot$/i.test(r.name));
const tt = roles.find((r) => /Ticket Tool/i.test(r.name));
const stat = roles.find((r) => /Statbot/i.test(r.name));
const jockie = roles.find((r) => /Jockie/i.test(r.name));

// Hierarchy high → low
const order = [
  owner,
  admin,
  mod,
  roles.find((r) => r.id === botsId),
  carl,
  tt,
  stat,
  jockie,
  ticketStaff,
  helper,
  media,
  roles.find((r) => r.id === clipperId),
  prime,
  roles.find((r) => r.id === macerId),
  roles.find((r) => r.id === warriorId),
  roles.find((r) => r.id === knightId),
  memberRole,
].filter(Boolean);

let pos = order.length;
const payload = order.map((r) => ({ id: r.id, position: pos-- }));
const hier = await api(`/guilds/${GUILD}/roles`, {
  method: "PATCH",
  body: JSON.stringify(payload),
});
console.log(
  "hierarchy",
  hier.status,
  payload.map((p) => {
    const n = roles.find((r) => r.id === p.id)?.name;
    return `${p.position} ${n}`;
  })
);

// --- members: give Member to all humans; Bots role to bots ---
async function fetchMembers() {
  const all = [];
  let after = null;
  for (let i = 0; i < 20; i++) {
    const q = after
      ? `/guilds/${GUILD}/members?limit=1000&after=${after}`
      : `/guilds/${GUILD}/members?limit=1000`;
    const res = await api(q);
    if (!Array.isArray(res.body)) {
      console.log("members fetch fail", res.status, JSON.stringify(res.body).slice(0, 200));
      break;
    }
    all.push(...res.body);
    if (res.body.length < 1000) break;
    after = res.body[res.body.length - 1].user.id;
  }
  return all;
}

const members = await fetchMembers();
console.log("members count", members.length);

let memberGiven = 0;
let botsGiven = 0;
for (const m of members) {
  const uid = m.user.id;
  const isBot = !!m.user.bot;
  if (isBot) {
    if (botsId && !(m.roles || []).includes(botsId)) {
      const r = await api(`/guilds/${GUILD}/members/${uid}/roles/${botsId}`, { method: "PUT" });
      if (r.status === 204 || r.status === 200) botsGiven++;
      await sleep(300);
    }
  } else if (memberRole && !(m.roles || []).includes(memberRole.id)) {
    const r = await api(`/guilds/${GUILD}/members/${uid}/roles/${memberRole.id}`, { method: "PUT" });
    if (r.status === 204 || r.status === 200) memberGiven++;
    await sleep(300);
  }
}
console.log("Member given to", memberGiven, "| Bots role given to", botsGiven);

// --- channels ---
const channels = (await api(`/guilds/${GUILD}/channels`)).body;
const findCh = (re) => channels.find((c) => re.test(c.name));
const everyone = GUILD;

const readOnlyDeny = bit(
  "SEND_MESSAGES",
  "SEND_TTS",
  "CREATE_PUBLIC_THREADS",
  "CREATE_PRIVATE_THREADS",
  "SEND_MESSAGES_IN_THREADS",
  "ADD_REACTIONS"
);
// keep reactions on rules? user said can't send msgs - allow reactions maybe for rules ack
const readOnlyDenyNoReact = bit(
  "SEND_MESSAGES",
  "SEND_TTS",
  "CREATE_PUBLIC_THREADS",
  "CREATE_PRIVATE_THREADS",
  "SEND_MESSAGES_IN_THREADS"
);

async function overwrite(channelId, targetId, type, allow, deny) {
  return api(`/channels/${channelId}/permissions/${targetId}`, {
    method: "PUT",
    body: JSON.stringify({ id: targetId, type, allow: String(allow), deny: String(deny) }),
  });
}

async function setSlowmode(channelId, seconds) {
  return api(`/channels/${channelId}`, {
    method: "PATCH",
    body: JSON.stringify({ rate_limit_per_user: seconds }),
  });
}

// INFORMATION: lock messaging for everyone
for (const re of [/announcements/i, /rules/i, /ip-links/i, /updates/i]) {
  const ch = findCh(re);
  if (!ch) continue;
  const deny = /rules/i.test(ch.name) ? readOnlyDenyNoReact : readOnlyDenyNoReact;
  const r = await overwrite(ch.id, everyone, 0, "0", deny);
  console.log("lock", ch.name, r.status);
  await sleep(250);
}

// store / vote / leaderboard / playtime — read mostly, light send deny for info-style
for (const re of [/store/i, /^.*vote$/i, /leaderboard/i]) {
  const ch = findCh(re);
  if (!ch) continue;
  const r = await overwrite(ch.id, everyone, 0, "0", readOnlyDenyNoReact);
  console.log("lock", ch.name, r.status);
  await sleep(250);
}

// create-ticket: everyone can view, no free chat spam (buttons only) — deny send
{
  const ch = findCh(/create-ticket/i);
  if (ch) {
    const r = await overwrite(ch.id, everyone, 0, "0", readOnlyDenyNoReact);
    console.log("lock create-ticket", r.status);
    // bots can send
    if (botsId) {
      await overwrite(
        ch.id,
        botsId,
        0,
        bit("VIEW_CHANNEL", "SEND_MESSAGES", "EMBED_LINKS", "ATTACH_FILES", "READ_MESSAGE_HISTORY", "USE_EXTERNAL_EMOJIS", "ADD_REACTIONS", "USE_APPLICATION_COMMANDS"),
        "0"
      );
    }
  }
}

// chat-geral: 5s slowmode
{
  const ch = findCh(/chat-geral/i);
  if (ch) {
    const r = await setSlowmode(ch.id, 5);
    console.log("slowmode chat-geral 5s", r.status);
  }
}

// media: 10s
{
  const ch = findCh(/📸┃media|media/i);
  // prefer community media not category
  const mediaCh = channels.find((c) => c.type === 0 && /media/i.test(c.name) && !/bot/i.test(c.name));
  if (mediaCh) {
    console.log("slowmode media 10s", (await setSlowmode(mediaCh.id, 10)).status);
  }
}

// sugestoes: 15s
{
  const ch = findCh(/sugest/i);
  if (ch) console.log("slowmode sugestoes 15s", (await setSlowmode(ch.id, 15)).status);
}

// looking-for-team: 10s
{
  const ch = findCh(/looking-for-team/i);
  if (ch) console.log("slowmode lft 10s", (await setSlowmode(ch.id, 10)).status);
}

// bot-commands: 3s + bots allowed
{
  const ch = findCh(/bot-commands/i);
  if (ch) {
    console.log("slowmode bot-commands 3s", (await setSlowmode(ch.id, 3)).status);
    if (botsId) {
      await overwrite(
        ch.id,
        botsId,
        0,
        bit("VIEW_CHANNEL", "SEND_MESSAGES", "EMBED_LINKS", "ATTACH_FILES", "READ_MESSAGE_HISTORY", "USE_EXTERNAL_EMOJIS", "ADD_REACTIONS", "USE_APPLICATION_COMMANDS", "MENTION_EVERYONE"),
        "0"
      );
    }
  }
}

// playtime: bots can post, members read (or allow chat with slowmode)
{
  const ch = findCh(/playtime/i);
  if (ch) {
    await overwrite(ch.id, everyone, 0, "0", readOnlyDenyNoReact);
    if (botsId) {
      await overwrite(
        ch.id,
        botsId,
        0,
        bit("VIEW_CHANNEL", "SEND_MESSAGES", "EMBED_LINKS", "ATTACH_FILES", "READ_MESSAGE_HISTORY", "USE_APPLICATION_COMMANDS"),
        "0"
      );
    }
    console.log("playtime bots-post");
  }
}

// logs: bots write
{
  const ch = findCh(/logs/i);
  if (ch && botsId) {
    await overwrite(
      ch.id,
      botsId,
      0,
      bit("VIEW_CHANNEL", "SEND_MESSAGES", "EMBED_LINKS", "ATTACH_FILES", "READ_MESSAGE_HISTORY"),
      "0"
    );
    console.log("logs bots ok");
  }
}

// final roles dump
roles = (await api(`/guilds/${GUILD}/roles`)).body;
console.log(
  "ROLES",
  roles
    .sort((a, b) => b.position - a.position)
    .map((r) => `${r.position} ${r.name} #${r.color.toString(16)}`)
    .join(" | ")
);

const sample = channels
  .filter((c) => c.type === 0)
  .map((c) => `${c.name} slow=${c.rate_limit_per_user || 0}`);
// refresh channels for slowmode
const channels2 = (await api(`/guilds/${GUILD}/channels`)).body;
console.log(
  "SLOWMODE",
  channels2
    .filter((c) => c.type === 0 && (c.rate_limit_per_user || 0) > 0)
    .map((c) => `${c.name}:${c.rate_limit_per_user}s`)
    .join(" | ")
);

ws.close();
console.log("DONE");
