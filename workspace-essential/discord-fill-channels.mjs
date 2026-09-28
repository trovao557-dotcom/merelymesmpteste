// Fill empty channels + fix ip-links + Tip4Serv store settings
const GUILD = "1534136666984419348";

const CHANNELS = {
  updates:    "1544904338860675074",
  media:      "1544904349640040508",
  lft:        "1544904360809603142",
  vote:       "1544904371601674251",
  leaderboard:"1544904375779065906",
  playtime:   "1544904379344359537",
  iplinks:    "1544904335530528788",
  store:      "1544904368157884476",
};

const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab = list.find(t => t.url && t.url.includes("discord.com/channels/153413"));
const ws = new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
let id=1; const pending=new Map();
ws.onmessage=ev=>{const m=JSON.parse(ev.data); if(m.id==null)return; const p=pending.get(m.id); if(!p)return; pending.delete(m.id); m.error?p.reject(Error(m.error.message)):p.resolve(m.result);};
const send=(method,params={})=>new Promise((resolve,reject)=>{const i=id++; const t=setTimeout(()=>{if(pending.has(i)){pending.delete(i);reject(Error("to"));}},20000); pending.set(i,{resolve:v=>{clearTimeout(t);resolve(v)},reject:e=>{clearTimeout(t);reject(e)}}); ws.send(JSON.stringify({id:i,method,params}));});
const tok=(await send("Runtime.evaluate",{expression:`(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,returnByValue:true})).result.value;
const api=async(p,o={})=>{const r=await fetch("https://discord.com/api/v9"+p,{...o,headers:{Authorization:tok,"Content-Type":"application/json",...(o.headers||{})}}); const t2=await r.text(); let b; try{b=JSON.parse(t2);}catch{b=t2;} return {status:r.status,body:b};};

const post = async (ch, content) => {
  const r = await api(`/channels/${ch}/messages`, {method:"POST", body:JSON.stringify({content})});
  console.log("post", ch, r.status, (r.body?.id || r.body?.message||"").slice(0,40));
  await new Promise(r=>setTimeout(r,350));
};

// ── 🌐 ip-links ── clean + pin-worthy single embed-style block
const oldMsgs = (await api(`/channels/${CHANNELS.iplinks}/messages?limit=30`)).body;
for (const m of (Array.isArray(oldMsgs)?oldMsgs:[])) {
  // delete our own old dupes (not the pinned 1545061196929957958)
  if (m.id === "1545061196929957958") continue;
  if (m.author?.id === "848312667411054603") {
    await api(`/channels/${CHANNELS.iplinks}/messages/${m.id}`, {method:"DELETE"});
    await new Promise(r=>setTimeout(r,300));
  }
}
// repost clean
await post(CHANNELS.iplinks,
`**MerelyMeSMP — LINKS**

🖥️  **Minecraft IP:** \`merelymesmp.com\`
🌐  **Website:** <https://merelymesmp.com>
🛒  **Store:** <https://merelymesmpstore.tip4serv.com>
🎮  **Discord invite:** <https://discord.gg/NEybDXCkhA>
📺  **Twitch:** <https://twitch.tv/merelyme>`
);

// ── 🛒 store ── clean + repost
const stOld = (await api(`/channels/${CHANNELS.store}/messages?limit=10`)).body;
for (const m of (Array.isArray(stOld)?stOld:[])) {
  if (m.author?.id === "848312667411054603") {
    await api(`/channels/${CHANNELS.store}/messages/${m.id}`, {method:"DELETE"});
    await new Promise(r=>setTimeout(r,300));
  }
}
await post(CHANNELS.store,
`**STORE — MerelyMeSMP**

Buy ranks (Knight · Warrior · Macer · Prime) and points packages.
Your rank is applied **automatically in-game** after purchase.
Link your Discord at checkout to also receive your Discord role.

🛒 <https://merelymesmpstore.tip4serv.com>
🌐 <https://merelymesmp.com>`
);

// ── 🎉 updates ── placeholder
await post(CHANNELS.updates,
`**UPDATES** — Season 1 is live 🎉
Server opens **07/09/2026**. Follow for patch notes, events and news.

📺 Twitch live: <https://twitch.tv/merelyme>`
);

// ── 📸 media ── rules
await post(CHANNELS.media,
`**MEDIA**
Post clips, screenshots and highlights from MerelyMeSMP here.

📌 Rules: Minecraft content only · No watermarks from other servers · Keep it SFW`
);

// ── 🤝 looking-for-team
await post(CHANNELS.lft,
`**LOOKING FOR TEAM / ALLIES**
Post here if you're looking for someone to team up, trade or practice PvP with.

Format: \`[REGION] what you're looking for\`
Example: \`[EU] Looking for practice partner, Warrior rank\``
);

// ── 🗳️ vote
await post(CHANNELS.vote,
`**VOTE FOR MERELYMESMP**
Every vote helps the server grow and earns you rewards in-game.

Vote links → use \`/vote\` in-game or check <https://merelymesmp.com>`
);

// ── 🏆 leaderboard
await post(CHANNELS.leaderboard,
`**LEADERBOARD — Season 1**
Top players by kills, KDR and economy will be listed here.

Check live stats: <https://merelymesmp.com/community#leaderboard>
In-game: \`/baltop\` · \`/stats\` · \`/killtop\``
);

// ── ⏱️ playtime
await post(CHANNELS.playtime,
`**PLAYTIME RECORDS — Season 1**
Top players by hours online will be tracked here.

In-game command: \`/playtime\`
Rewards for top playtime each season — stay tuned.`
);

ws.close();
console.log("done");
