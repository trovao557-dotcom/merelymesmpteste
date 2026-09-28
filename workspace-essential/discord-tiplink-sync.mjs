const GUILD = "1534136666984419348";
const BOT_CMDS = "1544904353121308672";
const APP = "1023897726301249628";

const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tipTab = list.find((t) => String(t.url).includes("tip4serv.com/dashboard/my-servers"));
const discTab = list.find((t) => t.url && t.url.includes("discord.com/channels/153413"));

async function evalTab(tab, expression, awaitPromise = false) {
  const ws = new WebSocket(tab.webSocketDebuggerUrl);
  await new Promise((r, j) => {
    ws.addEventListener("open", r);
    ws.addEventListener("error", j);
  });
  let id = 1;
  const pending = new Map();
  ws.addEventListener("message", (ev) => {
    const m = JSON.parse(String(ev.data));
    if (m.id && pending.has(m.id)) {
      const p = pending.get(m.id);
      pending.delete(m.id);
      m.error ? p.reject(new Error(m.error.message)) : p.resolve(m.result);
    }
  });
  const send = (method, params = {}) =>
    new Promise((resolve, reject) => {
      pending.set(id, { resolve, reject });
      ws.send(JSON.stringify({ id, method, params }));
      id++;
    });
  const r = await send("Runtime.evaluate", { expression, returnByValue: true, awaitPromise });
  ws.close();
  if (r.exceptionDetails) throw new Error(r.exceptionDetails.text || "eval");
  return r.result.value;
}

// 1) Open Tip4Serv Connect Discord modal
await evalTab(
  tipTab,
  `(() => { location.href='https://tip4serv.com/dashboard/my-servers'; return 'nav'; })()`
);
await new Promise((r) => setTimeout(r, 2500));
await evalTab(
  tipTab,
  `(() => { const btn=[...document.querySelectorAll('button')].find(x=>/Connect a Discord Server/i.test(x.innerText||'')); if(btn) btn.click(); return !!btn; })()`
);
await new Promise((r) => setTimeout(r, 1000));
const modal = await evalTab(
  tipTab,
  `(() => { const modal=[...document.querySelectorAll('.modal')].find(m=>getComputedStyle(m).display!=='none'); return modal?modal.innerText.slice(0,500):'no'; })()`
);
console.log("modal open", modal.slice(0, 200));

// 2) Run /tiplink while modal is open
const disc = await evalTab(
  discTab,
  `(() => {
    const tok = (()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})();
    let sid=null;
    webpackChunkdiscord_app.push([[Symbol()],{},(req)=>{for(const m of Object.values(req.c||{})){try{const fn=m?.exports?.default?.getSessionId||m?.exports?.getSessionId||m?.exports?.Z?.getSessionId; if(fn){const v=fn(); if(typeof v==='string'&&v.length>8) sid=v;}}catch{}}}]);
    webpackChunkdiscord_app.pop();
    return {tok,sid};
  })()`
);

const idx = await fetch(`https://discord.com/api/v9/guilds/${GUILD}/application-command-index`, {
  headers: { Authorization: disc.tok },
}).then((r) => r.json());
const tiplink = (idx.application_commands || []).find((c) => c.name === "tiplink" && c.application_id === APP);
const res = await fetch("https://discord.com/api/v9/interactions", {
  method: "POST",
  headers: { Authorization: disc.tok, "Content-Type": "application/json" },
  body: JSON.stringify({
    type: 2,
    application_id: APP,
    guild_id: GUILD,
    channel_id: BOT_CMDS,
    session_id: disc.sid,
    data: { version: tiplink.version, id: tiplink.id, name: "tiplink", type: 1, options: [] },
    nonce: String(Date.now() * 1000),
  }),
});
console.log("tiplink status", res.status);

await new Promise((r) => setTimeout(r, 3000));

// Check Tip4Serv DMs
const dms = await fetch("https://discord.com/api/v9/users/@me/channels", {
  headers: { Authorization: disc.tok },
}).then((r) => r.json());
const tipDm = (dms || []).find((c) => (c.recipients || []).some((u) => u.id === APP || /tip4/i.test(u.username || "")));
console.log(
  "tip dm",
  tipDm?.id,
  tipDm?.recipients?.map((u) => u.username)
);
if (tipDm?.id) {
  const msgs = await fetch(`https://discord.com/api/v9/channels/${tipDm.id}/messages?limit=5`, {
    headers: { Authorization: disc.tok },
  }).then((r) => r.json());
  console.log(
    "dm msgs",
    msgs.map((m) => (m.content || "").slice(0, 300) + " | " + (m.embeds?.[0]?.description || "").slice(0, 200))
  );
}

// Refresh Tip4Serv servers
await evalTab(tipTab, `location.reload(); 'r'`);
await new Promise((r) => setTimeout(r, 3000));
const servers = await evalTab(tipTab, `(() => (document.body.innerText||'').slice(0,3500))()`);
console.log("servers page", servers.includes("Discord") ? servers.match(/Discord[\\s\\S]{0,500}|Minecraft[\\s\\S]{0,400}/)?.[0] : servers.slice(0, 800));
console.log("---FULL---");
console.log(servers);
