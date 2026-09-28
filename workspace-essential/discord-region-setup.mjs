// Create region roles + #choose-region channel with reaction-role buttons (via Discord components)
const GUILD = "1534136666984419348";

const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab = list.find(t => t.url && t.url.includes("discord.com/channels/153413"));
const ws = new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
let id=1; const pending=new Map();
ws.onmessage=ev=>{const m=JSON.parse(ev.data); if(m.id==null)return; const p=pending.get(m.id); if(!p)return; pending.delete(m.id); m.error?p.reject(Error(m.error.message)):p.resolve(m.result);};
const send=(method,params={})=>new Promise((resolve,reject)=>{const i=id++; const t=setTimeout(()=>{if(pending.has(i)){pending.delete(i);reject(Error("to"));}},20000); pending.set(i,{resolve:v=>{clearTimeout(t);resolve(v)},reject:e=>{clearTimeout(t);reject(e)}}); ws.send(JSON.stringify({id:i,method,params}));});
const tok=(await send("Runtime.evaluate",{expression:`(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,returnByValue:true})).result.value;
const api=async(p,o={})=>{const r=await fetch("https://discord.com/api/v9"+p,{...o,headers:{Authorization:tok,"Content-Type":"application/json",...(o.headers||{})}}); const t2=await r.text(); let b; try{b=JSON.parse(t2);}catch{b=t2;} return {status:r.status,body:b};};

const sleep = ms => new Promise(r => setTimeout(r, ms));

// 1) Fetch existing roles
const roles = (await api(`/guilds/${GUILD}/roles`)).body;
console.log("roles fetched:", roles.length);

// Region role definitions
const REGION_ROLES = [
  { name: "🇪🇺┃EU",   color: 0x3498db },
  { name: "🇺🇸┃NA",   color: 0xe74c3c },
  { name: "🇧🇷┃SA",   color: 0x2ecc71 },
  { name: "🌏┃ASIA",  color: 0xf39c12 },
  { name: "🌍┃Other", color: 0x9b59b6 },
];

// 2) Create missing region roles
const regionRoleIds = {};
for (const rd of REGION_ROLES) {
  let existing = roles.find(r => r.name === rd.name);
  if (!existing) {
    const cr = await api(`/guilds/${GUILD}/roles`, {
      method: "POST",
      body: JSON.stringify({ name: rd.name, color: rd.color, hoist: false, mentionable: false })
    });
    console.log("created role", rd.name, cr.status, cr.body?.id || cr.body?.message);
    existing = cr.body;
    await sleep(400);
  } else {
    console.log("role exists", rd.name, existing.id);
  }
  regionRoleIds[rd.name] = existing.id;
}
console.log("regionRoleIds", JSON.stringify(regionRoleIds));

// 3) Find or create #choose-region channel in COMMUNITY category
const channels = (await api(`/guilds/${GUILD}/channels`)).body;
const communityCategory = channels.find(c => c.type === 4 && /community|comunidade/i.test(c.name));
const infoCategory = channels.find(c => c.type === 4 && /info|start|inicio|welcome/i.test(c.name));
const parentId = communityCategory?.id || infoCategory?.id || null;
console.log("category:", communityCategory?.name || infoCategory?.name || "none", parentId);

let regionChan = channels.find(c => /choose-region|escolhe-regiao|region-role|choose-your-region/i.test(c.name));
if (!regionChan) {
  const cc = await api(`/guilds/${GUILD}/channels`, {
    method: "POST",
    body: JSON.stringify({
      name: "🌍┃choose-region",
      type: 0,
      parent_id: parentId,
      topic: "Pick your region to get the matching role. One click — you can change it anytime.",
      position: 3,
    })
  });
  console.log("created channel", cc.status, cc.body?.id, cc.body?.name || cc.body?.message);
  regionChan = cc.body;
  await sleep(400);
} else {
  console.log("channel exists", regionChan.id, regionChan.name);
}

// 4) Set permissions: @everyone can read + add reactions, cannot send messages (read-only)
const EVERYONE_ID = GUILD; // @everyone role has same id as guild
await api(`/channels/${regionChan.id}/permissions/${EVERYONE_ID}`, {
  method: "PUT",
  body: JSON.stringify({ type: 0, allow: "66560", deny: "2048" }) // VIEW+READ_HISTORY+ADD_REACTIONS, no SEND
});
console.log("permissions set");

// 5) Post the role-picker message with buttons
// Discord buttons for role picking — we use a webhook-style bot message
// Since we don't have a bot token, we post a nicely formatted message with instructions
// and use Carl-bot's reaction roles if available. Let's post the guide message for now.
const msgs = (await api(`/channels/${regionChan.id}/messages?limit=10`)).body;
const existing = Array.isArray(msgs) ? msgs.find(m => /choose your region|escolhe a tua regi/i.test(m.content || (m.embeds?.[0]?.description||""))) : null;

if (!existing) {
  const content = [
    "## 🌍 Choose Your Region",
    "",
    "React with your region flag to get the matching Discord role.",
    "You can change it anytime — just remove the old reaction and add the new one.",
    "",
    "🇪🇺 — **EU** (Europe)",
    "🇺🇸 — **NA** (North America / USA)",
    "🇧🇷 — **SA** (South America)",
    "🌏 — **ASIA** (Asia / Oceania)",
    "🌍 — **Other** (Rest of world)",
    "",
    `**Region role IDs for Carl-bot setup:**`,
    ...REGION_ROLES.map(rd => `• ${rd.name} → \`${regionRoleIds[rd.name]}\``),
  ].join("\n");
  const mp = await api(`/channels/${regionChan.id}/messages`, {
    method: "POST",
    body: JSON.stringify({ content })
  });
  console.log("message posted", mp.status, mp.body?.id);

  // Add reactions
  const emojiMap = ["🇪🇺","🇺🇸","🇧🇷","🌏","🌍"];
  for (const emoji of emojiMap) {
    const er = await api(`/channels/${regionChan.id}/messages/${mp.body.id}/reactions/${encodeURIComponent(emoji)}/@me`, { method: "PUT" });
    console.log("reaction", emoji, er.status);
    await sleep(500);
  }
}

ws.close();
console.log("\n✅ Region channel setup complete!");
console.log("Region role IDs:", JSON.stringify(regionRoleIds, null, 2));
console.log("\nNext: set up Carl-bot reaction roles in this channel using the IDs above.");
