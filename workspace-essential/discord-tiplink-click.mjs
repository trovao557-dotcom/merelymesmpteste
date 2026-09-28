const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab = list.find((t) => t.url && t.url.includes("discord.com/channels/1534136666984419348/1544904353121308672"))
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

await send("Page.bringToFront").catch(() => {});
await send("Page.navigate", {
  url: "https://discord.com/channels/1534136666984419348/1544904353121308672",
});
await new Promise((r) => setTimeout(r, 4000));

// Re-run tiplink to get fresh ephemeral with components
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
await new Promise((r) => setTimeout(r, 2500));

const inspect = await send("Runtime.evaluate", {
  expression: `(() => {
    const buttons = [...document.querySelectorAll('button')].map(b => ({
      t: (b.innerText||'').trim().slice(0,80),
      aria: b.getAttribute('aria-label'),
      id: b.id,
      cls: (b.className||'').toString().slice(0,60)
    })).filter(b => b.t || b.aria);
    const selects = [...document.querySelectorAll('[role="listbox"], [class*="select"], select')].map(s => s.innerText.slice(0,120));
    const comps = [...document.querySelectorAll('[class*="component"], [class*="embed"]')].map(e => e.innerText.trim().slice(0,200)).filter(Boolean).slice(-15);
    return { buttons: buttons.slice(-40), selects, comps };
  })()`,
  returnByValue: true,
});
console.log("inspect", JSON.stringify(inspect.result.value, null, 2).slice(0, 5000));

// Click dropdown / store options related to Tip4Serv link
const clicked = await send("Runtime.evaluate", {
  expression: `(() => {
    const candidates = [...document.querySelectorAll('button, [role="button"], div[class*="select"]')].filter(el => {
      const t = (el.innerText||el.getAttribute('aria-label')||'');
      return /MerelyMe|store|Select|Link|Confirm|23255|Choose/i.test(t) && t.length < 120;
    });
    const info = candidates.map(c => c.innerText.trim().slice(0,80));
    // Prefer opening select menus near the ephemeral
    const selectBtn = [...document.querySelectorAll('button')].reverse().find(b => /select|escolher|store|servidor|server/i.test(b.innerText||'') || /select/i.test(b.getAttribute('aria-label')||''));
    if (selectBtn) { selectBtn.click(); return {action:'select', text:selectBtn.innerText.slice(0,80), info}; }
    if (candidates[0]) { candidates[0].click(); return {action:'cand', text:candidates[0].innerText.slice(0,80), info}; }
    return {action:'none', info, buttons: [...document.querySelectorAll('button')].slice(-15).map(b=>b.innerText.trim())};
  })()`,
  returnByValue: true,
  userGesture: true,
});
console.log("clicked", JSON.stringify(clicked.result.value, null, 2));

await new Promise((r) => setTimeout(r, 1000));
const after = await send("Runtime.evaluate", {
  expression: `(() => {
    const opts = [...document.querySelectorAll('[role="option"], [class*="option"], li')].map(e => e.innerText.trim()).filter(t => t && t.length < 100);
    const merely = opts.find(t => /MerelyMe|23255/i.test(t));
    if (merely) {
      const el = [...document.querySelectorAll('[role="option"], [class*="option"], li')].find(e => e.innerText.includes(merely));
      el?.click();
      return {opts: opts.slice(0,20), clickedOpt: merely};
    }
    return {opts: opts.slice(0,30), text:(document.body.innerText||'').match(/Link my server[\\s\\S]{0,600}/)?.[0]};
  })()`,
  returnByValue: true,
  userGesture: true,
});
console.log("after", JSON.stringify(after.result.value, null, 2));

await new Promise((r) => setTimeout(r, 1500));
const confirm = await send("Runtime.evaluate", {
  expression: `(() => {
    const btn = [...document.querySelectorAll('button')].reverse().find(b => /confirm|link|connect|yes|ok|submit|salvar|confirmar/i.test(b.innerText||''));
    if (btn) { btn.click(); return btn.innerText; }
    return [...document.querySelectorAll('button')].slice(-20).map(b=>b.innerText.trim());
  })()`,
  returnByValue: true,
  userGesture: true,
});
console.log("confirm", confirm.result.value);

await new Promise((r) => setTimeout(r, 2000));
const final = await send("Runtime.evaluate", {
  expression: `(() => (document.body.innerText||'').match(/Link my server[\\s\\S]{0,800}|successfully|connected|linked|error|MerelyMeSMP Store[\\s\\S]{0,300}/i)?.[0] || (document.body.innerText||'').slice(-800))()`,
  returnByValue: true,
});
console.log("final", final.result.value);
ws.close();
