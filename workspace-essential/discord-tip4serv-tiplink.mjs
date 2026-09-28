const GUILD = "1534136666984419348";
const BOT_CMDS = "1544904353121308672";
const TIP_ROLE = "1545049736892129373";
const BOTS_ROLE = "1545039149596287027";

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

const roles = (await api(`/guilds/${GUILD}/roles`)).body;
const byPos = [...roles].sort((a, b) => b.position - a.position);
const bots = roles.find((r) => r.id === BOTS_ROLE);
const tip = roles.find((r) => r.id === TIP_ROLE);
console.log("before tip", tip?.position, "bots", bots?.position);

// Build full positions: tip just below bots
const others = byPos.filter((r) => r.name !== "@everyone" && r.id !== TIP_ROLE);
const insertAt = others.findIndex((r) => r.id === BOTS_ROLE);
const ordered = [...others];
if (insertAt >= 0) ordered.splice(insertAt + 1, 0, tip);
else ordered.splice(4, 0, tip);

const payload = ordered.map((r, i) => ({ id: r.id, position: ordered.length - i }));
const reorder = await api(`/guilds/${GUILD}/roles`, {
  method: "PATCH",
  body: JSON.stringify(payload),
});
console.log("reorder", reorder.status, Array.isArray(reorder.body) ? "ok" : reorder.body?.message);

const after = (await api(`/guilds/${GUILD}/roles`)).body;
console.log(
  "after",
  [...after]
    .sort((a, b) => b.position - a.position)
    .slice(0, 18)
    .map((r) => `${r.position} ${r.name}`)
);

// Navigate Discord to bot-commands and try slash
await send("Page.navigate", {
  url: `https://discord.com/channels/${GUILD}/${BOT_CMDS}`,
});
await new Promise((r) => setTimeout(r, 4000));

// Discover Tip4Serv slash commands
const cmds = await api(`/guilds/${GUILD}/application-command-index`);
const tipCmds = (cmds.body?.application_commands || cmds.body?.applications || []).filter?.(Boolean);
console.log("cmd index status", cmds.status);
if (cmds.body?.application_commands) {
  const t4 = cmds.body.application_commands.filter(
    (c) => /tiplink|tip4|link/i.test(c.name) || c.application_id === "1023897726301249628"
  );
  console.log(
    "tip cmds",
    t4.map((c) => ({ n: c.name, id: c.id, app: c.application_id }))
  );
}

// Try typing slash via UI
const typed = await send("Runtime.evaluate", {
  expression: `(() => {
    const box = document.querySelector('[role="textbox"][data-slate-editor="true"], div[contenteditable="true"]');
    if (!box) return {err:'no box'};
    box.focus();
    document.execCommand('insertText', false, '/tiplink');
    return {ok:true, text: box.innerText};
  })()`,
  returnByValue: true,
  userGesture: true,
});
console.log("typed", typed.result.value);

ws.close();
