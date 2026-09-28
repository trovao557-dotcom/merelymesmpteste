const GUILD = "1534136666984419348";
// Region picker needs reactions for Carl-bot
const REACTIONS_OK_CHANNELS = new Set(["1545086719399952435"]);

// Permission bits
const ADD_REACTIONS = 1n << 6n; // 64
const EMBED_LINKS = 1n << 14n; // 16384 — blocks Discord GIF picker / Tenor
const USE_EXTERNAL_EMOJIS = 1n << 18n; // 262144
const USE_EXTERNAL_STICKERS = 1n << 37n; // 137438953472
const DENY_ALWAYS = EMBED_LINKS | USE_EXTERNAL_EMOJIS | USE_EXTERNAL_STICKERS;
const DENY_WITH_REACTIONS = DENY_ALWAYS | ADD_REACTIONS;

const TIMEOUT_SECONDS = 60 * 60; // 1 hour timeout

const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab = list.find((t) => t.url && t.url.includes("discord.com/channels/153413"));
if (!tab) throw new Error("Open Discord guild tab in Brave agent");
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
      pending.delete(i);
      reject(Error("to"));
    }, 30000);
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

const tok = (
  await send("Runtime.evaluate", {
    expression: `(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,
    returnByValue: true,
  })
).result.value;

const api = async (p, o = {}) => {
  const r = await fetch("https://discord.com/api/v9" + p, {
    ...o,
    headers: {
      Authorization: tok,
      "Content-Type": "application/json",
      ...(o.headers || {}),
    },
  });
  const t2 = await r.text();
  let b;
  try {
    b = JSON.parse(t2);
  } catch {
    b = t2;
  }
  return { status: r.status, body: b };
};
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const roles = (await api(`/guilds/${GUILD}/roles`)).body;
const staffRoleIds = roles
  .filter((r) => /owner|admin|mod|staff|helper|bot/i.test(r.name) || r.permissions === "8" || (BigInt(r.permissions) & 8n) === 8n)
  .map((r) => r.id);
console.log(
  "staff/bypass roles:",
  roles.filter((r) => staffRoleIds.includes(r.id)).map((r) => r.name)
);

const channels = (await api(`/guilds/${GUILD}/channels`)).body;
// Text, announcement, forum, voice (for reactions in chat), stage, media
const targetTypes = new Set([0, 5, 15, 16]); // GUILD_TEXT, ANNOUNCEMENT, FORUM, MEDIA
const targets = channels.filter((c) => targetTypes.has(c.type));
console.log("channels to lock:", targets.length);

let ok = 0;
let fail = 0;
for (const ch of targets) {
  // Preserve existing allow bits for @everyone if any; only add deny
  const everyone = (ch.permission_overwrites || []).find((o) => o.id === GUILD && o.type === 0);
  const prevDeny = BigInt(everyone?.deny || "0");
  const prevAllow = BigInt(everyone?.allow || "0");
  const denyBits = REACTIONS_OK_CHANNELS.has(ch.id) ? DENY_ALWAYS : DENY_WITH_REACTIONS;
  let newDeny = (prevDeny | denyBits) & ~(REACTIONS_OK_CHANNELS.has(ch.id) ? ADD_REACTIONS : 0n);
  let newAllow = prevAllow & ~denyBits;
  if (REACTIONS_OK_CHANNELS.has(ch.id)) {
    newAllow = newAllow | ADD_REACTIONS;
    newDeny = newDeny & ~ADD_REACTIONS;
  }

  const r = await api(`/channels/${ch.id}/permissions/${GUILD}`, {
    method: "PUT",
    body: JSON.stringify({
      type: 0,
      allow: newAllow.toString(),
      deny: newDeny.toString(),
    }),
  });
  if (r.status === 204 || r.status === 200) ok++;
  else {
    fail++;
    console.log("fail", ch.name, r.status, JSON.stringify(r.body)?.slice(0, 120));
  }
  await sleep(350);
}
console.log("permission updates ok", ok, "fail", fail);

// Give staff roles back the denied perms on categories? Better: role-level allow on staff roles
for (const rid of staffRoleIds) {
  if (rid === GUILD) continue;
  // Patch role permissions to include the bits we denied for everyone
  const role = roles.find((r) => r.id === rid);
  if (!role || role.managed) continue;
  const perms = BigInt(role.permissions) | DENY_WITH_REACTIONS;
  // Don't touch @everyone
  if (role.name === "@everyone") continue;
  // Only boost staff-like roles that already have manage messages or admin
  const p = BigInt(role.permissions);
  const isStaff = (p & 8n) === 8n || (p & (1n << 13n)) === 1n << 13n; // ADMIN or MANAGE_MESSAGES
  if (!isStaff && !/owner|admin|mod|staff|helper/i.test(role.name)) continue;
  const pr = await api(`/guilds/${GUILD}/roles/${rid}`, {
    method: "PATCH",
    body: JSON.stringify({ permissions: perms.toString() }),
  });
  console.log("staff role allow media", role.name, pr.status);
  await sleep(400);
}

// --- AutoMod rules ---
const existing = (await api(`/guilds/${GUILD}/auto-moderation/rules`)).body;
if (Array.isArray(existing)) {
  for (const rule of existing) {
    if (/merely|gif|spam|bad|filter/i.test(rule.name)) {
      await api(`/guilds/${GUILD}/auto-moderation/rules/${rule.id}`, { method: "DELETE" });
      console.log("deleted old rule", rule.name);
      await sleep(400);
    }
  }
}

const common = {
  enabled: true,
  event_type: 1, // MESSAGE_SEND
  exempt_roles: staffRoleIds.filter((id) => id !== GUILD).slice(0, 20),
  exempt_channels: [],
};

// 1) Block GIF hosts + timeout 1h
const gifRule = await api(`/guilds/${GUILD}/auto-moderation/rules`, {
  method: "POST",
  body: JSON.stringify({
    name: "MerelyMe — Block GIFs",
    ...common,
    trigger_type: 1, // KEYWORD
    trigger_metadata: {
      keyword_filter: [
        "*tenor.com*",
        "*giphy.com*",
        "*media.tenor*",
        "*c.tenor*",
        "*.gif*",
        "*discordapp.com/attachments/*.gif*",
      ],
      regex_patterns: [],
      allow_list: [],
    },
    actions: [
      { type: 1 }, // block message
      {
        type: 3, // timeout
        metadata: { duration_seconds: TIMEOUT_SECONDS },
      },
    ],
  }),
});
console.log("gif rule", gifRule.status, gifRule.body?.id || gifRule.body?.message || JSON.stringify(gifRule.body)?.slice(0, 200));

// 2) Mention spam
const mentionRule = await api(`/guilds/${GUILD}/auto-moderation/rules`, {
  method: "POST",
  body: JSON.stringify({
    name: "MerelyMe — Mention spam",
    ...common,
    trigger_type: 4, // MENTION_SPAM
    trigger_metadata: {
      mention_total_limit: 5,
      mention_raid_protection_enabled: true,
    },
    actions: [
      { type: 1 },
      { type: 3, metadata: { duration_seconds: TIMEOUT_SECONDS * 2 } },
    ],
  }),
});
console.log("mention rule", mentionRule.status, mentionRule.body?.id || mentionRule.body?.message || JSON.stringify(mentionRule.body)?.slice(0, 200));

// 3) Bad words / invites (common spam)
const badRule = await api(`/guilds/${GUILD}/auto-moderation/rules`, {
  method: "POST",
  body: JSON.stringify({
    name: "MerelyMe — Bad links & spam words",
    ...common,
    trigger_type: 1,
    trigger_metadata: {
      keyword_filter: [
        "*discord.gg/*",
        "*discord.com/invite*",
        "free nitro*",
        "*steamcommunity.com/gift*",
        "*steamcommun*",
        "*bit.ly/*",
        "*tinyurl.com*",
      ],
      regex_patterns: [],
      allow_list: ["*discord.gg/NEybDXCkhA*", "*merelymesmp.com*"],
    },

    actions: [
      { type: 1 },
      { type: 3, metadata: { duration_seconds: TIMEOUT_SECONDS } },
    ],
  }),
});
console.log("bad rule", badRule.status, badRule.body?.id || badRule.body?.message || JSON.stringify(badRule.body)?.slice(0, 250));

// 4) Keyword presets (profanity etc) if available
const preset = await api(`/guilds/${GUILD}/auto-moderation/rules`, {
  method: "POST",
  body: JSON.stringify({
    name: "MerelyMe — Profanity preset",
    ...common,
    trigger_type: 3, // KEYWORD_PRESET
    trigger_metadata: {
      presets: [1, 2, 3], // PROFANITY, SEXUAL_CONTENT, SLURS
    },
    actions: [
      { type: 1 },
      { type: 3, metadata: { duration_seconds: TIMEOUT_SECONDS } },
    ],
  }),
});
console.log("preset rule", preset.status, preset.body?.id || preset.body?.message || JSON.stringify(preset.body)?.slice(0, 200));

ws.close();
console.log("\nDONE — timeout duration:", TIMEOUT_SECONDS, "seconds (1 hour)");
