const GUILD = "1534136666984419348";
const BOT_CMDS = "1544904353121308672";
const APP = "1023897726301249628";

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

await send("Page.navigate", { url: `https://discord.com/channels/${GUILD}/${BOT_CMDS}` });
await new Promise((r) => setTimeout(r, 4000));

const auth = (
  await send("Runtime.evaluate", {
    expression: `(()=>{
    const tok=(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})();
    let sid=null;
    webpackChunkdiscord_app.push([[Symbol()],{},(req)=>{for(const m of Object.values(req.c||{})){try{const fn=m?.exports?.default?.getSessionId||m?.exports?.getSessionId||m?.exports?.Z?.getSessionId; if(fn){const v=fn(); if(typeof v==='string'&&v.length>8) sid=v;}}catch{}}}]);
    webpackChunkdiscord_app.pop();
    return {tok,sid};
  })()`,
    returnByValue: true,
  })
).result.value;

const idx = await fetch(`https://discord.com/api/v9/guilds/${GUILD}/application-command-index`, {
  headers: { Authorization: auth.tok },
}).then((r) => r.json());
const tiplink = (idx.application_commands || []).find((c) => c.name === "tiplink" && c.application_id === APP);

await fetch("https://discord.com/api/v9/interactions", {
  method: "POST",
  headers: { Authorization: auth.tok, "Content-Type": "application/json" },
  body: JSON.stringify({
    type: 2,
    application_id: APP,
    guild_id: GUILD,
    channel_id: BOT_CMDS,
    session_id: auth.sid,
    data: { version: tiplink.version, id: tiplink.id, name: "tiplink", type: 1, options: [] },
    nonce: String(Date.now() * 1000),
  }),
});

await new Promise((r) => setTimeout(r, 2500));

const ui = await send("Runtime.evaluate", {
  expression: `(() => {
    const all = document.body.innerText || '';
    const hits = [...document.querySelectorAll('[class*="ephemeral"], [class*="message"], article, li')].map(el => el.innerText.trim()).filter(t => /tip4|tiplink|store|link|error|permission|success|connected|already|admin/i.test(t) && t.length < 500);
    return { hits: [...new Set(hits)].slice(0, 20), hasOnlyYou: /only you can see|só você pode ver/i.test(all), snippet: all.match(/só você[\\s\\S]{0,400}|Only you[\\s\\S]{0,400}|Tip4Serv[\\s\\S]{0,400}/i)?.[0] };
  })()`,
  returnByValue: true,
});
console.log(JSON.stringify(ui.result.value, null, 2));

// Also try /help from Tip4Serv
const help = (idx.application_commands || []).find((c) => c.name === "help" && c.application_id === APP);
await fetch("https://discord.com/api/v9/interactions", {
  method: "POST",
  headers: { Authorization: auth.tok, "Content-Type": "application/json" },
  body: JSON.stringify({
    type: 2,
    application_id: APP,
    guild_id: GUILD,
    channel_id: BOT_CMDS,
    session_id: auth.sid,
    data: { version: help.version, id: help.id, name: "help", type: 1, options: [] },
    nonce: String(Date.now() * 1000 + 1),
  }),
});
await new Promise((r) => setTimeout(r, 2500));
const ui2 = await send("Runtime.evaluate", {
  expression: `(() => ({ text: (document.body.innerText||'').match(/Tip4Serv[\\s\\S]{0,800}|help[\\s\\S]{0,400}/i)?.[0], hits: [...document.querySelectorAll('article, [class*=messageContent]')].slice(-8).map(e=>e.innerText.trim().slice(0,200)) }))()`,
  returnByValue: true,
});
console.log("after help", JSON.stringify(ui2.result.value, null, 2));

ws.close();
