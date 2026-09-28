const REGION_CHAN = "1545086719399952435";
const MSG_ID = "1545087303570169958";
const OTHER_ROLE = "1545086715604115577";
const BOT_CHAN = "1544904353121308672";

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

// Add missing "Other" reaction role
const r1 = await api(`/channels/${BOT_CHAN}/messages`, {method:"POST", body:JSON.stringify({content:`!rr add ${REGION_CHAN} ${MSG_ID} 🌍 ${OTHER_ROLE}`})});
console.log("add Other:", r1.status);
await sleep(2000);

// Set unique mode (only one region role at a time)
const r2 = await api(`/channels/${BOT_CHAN}/messages`, {method:"POST", body:JSON.stringify({content:`!rr unique ${REGION_CHAN} ${MSG_ID}`})});
console.log("unique:", r2.status);
await sleep(2000);

// Check recent bot responses
const msgs = (await api(`/channels/${BOT_CHAN}/messages?limit=5`)).body;
msgs.slice(0,5).forEach(m=>console.log(m.author?.username+":", m.content?.slice(0,100)));

ws.close();
