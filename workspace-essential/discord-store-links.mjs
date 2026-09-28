const GUILD="1534136666984419348";
const STORE="1544904368157884476";
const IPLINKS="1544904335530528788";
const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>t.url&&t.url.includes("discord.com/channels/153413"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
let id=1; const pending=new Map();
ws.onmessage=ev=>{const m=JSON.parse(ev.data); if(m.id==null)return; const p=pending.get(m.id); if(!p)return; pending.delete(m.id); m.error?p.reject(Error(m.error.message)):p.resolve(m.result);};
const send=(method,params={})=>new Promise((resolve,reject)=>{const i=id++; const t=setTimeout(()=>{if(pending.has(i)){pending.delete(i);reject(Error("to"));}},20000); pending.set(i,{resolve:v=>{clearTimeout(t);resolve(v)},reject:e=>{clearTimeout(t);reject(e)}}); ws.send(JSON.stringify({id:i,method,params}));});
const tok=(await send("Runtime.evaluate",{expression:`(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,returnByValue:true})).result.value;
const api=async(p,o={})=>{const r=await fetch("https://discord.com/api/v9"+p,{...o,headers:{Authorization:tok,"Content-Type":"application/json",...(o.headers||{})}}); const t2=await r.text(); let b; try{b=JSON.parse(t2);}catch{b=t2;} return {status:r.status,body:b};};
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const cleanPost=async(ch,content)=>{
  const old=(await api(`/channels/${ch}/messages?limit=15`)).body;
  for(const m of (Array.isArray(old)?old:[])){
    if(m.author?.username===".merelyme" || m.author?.id){
      // only delete our bot-ish short store/ip posts that look outdated
      if(/store|discord|merelyme|g-portal|tip4serv|IP/i.test(m.content||"") && m.author?.username?.includes("merely")){
        await api(`/channels/${ch}/messages/${m.id}`,{method:"DELETE"});
        await sleep(350);
      }
    }
  }
  const r=await api(`/channels/${ch}/messages`,{method:"POST",body:JSON.stringify({content})});
  console.log(ch,r.status);
};
await cleanPost(STORE, [
"**STORE — MerelyMeSMP**",
"",
"Buy ranks (Knight · Warrior · Macer · Prime) and points packages.",
"Rank applies in-game automatically after purchase.",
"Link Discord at checkout to also get your Discord role.",
"",
"🛒 Store: <https://merelymesmpstore.tip4serv.com>",
"💬 Discord: <https://discord.gg/NEybDXCkhA>",
"🖥️ IP: `merelymesmp.com`",
].join("\n"));
await cleanPost(IPLINKS, [
"**MerelyMeSMP — LINKS**",
"",
"🖥️  **Minecraft IP:** `merelymesmp.com`",
"🛒  **Store:** <https://merelymesmpstore.tip4serv.com>",
"💬  **Discord:** <https://discord.gg/NEybDXCkhA>",
"🌐  **Website:** <https://merelymesmp.com>",
"📺  **Twitch:** <https://twitch.tv/merelyme>",
].join("\n"));
ws.close();
console.log("discord updated");
