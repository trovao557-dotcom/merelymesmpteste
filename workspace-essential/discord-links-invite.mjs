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

const msgs = await (
  await fetch("https://discord.com/api/v9/channels/1544904386587787334/messages?limit=3", {
    headers: { Authorization: tok },
  })
).json();
console.log("components", JSON.stringify(msgs[0]?.components, null, 2));
console.log("embed", msgs[0]?.embeds?.[0]?.description, msgs[0]?.embeds?.[0]?.title);

const inv = await fetch("https://discord.com/api/v9/channels/1544904346188120094/invites", {
  method: "POST",
  headers: { Authorization: tok, "Content-Type": "application/json" },
  body: JSON.stringify({ max_age: 0, max_uses: 0, unique: false }),
});
const invBody = await inv.json();
console.log("invite", inv.status, invBody.code);

const iplinks = "1544904335530528788";
const old = await (
  await fetch(`https://discord.com/api/v9/channels/${iplinks}/messages?limit=15`, {
    headers: { Authorization: tok },
  })
).json();
for (const m of old) {
  if (!m.author?.bot) {
    await fetch(`https://discord.com/api/v9/channels/${iplinks}/messages/${m.id}`, {
      method: "DELETE",
      headers: { Authorization: tok },
    });
    await new Promise((r) => setTimeout(r, 300));
  }
}

const lines = [
  "**CONNECT**",
  "IP: `merelymesmp.g-portal.game`",
  "Website: https://merelymesmp.com",
  "",
  "**DISCORD**",
  "https://merelymesmp.com",
];
if (invBody.code) lines.push("Invite: https://discord.gg/" + invBody.code);
lines.push("", "**SOCIALS**", "Twitch — https://twitch.tv/merelyme", "", "Season 1 · EU · Survival Economy");

const post = await fetch(`https://discord.com/api/v9/channels/${iplinks}/messages`, {
  method: "POST",
  headers: { Authorization: tok, "Content-Type": "application/json" },
  body: JSON.stringify({ content: lines.join("\n") }),
});
console.log("iplinks", post.status);

// Also update rules footer if needed - append discord link in a new clean rules note? skip if already good
ws.close();
