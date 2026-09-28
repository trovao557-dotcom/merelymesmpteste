const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab = list.find((t) => t.url && t.url.includes("1544904353121308672"))
  || list.find((t) => t.url && t.url.includes("discord.com/channels/153413"));
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

const idx = await fetch(
  "https://discord.com/api/v9/guilds/1534136666984419348/application-command-index",
  { headers: { Authorization: auth.tok } }
).then((r) => r.json());
const tiplink = (idx.application_commands || []).find(
  (c) => c.name === "tiplink" && c.application_id === "1023897726301249628"
);

await fetch("https://discord.com/api/v9/interactions", {
  method: "POST",
  headers: { Authorization: auth.tok, "Content-Type": "application/json" },
  body: JSON.stringify({
    type: 2,
    application_id: "1023897726301249628",
    guild_id: "1534136666984419348",
    channel_id: "1544904353121308672",
    session_id: auth.sid,
    data: { version: tiplink.version, id: tiplink.id, name: "tiplink", type: 1, options: [] },
    nonce: String(Date.now() * 1000),
  }),
});
await new Promise((r) => setTimeout(r, 2000));

// Find the ephemeral message block and dump interactive nodes
const dump = await send("Runtime.evaluate", {
  expression: `(() => {
    const root = [...document.querySelectorAll('li, article, div')].find(el => /Link my server to Tip4Serv/i.test(el.innerText||'') && el.innerText.length < 2000);
    if (!root) return {err:'no ephemeral root', sample:(document.body.innerText||'').match(/tiplink[\\s\\S]{0,300}|Link my server[\\s\\S]{0,300}/i)?.[0]};
    const interactive = [...root.querySelectorAll('button, [role="button"], [role="combobox"], [aria-haspopup], select, a')].map(el => ({
      tag: el.tagName,
      role: el.getAttribute('role'),
      aria: el.getAttribute('aria-label'),
      text: (el.innerText||'').trim().slice(0,100),
      cls: (el.className||'').toString().slice(0,80)
    }));
    // also all clickable-looking
    const all = [...root.querySelectorAll('*')].filter(el => el.childElementCount === 0 || /button|select|component/i.test(el.className||'')).slice(0,80).map(el => ({
      tag: el.tagName,
      text: (el.innerText||'').trim().slice(0,60),
      cls: (el.className||'').toString().slice(0,70)
    })).filter(x => x.text || /button|select|component/i.test(x.cls));
    return { interactive, all: all.slice(0,40), html: root.innerHTML.slice(0,2500) };
  })()`,
  returnByValue: true,
});
console.log(JSON.stringify(dump.result.value, null, 2).slice(0, 8000));
ws.close();
