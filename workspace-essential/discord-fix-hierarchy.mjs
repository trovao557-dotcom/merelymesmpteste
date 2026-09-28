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
    pending.set(i, { resolve, reject });
    setTimeout(() => {
      if (pending.has(i)) {
        pending.delete(i);
        reject(Error("timeout " + method));
      }
    }, 25000);
    ws.send(JSON.stringify({ id: i, method, params }));
  });
const tok = (
  await send("Runtime.evaluate", {
    expression: `(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,
    returnByValue: true,
  })
).result.value;

const roles = await (
  await fetch(`https://discord.com/api/v9/guilds/${GUILD}/roles`, {
    headers: { Authorization: tok },
  })
).json();

const byName = (re) => roles.find((r) => re.test(r.name));
const order = [
  byName(/Owner/),
  byName(/Admin/),
  byName(/Mod/),
  byName(/^carl-bot$/i),
  byName(/Ticket Tool/),
  byName(/Statbot/),
  byName(/Jockie/),
  byName(/Ticket Staff/),
  byName(/Helper/),
  byName(/Media/),
  byName(/Prime/),
  byName(/Member/),
].filter(Boolean);

// Assign positions from high to low (Discord: higher = above)
let pos = order.length;
const payload = order.map((r) => ({ id: r.id, position: pos-- }));
console.log(
  "payload",
  payload.map((p) => {
    const n = roles.find((r) => r.id === p.id)?.name;
    return p.position + " " + n;
  })
);

const res = await fetch(`https://discord.com/api/v9/guilds/${GUILD}/roles`, {
  method: "PATCH",
  headers: { Authorization: tok, "Content-Type": "application/json" },
  body: JSON.stringify(payload),
});
const text = await res.text();
console.log("status", res.status, text.slice(0, 300));

const roles2 = await (
  await fetch(`https://discord.com/api/v9/guilds/${GUILD}/roles`, {
    headers: { Authorization: tok },
  })
).json();
console.log(
  "AFTER",
  roles2
    .sort((a, b) => b.position - a.position)
    .map((r) => r.position + " " + r.name)
    .join(" | ")
);

// Assign Member role to guild owner if missing (optional clean)
const OWNER = "848312667411054603";
try {
  const me = await (
    await fetch(`https://discord.com/api/v9/guilds/${GUILD}/members/${OWNER}`, {
      headers: { Authorization: tok },
    })
  ).json();
  const memberRole = byName(/Member/);
  if (memberRole && !(me.roles || []).includes(memberRole.id)) {
    await fetch(
      `https://discord.com/api/v9/guilds/${GUILD}/members/${OWNER}/roles/${memberRole.id}`,
      { method: "PUT", headers: { Authorization: tok } }
    );
    console.log("gave owner Member role");
  } else console.log("owner already has member or no role");
} catch (e) {
  console.log("owner member assign", e.message);
}

ws.close();
