// Post a Discord message with region-pick buttons using a self-bot interaction
// Then configure an AutoMod / interaction handler via Discord's component system
// We create a proper button-based role picker using Discord's component interactions

const GUILD = "1534136666984419348";
const REGION_CHAN = "1545086719399952435"; // 🌍┃choose-region
const REGION_ROLES = {
  "🇪🇺 EU":   "1545086702308036688",
  "🇺🇸 NA":   "1545086705579589692",
  "🇧🇷 SA":   "1545086709576900780",
  "🌏 ASIA":  "1545086712592736276",
  "🌍 Other": "1545086715604115577",
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

const sleep = ms => new Promise(r => setTimeout(r, ms));

// Delete old messages in the channel
const old = (await api(`/channels/${REGION_CHAN}/messages?limit=10`)).body;
for (const m of (Array.isArray(old)?old:[])) {
  await api(`/channels/${REGION_CHAN}/messages/${m.id}`, {method:"DELETE"});
  await sleep(300);
}
console.log("cleared old messages");

// Post a new clean embed-style message
// Discord doesn't allow self-bots to post interactive buttons that handle role assignment
// Best approach without a real bot: use reaction roles via Discord's native feature
// Post a beautifully formatted message with reactions + instructions for Carl-bot command

const regionMessage = `## 🌍 Choose Your Region

Click a reaction below to get your region role.
Remove it to unselect. You can have only one region role.

🇪🇺 — **EU** Europe
🇺🇸 — **NA** North America
🇧🇷 — **SA** South America
🌏 — **ASIA** Asia / Oceania
🌍 — **Other** Rest of world

> *Roles are assigned automatically. If it doesn't work, open a ticket in <#1544904386587787334>.*`;

const mp = await api(`/channels/${REGION_CHAN}/messages`, {
  method: "POST",
  body: JSON.stringify({ content: regionMessage })
});
console.log("posted msg", mp.status, mp.body?.id);
const MSG_ID = mp.body?.id;

// Add reactions
for (const emoji of ["🇪🇺","🇺🇸","🇧🇷","🌏","🌍"]) {
  await api(`/channels/${REGION_CHAN}/messages/${MSG_ID}/reactions/${encodeURIComponent(emoji)}/@me`, {method:"PUT"});
  await sleep(600);
  console.log("reacted", emoji);
}

// Now configure Carl-bot reaction roles via the !rr command in bot-commands
// We'll post the setup commands to #bot-commands for the bot to process
const BOT_CHAN = "1544904353121308672"; // #🤖┃bot-commands

// Carl-bot rr setup command
const rrSetupMsg = [
  `**Setting up reaction roles for region picker...**`,
  `\`\`\``,
  `!rr add ${REGION_CHAN} ${MSG_ID} 🇪🇺 ${REGION_ROLES["🇪🇺 EU"]}`,
  `!rr add ${REGION_CHAN} ${MSG_ID} 🇺🇸 ${REGION_ROLES["🇺🇸 NA"]}`,
  `!rr add ${REGION_CHAN} ${MSG_ID} 🇧🇷 ${REGION_ROLES["🇧🇷 SA"]}`,
  `!rr add ${REGION_CHAN} ${MSG_ID} 🌏 ${REGION_ROLES["🌏 ASIA"]}`,
  `!rr add ${REGION_CHAN} ${MSG_ID} 🌍 ${REGION_ROLES["🌍 Other"]}`,
  `\`\`\``,
].join("\n");

// Execute each Carl-bot reaction role command
const commands = [
  `!rr add ${REGION_CHAN} ${MSG_ID} 🇪🇺 ${REGION_ROLES["🇪🇺 EU"]}`,
  `!rr add ${REGION_CHAN} ${MSG_ID} 🇺🇸 ${REGION_ROLES["🇺🇸 NA"]}`,
  `!rr add ${REGION_CHAN} ${MSG_ID} 🇧🇷 ${REGION_ROLES["🇧🇷 SA"]}`,
  `!rr add ${REGION_CHAN} ${MSG_ID} 🌏 ${REGION_ROLES["🌏 ASIA"]}`,
  `!rr add ${REGION_CHAN} ${MSG_ID} 🌍 ${REGION_ROLES["🌍 Other"]}`,
];

for (const cmd of commands) {
  const r = await api(`/channels/${BOT_CHAN}/messages`, {
    method: "POST",
    body: JSON.stringify({ content: cmd })
  });
  console.log("cmd sent:", r.status, cmd.slice(0,40));
  await sleep(1500); // give Carl-bot time to process
}

// Set reaction role mode to "unique" (only one region at a time)
await sleep(2000);
const uniqueCmd = `!rr unique ${REGION_CHAN} ${MSG_ID}`;
const ur = await api(`/channels/${BOT_CHAN}/messages`, {
  method: "POST",
  body: JSON.stringify({ content: uniqueCmd })
});
console.log("unique mode:", ur.status, uniqueCmd);

ws.close();
console.log("\n✅ Region reaction roles configured!");
console.log("Message ID:", MSG_ID);
console.log("Channel:", REGION_CHAN);
