const GUILD = "1534136666984419348";
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
    }, 60000);
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

// Extract guild members from webpack stores
const members = await send("Runtime.evaluate", {
  expression: `(() => {
    const guildId = "${GUILD}";
    const out = [];
    const seen = new Set();
    try {
      let wpReq;
      webpackChunkdiscord_app.push([[Symbol()], {}, (r) => { wpReq = r; }]);
      webpackChunkdiscord_app.pop();
      const cache = wpReq.c;
      const stores = [];
      for (const id in cache) {
        const m = cache[id]?.exports;
        if (!m) continue;
        const cand = m.default || m;
        // GuildMemberStore-like
        if (cand?.getMembers && typeof cand.getMembers === 'function') {
          try {
            const list = cand.getMembers(guildId) || [];
            for (const mid of list) {
              const mem = cand.getMember?.(guildId, mid) || { userId: mid };
              const uid = mem.userId || mid;
              if (seen.has(uid)) continue;
              seen.add(uid);
              out.push({ id: uid, roles: mem.roles || [] });
            }
            stores.push('getMembers:' + list.length);
          } catch (e) { stores.push('err:' + e.message); }
        }
        if (cand?.getMemberIds && typeof cand.getMemberIds === 'function') {
          try {
            const ids = cand.getMemberIds(guildId) || [];
            for (const uid of ids) {
              if (seen.has(uid)) continue;
              seen.add(uid);
              const mem = cand.getMember?.(guildId, uid);
              out.push({ id: uid, roles: mem?.roles || [] });
            }
            stores.push('getMemberIds:' + ids.length);
          } catch (e) {}
        }
      }
      // also UserStore for bot flags
      let userStore;
      for (const id in cache) {
        const m = cache[id]?.exports;
        const cand = m?.default || m;
        if (cand?.getUser && cand?.getCurrentUser && cand?.getUsers) { userStore = cand; break; }
      }
      const enriched = out.map(m => {
        const u = userStore?.getUser?.(m.id);
        return { id: m.id, roles: m.roles, bot: !!u?.bot, username: u?.username || null };
      });
      return { count: enriched.length, stores, members: enriched };
    } catch (e) {
      return { error: String(e), stack: e.stack?.slice(0,300) };
    }
  })()`,
  returnByValue: true,
});

console.log(JSON.stringify({ count: members.result.value?.count, stores: members.result.value?.stores, error: members.result.value?.error, sample: (members.result.value?.members||[]).slice(0,15) }, null, 2));

const listMem = members.result.value?.members || [];
if (!listMem.length) {
  console.log("no webpack members");
  ws.close();
  process.exit(0);
}

async function api(path, opts = {}) {
  const res = await fetch("https://discord.com/api/v9" + path, {
    ...opts,
    headers: { Authorization: tok, "Content-Type": "application/json", ...(opts.headers || {}) },
  });
  const text = await res.text();
  let body; try { body = JSON.parse(text); } catch { body = text; }
  return { status: res.status, body };
}

const roles = (await api(`/guilds/${GUILD}/roles`)).body;
const memberRole = roles.find((r) => /Member/.test(r.name));
const botsRole = roles.find((r) => /🤖┃Bots/.test(r.name) || r.name === "🤖┃Bots");

let memberGiven = 0, botsGiven = 0, already = 0;
for (const m of listMem) {
  if (m.bot) {
    if (botsRole && !(m.roles || []).includes(botsRole.id)) {
      const r = await api(`/guilds/${GUILD}/members/${m.id}/roles/${botsRole.id}`, { method: "PUT" });
      if (r.status === 204) { botsGiven++; console.log("bots", m.username || m.id); }
      await new Promise(r => setTimeout(r, 280));
    }
  } else {
    if (memberRole && !(m.roles || []).includes(memberRole.id)) {
      const r = await api(`/guilds/${GUILD}/members/${m.id}/roles/${memberRole.id}`, { method: "PUT" });
      if (r.status === 204) { memberGiven++; console.log("member", m.username || m.id); }
      else console.log("fail", m.username, r.status);
      await new Promise(r => setTimeout(r, 280));
    } else already++;
  }
}
console.log({ memberGiven, botsGiven, already, total: listMem.length });
ws.close();
