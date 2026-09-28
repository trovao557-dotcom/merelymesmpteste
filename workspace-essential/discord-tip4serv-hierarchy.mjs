const GUILD = "1534136666984419348";
const BOT_CMDS = "1544904353121308672";
const TIP4_BOT = "1023897726301249628";
const BOTS_ROLE = "1545039149596287027";

const RANK_ROLES = {
  Knight: "1545039134106845205",
  Warrior: "1545039138183716955",
  Macer: "1545039141773770772",
  Prime: "1544904299627159632",
};

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
const tipMember = (await api(`/guilds/${GUILD}/members/${TIP4_BOT}`)).body;
console.log(
  "tip member roles",
  (tipMember.roles || []).map((rid) => roles.find((r) => r.id === rid)?.name || rid)
);

const tipRole = roles.find((r) => /tip4/i.test(r.name)) || roles.find((r) => tipMember.roles?.includes(r.id) && r.name !== "@everyone");
console.log(
  "tip role candidates",
  roles
    .filter((r) => /tip4|Tip4/i.test(r.name) || tipMember.roles?.includes(r.id))
    .map((r) => ({ n: r.name, id: r.id, pos: r.position }))
);

// Hierarchy: Owner > Admin > Mod > Bots > Tip4Serv > ranks
const sorted = [...roles].sort((a, b) => b.position - a.position);
console.log(
  "top roles",
  sorted.slice(0, 20).map((r) => `${r.position} ${r.name}`)
);

const botsRole = roles.find((r) => r.id === BOTS_ROLE);
const tipManaged = roles.find((r) => /tip4serv/i.test(r.name));
if (tipManaged && botsRole) {
  // Put Tip4Serv just under Bots (position = bots.position - need full reorder via PATCH positions)
  const targetPos = Math.max(1, botsRole.position - 1);
  // Ensure tip role is above rank roles
  const warrior = roles.find((r) => r.id === RANK_ROLES.Warrior);
  if (tipManaged.position <= (warrior?.position || 0)) {
    const payload = roles
      .filter((r) => r.name !== "@everyone")
      .map((r) => {
        let pos = r.position;
        if (r.id === tipManaged.id) pos = Math.max(targetPos, (warrior?.position || 0) + 1);
        return { id: r.id, position: pos };
      });
    // simpler: set tip role position high via single role patch isn't allowed for position
    const reorder = await api(`/guilds/${GUILD}/roles`, {
      method: "PATCH",
      body: JSON.stringify([{ id: tipManaged.id, position: Math.max(botsRole.position - 1, (warrior?.position || 0) + 2) }]),
    });
    console.log("reorder tip", reorder.status, Array.isArray(reorder.body) ? "ok roles" : reorder.body?.message);
  } else {
    console.log("tip already above warrior", tipManaged.position, warrior?.position);
  }
}

// Assign Bots role to Tip4Serv bot
if (!(tipMember.roles || []).includes(BOTS_ROLE)) {
  const add = await api(`/guilds/${GUILD}/members/${TIP4_BOT}/roles/${BOTS_ROLE}`, { method: "PUT" });
  console.log("add bots role", add.status, add.body?.message || "ok");
}

// Post /tiplink instruction in bot-commands (slash must be run by human or via interactions - try message)
const tipMsg = await api(`/channels/${BOT_CMDS}/messages`, {
  method: "POST",
  body: JSON.stringify({
    content:
      "**Tip4Serv link step:** In this channel run slash command `/tiplink` (from Tip4Serv bot) to finish connecting the store. Then drag **Tip4Serv** role above Knight/Warrior/Macer/Prime in Server Settings → Roles.",
  }),
});
console.log("tip msg", tipMsg.status, tipMsg.body?.id || tipMsg.body?.message);

ws.close();
