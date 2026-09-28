const GUILD = "1534136666984419348";
const MEMBER = "1544904296439619614"; // may refresh - lookup by name
const BOTS = "1545039149596287027";

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
        reject(Error("timeout"));
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
  return { status: res.status, body };
}

const roles = (await api(`/guilds/${GUILD}/roles`)).body;
const memberRole = roles.find((r) => /Member/.test(r.name));
const botsRole = roles.find((r) => /🤖┃Bots|Bots/.test(r.name));
console.log("member", memberRole?.id, "bots", botsRole?.id);

// Pull members from Discord client cache
const cached = await send("Runtime.evaluate", {
  expression: `(() => {
    try {
      const mods = webpackChunkdiscord_app;
      // fallback: walk guild member list from DOM if open
      return { ok: true };
    } catch(e) { return { err: String(e) }; }
  })()`,
  returnByValue: true,
});

// Search API with many queries
const found = new Map();
const queries = "abcdefghijklmnopqrstuvwxyz0123456789_.".split("");
for (const q of queries) {
  const res = await api(`/guilds/${GUILD}/members/search?query=${encodeURIComponent(q)}&limit=100`);
  if (Array.isArray(res.body)) {
    for (const m of res.body) found.set(m.user.id, m);
  }
  await new Promise((r) => setTimeout(r, 200));
}
// also empty-ish
for (const q of ["merely", "spider", "carl", "ticket", "stat", "jockie", "bot", "dash", "me"]) {
  const res = await api(`/guilds/${GUILD}/members/search?query=${encodeURIComponent(q)}&limit=100`);
  if (Array.isArray(res.body)) {
    for (const m of res.body) found.set(m.user.id, m);
  }
  await new Promise((r) => setTimeout(r, 200));
}

console.log("found members", found.size);
let memberGiven = 0;
let botsGiven = 0;
for (const m of found.values()) {
  const uid = m.user.id;
  const isBot = !!m.user.bot;
  if (isBot && botsRole && !(m.roles || []).includes(botsRole.id)) {
    const r = await api(`/guilds/${GUILD}/members/${uid}/roles/${botsRole.id}`, { method: "PUT" });
    if (r.status === 204 || r.status === 200) {
      botsGiven++;
      console.log("bots ->", m.user.username);
    } else console.log("bots fail", m.user.username, r.status);
    await new Promise((r) => setTimeout(r, 300));
  }
  if (!isBot && memberRole && !(m.roles || []).includes(memberRole.id)) {
    const r = await api(`/guilds/${GUILD}/members/${uid}/roles/${memberRole.id}`, { method: "PUT" });
    if (r.status === 204 || r.status === 200) {
      memberGiven++;
      console.log("member ->", m.user.username);
    } else console.log("member fail", m.user.username, r.status, JSON.stringify(r.body).slice(0, 120));
    await new Promise((r) => setTimeout(r, 300));
  } else if (!isBot) {
    console.log("already member", m.user.username);
  }
}

// Known bots hardcode
for (const [name, bid] of [
  ["Carl-bot", "235148962103951360"],
  ["Ticket Tool", "557628352828014614"],
  ["Statbot", "491769129318088714"],
]) {
  if (!botsRole) break;
  const r = await api(`/guilds/${GUILD}/members/${bid}/roles/${botsRole.id}`, { method: "PUT" });
  console.log("known bot", name, r.status);
  await new Promise((x) => setTimeout(x, 300));
}

console.log("SUMMARY memberGiven", memberGiven, "botsGiven", botsGiven);
ws.close();
