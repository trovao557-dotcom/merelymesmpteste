const GUILD="1534136666984419348";
const IPLINKS="1544904335530528788";
const STORE="1544904368157884476";
const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>t.url&&t.url.includes("discord.com/channels/153413"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
let id=1; const pending=new Map();
ws.onmessage=ev=>{const m=JSON.parse(ev.data); if(m.id==null)return; const p=pending.get(m.id); if(!p)return; pending.delete(m.id); m.error?p.reject(Error(m.error.message)):p.resolve(m.result);};
const send=(method,params={})=>new Promise((resolve,reject)=>{const i=id++; const t=setTimeout(()=>{if(pending.has(i)){pending.delete(i);reject(Error("to"));}},30000); pending.set(i,{resolve:v=>{clearTimeout(t);resolve(v)},reject:e=>{clearTimeout(t);reject(e)}}); ws.send(JSON.stringify({id:i,method,params}));});
const tok=(await send("Runtime.evaluate",{expression:`(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,returnByValue:true})).result.value;
const api=async(path,opts={})=>{const res=await fetch("https://discord.com/api/v9"+path,{...opts,headers:{Authorization:tok,"Content-Type":"application/json",...(opts.headers||{})}}); const text=await res.text(); let body; try{body=JSON.parse(text);}catch{body=text;} return {status:res.status,body};};

// Clear old sticky-ish messages? Post fresh IP block
const ipMsg=await api(`/channels/${IPLINKS}/messages`,{method:"POST",body:JSON.stringify({content:[
"**MerelyMeSMP — IP & LINKS**",
"",
"**Minecraft IP:** `merelymesmp.com`",
"**Website / Store:** https://merelymesmp.com",
"**Discord invite:** https://merelymesmp.com",
"",
"(Same address for join + store + Discord invite page.)"
].join("\\n")})});
console.log("iplinks", ipMsg.status, ipMsg.body?.id||ipMsg.body?.message);

const storeMsg=await api(`/channels/${STORE}/messages`,{method:"POST",body:JSON.stringify({content:[
"**Store & join**",
"Minecraft: `merelymesmp.com`",
"Store / Discord: https://merelymesmp.com"
].join("\\n")})});
console.log("store", storeMsg.status);

// Update welcome intro guide if exists
const intro="1545055586247250030";
const threads=await api(`/channels/${intro}/threads/active`);
const guide=(threads.body?.threads||[]).find(t=>/READ ME|INTRO/i.test(t.name));
if(guide?.id){
  const m=await api(`/channels/${guide.id}/messages`,{method:"POST",body:JSON.stringify({content:"**IP / links:** Minecraft `merelymesmp.com` · Site & Discord invite https://merelymesmp.com"})});
  console.log("intro", m.status);
}
ws.close();
