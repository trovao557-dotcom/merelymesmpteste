// Carl-bot !rr list showed "Reaction roles" embed but unique keeps failing
// Try !rr edit to see all configured RR, and verify channel+msg
const REGION_CHAN = "1545086719399952435";
const MSG_ID = "1545087303570169958";
const BOT_CHAN = "1544904353121308672";
const REGION_ROLES = {
  "🇪🇺": "1545086702308036688",
  "🇺🇸": "1545086705579589692",
  "🇧🇷": "1545086709576900780",
  "🌏":  "1545086712592736276",
  "🌍":  "1545086715604115577",
};

const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>t.url&&t.url.includes("discord.com/channels/153413"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
let id=1; const pending=new Map();
ws.onmessage=ev=>{const m=JSON.parse(ev.data); if(m.id==null)return; const p=pending.get(m.id); if(!p)return; pending.delete(m.id); m.error?p.reject(Error(m.error.message)):p.resolve(m.result);};
const send=(method,params={})=>new Promise((resolve,reject)=>{const i=id++; const t=setTimeout(()=>{if(pending.has(i)){pending.delete(i);reject(Error("to"));}},15000); pending.set(i,{resolve:v=>{clearTimeout(t);resolve(v)},reject:e=>{clearTimeout(t);reject(e)}}); ws.send(JSON.stringify({id:i,method,params}));});
const tok=(await send("Runtime.evaluate",{expression:`(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,returnByValue:true})).result.value;
const api=async(p,o={})=>{const r=await fetch("https://discord.com/api/v9"+p,{...o,headers:{Authorization:tok,"Content-Type":"application/json",...(o.headers||{})}}); const t2=await r.text(); let b; try{b=JSON.parse(t2);}catch{b=t2;} return {status:r.status,body:b};};
const sleep=ms=>new Promise(r=>setTimeout(r,ms));

const postCmd = async (cmd) => {
  const r=await api(`/channels/${BOT_CHAN}/messages`,{method:"POST",body:JSON.stringify({content:cmd})});
  console.log("sent:", r.status, cmd.slice(0,60));
  await sleep(2000);
};

// Try re-adding all 5 with fresh calls
for (const [emoji, roleId] of Object.entries(REGION_ROLES)) {
  await postCmd(`!rr add ${REGION_CHAN} ${MSG_ID} ${emoji} ${roleId}`);
}

// Check responses
const botMsgs=(await api(`/channels/${BOT_CHAN}/messages?limit=12`)).body;
console.log("\nBot responses:");
botMsgs.slice(0,12).forEach(m=>console.log(m.author?.username+":", (m.content||"").slice(0,120)));

ws.close();
