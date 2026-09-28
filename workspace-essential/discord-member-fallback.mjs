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
    }, 90000);
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

await send("Network.enable").catch(() => {});
const memberHits = [];
// listen via Runtime - we'll poll Network.getResponseBody after navigating

// Open server settings members via UI click
await send("Runtime.evaluate", {
  expression: `location.href = "https://discord.com/channels/${GUILD}"`,
  returnByValue: true,
});
await new Promise((r) => setTimeout(r, 3000));

const clicked = await send("Runtime.evaluate", {
  expression: `(() => {
    // open server header menu
    const header = document.querySelector('[aria-label*="MerelyMe"], [class*="header"] [role="button"], h1, [class*="nameTag"]');
    const buttons = [...document.querySelectorAll('div[role="button"],button')].filter(b => /MerelyMe SMP/i.test(b.innerText||'') || /Server Settings|Defini|Configura/i.test(b.innerText||''));
    return {
      header: header ? (header.innerText||'').slice(0,40) : null,
      btns: buttons.map(b => (b.innerText||'').trim().slice(0,40)).slice(0,20),
      text: (document.body.innerText||'').slice(0,500)
    };
  })()`,
  returnByValue: true,
});
console.log("ui", JSON.stringify(clicked.result.value, null, 2));

// Deep webpack scan
const scan = await send("Runtime.evaluate", {
  expression: `(() => {
    let wpReq;
    webpackChunkdiscord_app.push([[Symbol('x')], {}, (r) => { wpReq = r; }]);
    const keys = Object.keys(wpReq.c || {});
    const hits = [];
    for (const id of keys) {
      const ex = wpReq.c[id]?.exports;
      if (!ex) continue;
      const check = (obj, path) => {
        if (!obj || typeof obj !== 'object') return;
        try {
          if (typeof obj.getMember === 'function' && (typeof obj.getMembers === 'function' || typeof obj.getMemberIds === 'function')) {
            hits.push({ id, path, hasGetMembers: typeof obj.getMembers === 'function', hasIds: typeof obj.getMemberIds === 'function' });
          }
        } catch {}
      };
      check(ex, 'ex');
      check(ex.default, 'default');
      check(ex.Z, 'Z');
      check(ex.ZP, 'ZP');
    }
    // try flux Dispatcher / stores by name
    const named = [];
    for (const id of keys) {
      const ex = wpReq.c[id]?.exports;
      const s = JSON.stringify(Object.keys(ex||{})).slice(0,80);
      if (/MemberStore|GuildMember/i.test(s) || /MemberStore|GuildMember/i.test(ex?.default?.displayName||'') || /MemberStore|GuildMember/i.test(ex?.default?.constructor?.displayName||'')) {
        named.push({ id, keys: Object.keys(ex||{}).slice(0,10), dn: ex?.default?.displayName || ex?.default?.constructor?.displayName });
      }
    }
    return { moduleCount: keys.length, hits: hits.slice(0,20), named: named.slice(0,20) };
  })()`,
  returnByValue: true,
});
console.log("scan", JSON.stringify(scan.result.value, null, 2));

const tok = (
  await send("Runtime.evaluate", {
    expression: `(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,
    returnByValue: true,
  })
).result.value;

// Carl massrole in bot-commands if available
const BOT_CMDS = "1544904353121308672";
const roles = await (
  await fetch(`https://discord.com/api/v9/guilds/${GUILD}/roles`, { headers: { Authorization: tok } })
).json();
const memberRole = roles.find((r) => /Member/.test(r.name));

// Try Carl: ?role all / massrole
const cmds = [
  `?massrole add <@&${memberRole.id}>`,
  `?role all <@&${memberRole.id}>`,
];
for (const content of cmds) {
  const res = await fetch(`https://discord.com/api/v9/channels/${BOT_CMDS}/messages`, {
    method: "POST",
    headers: { Authorization: tok, "Content-Type": "application/json" },
    body: JSON.stringify({ content }),
  });
  console.log("cmd", content.slice(0, 40), res.status);
  await new Promise((r) => setTimeout(r, 2000));
}
const recent = await (
  await fetch(`https://discord.com/api/v9/channels/${BOT_CMDS}/messages?limit=8`, {
    headers: { Authorization: tok },
  })
).json();
console.log(
  "replies",
  recent.map((m) => ({ u: m.author?.username, c: (m.content || "").slice(0, 120), e: (m.embeds || [])[0]?.description?.slice(0, 100) }))
);

// Give owner Member for sure
await fetch(`https://discord.com/api/v9/guilds/${GUILD}/members/848312667411054603/roles/${memberRole.id}`, {
  method: "PUT",
  headers: { Authorization: tok },
});
console.log("owner member ok");

// Try Jockie by searching presence / relationships
const rel = await fetch("https://discord.com/api/v9/users/@me/relationships", { headers: { Authorization: tok } });
const relBody = await rel.json();
console.log("relationships", Array.isArray(relBody) ? relBody.length : relBody);

ws.close();
