const GUILD = "1534136666984419348";
const AH = "1545047708799209492";
const TRADES = "1545047710288060517";
const IPLINKS = "1544904335530528788";
const STORE = null; // find store channel

const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab = list.find((t) => t.url && t.url.includes("discord.com/channels/153413"));
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
const storeCh = channels.find((c) => /store|loja|shop/i.test(c.name) && c.type === 0);
console.log("store", storeCh?.id, storeCh?.name);

// Forum starter posts use threads API
async function ensureForumPost(forumId, name, content) {
  const threads = await api(`/channels/${forumId}/threads/active`);
  const existing = (threads.body?.threads || []).find((t) => t.name === name);
  if (existing) {
    console.log("exists", forumId, existing.id, existing.name);
    return existing;
  }
  const r = await api(`/channels/${forumId}/threads`, {
    method: "POST",
    body: JSON.stringify({
      name,
      auto_archive_duration: 10080,
      message: { content },
    }),
  });
  console.log("create post", forumId, r.status, r.body?.id || r.body?.message || JSON.stringify(r.body).slice(0, 200));
  return r.body;
}

await ensureForumPost(
  AH,
  "HOW TO USE THIS FORUM",
  [
    "**AH SELL — Discord listings for `/ah`**",
    "",
    "When you put an item on `/ah` in-game, also open a post here so people on Discord can see it.",
    "",
    "**Title:** `[PRICE] Item name`",
    "Example: `[50k] Netherite Sword Sharpness V`",
    "",
    "**In the post include:**",
    "• Minecraft nick",
    "• Exact `/ah` price",
    "• Enchantments / extras",
    "• Screenshot if you want",
    "",
    "Use tags (Weapons / Armor / Tools / Blocks / Spawners / Misc).",
    "When sold, ask staff to mark **Sold** or close the post.",
    "",
    "In-game: `/ah`  |  Site: https://merelymesmp.com",
  ].join("\n")
);

await ensureForumPost(
  TRADES,
  "HOW TO TRADE HERE",
  [
    "**Player trades (not only AH)**",
    "",
    "Tags: **WTS** (sell) · **WTB** (buy) · **WTT** (trade) · **Done**",
    "",
    "Be clear with nick + offer. No scams. Prefer midman/staff if high value.",
  ].join("\n")
);

if (IPLINKS) {
  const msg = await api(`/channels/${IPLINKS}/messages`, {
    method: "POST",
    body: JSON.stringify({
      content: [
        "**NEW: AH & TRADES FORUMS**",
        `• <#${AH}> — post items you listed on \`/ah\``,
        `• <#${TRADES}> — WTS / WTB / WTT with other players`,
        "IP: `merelymesmp.g-portal.game` · Store: https://merelymesmp.com",
      ].join("\n"),
    }),
  });
  console.log("iplinks", msg.status, msg.body?.id || msg.body?.message);
}

if (storeCh) {
  const msg = await api(`/channels/${storeCh.id}/messages`, {
    method: "POST",
    body: JSON.stringify({
      content: [
        "**Store ranks ↔ Discord roles**",
        "Buy Knight / Warrior / Macer / Prime on the store and **link your Discord at checkout** so Tip4Serv can give you the matching Discord role.",
        "Store: https://merelymesmp.com",
      ].join("\n"),
    }),
  });
  console.log("store msg", msg.status);
}

// Check Tip4Serv bot member
const tip = await api(`/guilds/${GUILD}/members/1023897726301249628`);
console.log("tip4serv bot member", tip.status, tip.body?.user?.username || tip.body?.message);

ws.close();
