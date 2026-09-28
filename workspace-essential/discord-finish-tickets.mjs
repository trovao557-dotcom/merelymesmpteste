const GUILD = "1534136666984419348";
const CREATE_TICKET = "1544904386587787334";
const TICKET_STAFF = "1544904309882232842";
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
        reject(Error("timeout"));
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

async function api(path, opts = {}) {
  const res = await fetch(`https://discord.com/api/v9${path}`, {
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

// Try convert to forum
const forum = await api(`/channels/${CREATE_TICKET}`, {
  method: "PATCH",
  body: JSON.stringify({
    type: 15,
    topic: "Open a ticket — pick a tag and post. Staff will reply here.",
    available_tags: [
      { name: "Support", moderated: false, emoji_name: "🎫" },
      { name: "Appeal", moderated: false, emoji_name: "⚖️" },
      { name: "Buy", moderated: false, emoji_name: "🛒" },
      { name: "Bug", moderated: false, emoji_name: "🐛" },
      { name: "Staff App", moderated: false, emoji_name: "📝" },
    ],
    default_reaction_emoji: { emoji_name: "🎫" },
  }),
});
console.log("forum convert", forum.status, JSON.stringify(forum.body).slice(0, 400));

if (forum.status >= 400) {
  // Keep text channel; post clean setup note
  const msg = await api(`/channels/${CREATE_TICKET}/messages`, {
    method: "POST",
    body: JSON.stringify({
      content:
        "**TICKETS**\n\nTicket Tool is in this server.\nTo finish the button panel (30s):\n1. Open https://tickettool.xyz/dashboard\n2. Login with Discord → select **MerelyMe SMP**\n3. Create a panel → send it to this channel\n4. Set support role to Ticket Staff\n\nUntil then, ping <@&" +
        TICKET_STAFF +
        "> in staff-chat or open a thread here.",
    }),
  });
  console.log("fallback msg", msg.status);
} else {
  // Forum guidelines
  const g = await api(`/channels/${CREATE_TICKET}`, {
    method: "PATCH",
    body: JSON.stringify({
      name: "🎫┃create-ticket",
    }),
  });
  console.log("rename", g.status);
}

// Verify bots + joinrole still set - ask Carl again briefly
const verify = await api(`/channels/1544904353121308672/messages`, {
  method: "POST",
  body: JSON.stringify({ content: "?joinrole" }),
});
console.log("joinrole check", verify.status);
await new Promise((r) => setTimeout(r, 2000));
const recent = await api(`/channels/1544904353121308672/messages?limit=5`);
console.log(
  "recent",
  (recent.body || []).map((m) => ({
    u: m.author?.username,
    c: (m.content || "").slice(0, 120),
    e: (m.embeds || []).map((x) => (x.description || x.title || "").slice(0, 100)),
  }))
);

// Bot members
for (const [name, bid] of [
  ["Carl", "235148962103951360"],
  ["TicketTool", "557628352828014614"],
  ["Statbot", "491769129318088714"],
]) {
  const m = await api(`/guilds/${GUILD}/members/${bid}`);
  console.log(name, m.status === 200 ? m.body.user?.username : "MISSING");
}

ws.close();
