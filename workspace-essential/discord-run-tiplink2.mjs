const GUILD = "1534136666984419348";
const BOT_CMDS = "1544904353121308672";

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
        reject(Error("to " + method));
      }
    }, 20000);
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
await send("Page.navigate", { url: `https://discord.com/channels/${GUILD}/${BOT_CMDS}` });
await new Promise((r) => setTimeout(r, 5000));

// Find session id properly
const session = (
  await send("Runtime.evaluate", {
    expression: `(() => {
      let sid = null;
      try {
        webpackChunkdiscord_app.push([[Symbol()], {}, (req) => {
          for (const m of Object.values(req.c || {})) {
            const exp = m?.exports;
            const cand = [
              exp?.default?.getSessionId,
              exp?.getSessionId,
              exp?.Z?.getSessionId,
              exp?.ZP?.getSessionId,
            ].filter(Boolean);
            for (const fn of cand) {
              try {
                const v = fn();
                if (typeof v === 'string' && v.length > 8) sid = v;
              } catch {}
            }
            try {
              const s = exp?.default?.getState?.()?.session?.sessionId
                || exp?.getState?.()?.session?.sessionId;
              if (typeof s === 'string') sid = s;
            } catch {}
          }
        }]);
        webpackChunkdiscord_app.pop();
      } catch (e) { return {err:String(e)}; }
      return sid;
    })()`,
    returnByValue: true,
  })
).result.value;
console.log("session", session);

const tok = (
  await send("Runtime.evaluate", {
    expression: `(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,
    returnByValue: true,
  })
).result.value;

if (typeof session === "string") {
  const idx = await fetch(`https://discord.com/api/v9/guilds/${GUILD}/application-command-index`, {
    headers: { Authorization: tok },
  }).then((r) => r.json());
  const tiplink = (idx.application_commands || []).find(
    (c) => c.name === "tiplink" && c.application_id === "1023897726301249628"
  );
  const body = {
    type: 2,
    application_id: "1023897726301249628",
    guild_id: GUILD,
    channel_id: BOT_CMDS,
    session_id: session,
    data: {
      version: tiplink.version,
      id: tiplink.id,
      name: "tiplink",
      type: 1,
      options: [],
    },
    nonce: String(Date.now() * 1000 + Math.floor(Math.random() * 1000)),
  };
  const res = await fetch("https://discord.com/api/v9/interactions", {
    method: "POST",
    headers: { Authorization: tok, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  console.log("interaction", res.status, (await res.text()).slice(0, 400));
} else {
  // UI fallback: click box, type slash
  await send("Runtime.evaluate", {
    expression: `(() => {
      const box = document.querySelector('[data-slate-editor="true"]');
      if (!box) return false;
      box.focus();
      box.click();
      return true;
    })()`,
    returnByValue: true,
    userGesture: true,
  });
  await new Promise((r) => setTimeout(r, 400));
  // Use Input.insertText if available
  try {
    await send("Input.insertText", { text: "/tiplink" });
    console.log("inserted /tiplink");
  } catch (e) {
    console.log("insert fail", e.message);
    await send("Runtime.evaluate", {
      expression: `document.execCommand('insertText', false, '/tiplink')`,
      userGesture: true,
    });
  }
  await new Promise((r) => setTimeout(r, 1200));
  // Click Tip4Serv autocomplete option
  const pick = await send("Runtime.evaluate", {
    expression: `(() => {
      const opt = [...document.querySelectorAll('[class*="option"], [role="option"], div')].find(el => /tiplink/i.test(el.innerText||'') && (el.innerText||'').length < 80);
      if (opt) { opt.click(); return {clicked: opt.innerText.slice(0,80)}; }
      return {text:(document.body.innerText||'').slice(0,500)};
    })()`,
    returnByValue: true,
    userGesture: true,
  });
  console.log("pick", pick.result.value);
  await new Promise((r) => setTimeout(r, 500));
  try {
    await send("Input.dispatchKeyEvent", { type: "keyDown", key: "Enter", code: "Enter", windowsVirtualKeyCode: 13 });
    await send("Input.dispatchKeyEvent", { type: "keyUp", key: "Enter", code: "Enter", windowsVirtualKeyCode: 13 });
    console.log("enter sent");
  } catch (e) {
    console.log("enter fail", e.message);
  }
}

await new Promise((r) => setTimeout(r, 3500));
const msgs = await fetch(`https://discord.com/api/v9/channels/${BOT_CMDS}/messages?limit=8`, {
  headers: { Authorization: tok },
}).then((r) => r.json());
console.log(
  "msgs",
  msgs.map((m) => ({
    u: m.author?.username,
    bot: m.author?.bot,
    c: (m.content || "").slice(0, 180),
    embeds: (m.embeds || []).map((e) => (e.title || "") + " " + (e.description || "").slice(0, 120)),
  }))
);

ws.close();
