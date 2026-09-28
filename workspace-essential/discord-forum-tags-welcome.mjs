const GUILD = "1534136666984419348";
const AH = "1545047708799209492";
const TRADES = "1545047710288060517";
const MARKET = "1545047707704500314";

const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab = list.find((t) => t.url && t.url.includes("discord.com/channels/153413"));
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
        reject(Error("to"));
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

const tok = (
  await send("Runtime.evaluate", {
    expression: `(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,
    returnByValue: true,
  })
).result.value;

const api = async (path, opts = {}) => {
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
  return { status: res.status, body };
};

const channels = (await api(`/guilds/${GUILD}/channels`)).body;
const roles = (await api(`/guilds/${GUILD}/roles`)).body;
const everyone = roles.find((r) => r.name === "@everyone")?.id || GUILD;

// Helper: set forum tags (replace available_tags)
async function setForumTags(channelId, tags) {
  const r = await api(`/channels/${channelId}`, {
    method: "PATCH",
    body: JSON.stringify({ available_tags: tags }),
  });
  console.log(
    "tags",
    channelId,
    r.status,
    (r.body?.available_tags || []).map((t) => t.name),
    r.body?.message || ""
  );
  return r.body;
}

// Helper: open perms for @everyone (view + post + send in threads)
async function openForum(channelId) {
  // overwrite everyone overwrite
  const r = await api(`/channels/${channelId}/permissions/${everyone}`, {
    method: "PUT",
    body: JSON.stringify({
      id: everyone,
      type: 0,
      allow: String(
        // VIEW_CHANNEL | SEND_MESSAGES | SEND_MESSAGES_IN_THREADS | CREATE_PUBLIC_THREADS | EMBED_LINKS | ATTACH_FILES | READ_MESSAGE_HISTORY | USE_EXTERNAL_EMOJIS | ADD_REACTIONS
        1024n | 2048n | 274877906944n | 34359738368n | 16384n | 32768n | 65536n | 262144n | 64n
      ),
      deny: "0",
    }),
  });
  console.log("perms", channelId, r.status, r.body?.message || "ok");
}

// 1) AH-SELL: Selling / Buying / Sold
await setForumTags(AH, [
  { name: "Selling", moderated: false },
  { name: "Buying", moderated: false },
  { name: "Sold", moderated: false },
]);
await openForum(AH);

// 2) PLAYER-TRADES: Selling / Buying / Trade / Done
await setForumTags(TRADES, [
  { name: "Selling", moderated: false },
  { name: "Buying", moderated: false },
  { name: "Trade", moderated: false },
  { name: "Done", moderated: false },
]);
await openForum(TRADES);

// 3) Create welcome / intro forum if missing
let intro = channels.find((c) => /welcome|intro|new-member|cheguei|onboard/i.test(c.name));
if (!intro) {
  const c = await api(`/guilds/${GUILD}/channels`, {
    method: "POST",
    body: JSON.stringify({
      name: "👋┃welcome-intros",
      type: 15,
      parent_id: MARKET, // put under market? better COMMUNITY - find COMMUNITY cat
      topic: "New here? Introduce yourself. Anyone can post.",
      available_tags: [
        { name: "EU", moderated: false },
        { name: "NA", moderated: false },
        { name: "SA", moderated: false },
        { name: "ASIA", moderated: false },
        { name: "Other", moderated: false },
        { name: "Notify-PvP", moderated: false },
        { name: "Notify-Events", moderated: false },
        { name: "Notify-Sales", moderated: false },
      ],
      default_reaction_emoji: { emoji_name: "👋" },
      rate_limit_per_user: 5,
    }),
  });
  console.log("create intro", c.status, c.body?.id || c.body?.message, c.body?.name);
  intro = c.body;
}

// Prefer COMMUNITY category parent
const community = channels.find((c) => c.type === 4 && /COMMUNITY/i.test(c.name));
if (intro?.id && community?.id && intro.parent_id !== community.id) {
  const move = await api(`/channels/${intro.id}`, {
    method: "PATCH",
    body: JSON.stringify({ parent_id: community.id }),
  });
  console.log("move intro", move.status, move.body?.parent_id || move.body?.message);
}

if (intro?.id) {
  await openForum(intro.id);
  // Ensure tags if forum already existed with other tags
  await setForumTags(intro.id, [
    { name: "EU", moderated: false },
    { name: "NA", moderated: false },
    { name: "SA", moderated: false },
    { name: "ASIA", moderated: false },
    { name: "Other", moderated: false },
    { name: "Notify-PvP", moderated: false },
    { name: "Notify-Events", moderated: false },
    { name: "Notify-Sales", moderated: false },
  ]);

  // Pin / create guidelines post
  const threads = await api(`/channels/${intro.id}/threads/active`);
  const existing = (threads.body?.threads || []).find((t) =>
    /HOW TO|READ ME|INTRO/i.test(t.name)
  );
  if (!existing) {
    const post = await api(`/channels/${intro.id}/threads`, {
      method: "POST",
      body: JSON.stringify({
        name: "READ ME — Introduce yourself",
        auto_archive_duration: 10080,
        message: {
          content: [
            "**Welcome to MerelyMeSMP!** Anyone can post here.",
            "",
            "Make a new post with your Minecraft nick as the title, then answer:",
            "",
            "1. **How did you find this server?** (Twitch, friend, Discord, Google, etc.)",
            "2. **What do you want to be notified about?** (PvP, events, sales/AH, updates — pick tags)",
            "3. **What region are you?** (EU / NA / SA / ASIA / Other — pick a tag)",
            "4. **Anything else?** goals, playstyle, languages…",
            "",
            "IP: `merelymesmp.g-portal.game` · Site: https://merelymesmp.com",
          ].join("\n"),
        },
      }),
    });
    console.log("intro post", post.status, post.body?.id || post.body?.message);
  } else {
    console.log("intro guide exists", existing.id);
  }
}

// Update AH guidelines post title content via new post if needed
const ahThreads = await api(`/channels/${AH}/threads/active`);
const how = (ahThreads.body?.threads || []).find((t) => /HOW TO/i.test(t.name));
if (how?.id) {
  // Post a new message in the thread updating rules
  const msg = await api(`/channels/${how.id}/messages`, {
    method: "POST",
    body: JSON.stringify({
      content: [
        "**Updated tags:** use **Selling** or **Buying** (and **Sold** when done).",
        "Title example: `[50k] Netherite Sword` + your nick in the post.",
      ].join("\n"),
    }),
  });
  console.log("ah update msg", msg.status);
}

const tradeThreads = await api(`/channels/${TRADES}/threads/active`);
const howT = (tradeThreads.body?.threads || []).find((t) => /HOW TO|TRADE/i.test(t.name));
if (howT?.id) {
  const msg = await api(`/channels/${howT.id}/messages`, {
    method: "POST",
    body: JSON.stringify({
      content: "**Tags:** **Selling** · **Buying** · **Trade** · **Done**",
    }),
  });
  console.log("trades update msg", msg.status);
}

// Announce in ip-links
await api(`/channels/1544904335530528788/messages`, {
  method: "POST",
  body: JSON.stringify({
    content: [
      "**Forums updated**",
      `• <#${AH}> / <#${TRADES}> — tags: **Selling** & **Buying**`,
      intro?.id
        ? `• <#${intro.id}> — new here? say how you found us, region, and what you want notifications for (open to everyone)`
        : "",
    ]
      .filter(Boolean)
      .join("\n"),
  }),
});

console.log("done", { intro: intro?.id, introName: intro?.name });
ws.close();
