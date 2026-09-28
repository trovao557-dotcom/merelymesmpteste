const GUILD = "1534136666984419348";
const CREATE_TICKET = "1544904386587787334";
const TT_ROLE = "1544909536194986048"; // Ticket Tool managed role
const TT_BOT = "557628352828014614";

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

// Allow Ticket Tool role to view + send in create-ticket
// VIEW_CHANNEL=1024, SEND_MESSAGES=2048, EMBED_LINKS=16384, ATTACH_FILES=32768, READ_MESSAGE_HISTORY=65536, USE_EXTERNAL_EMOJIS=262144, ADD_REACTIONS=64, MANAGE_CHANNELS=16, MANAGE_ROLES=268435456
const allow =
  1024n | 2048n | 16384n | 32768n | 65536n | 262144n | 64n | 16n | 268435456n;
const r1 = await api(`/channels/${CREATE_TICKET}/permissions/${TT_ROLE}`, {
  method: "PUT",
  body: JSON.stringify({
    id: TT_ROLE,
    type: 0,
    allow: allow.toString(),
    deny: "0",
  }),
});
console.log("perm role", r1.status, JSON.stringify(r1.body).slice(0, 200));

// Also SUPPORT category
const SUPPORT = "1544904383106387968";
const r2 = await api(`/channels/${SUPPORT}/permissions/${TT_ROLE}`, {
  method: "PUT",
  body: JSON.stringify({
    id: TT_ROLE,
    type: 0,
    allow: (1024n | 16n | 268435456n).toString(),
    deny: "0",
  }),
});
console.log("perm cat", r2.status);

// Carl + Statbot can see logs
const LOGS = "1544904420536483861";
const CARL_ROLE = "1544907267994816583";
await api(`/channels/${LOGS}/permissions/${CARL_ROLE}`, {
  method: "PUT",
  body: JSON.stringify({
    id: CARL_ROLE,
    type: 0,
    allow: (1024n | 2048n | 16384n | 65536n).toString(),
    deny: "0",
  }),
});

const ch = await api(`/channels/${CREATE_TICKET}`);
console.log(
  "channel overwrites",
  (ch.body.permission_overwrites || []).map((o) => o.id + ":" + o.allow + "/" + o.deny)
);

ws.close();
