const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>t.url&&t.url.includes("discord.com/channels/153413"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
let id=1; const pending=new Map();
ws.onmessage=ev=>{const m=JSON.parse(ev.data); if(m.id==null)return; const p=pending.get(m.id); if(!p)return; pending.delete(m.id); m.error?p.reject(Error(m.error.message)):p.resolve(m.result);};
const send=(method,params={})=>new Promise((resolve,reject)=>{const i=id++; const t=setTimeout(()=>{if(pending.has(i)){pending.delete(i);reject(Error("to"));}},30000); pending.set(i,{resolve:v=>{clearTimeout(t);resolve(v)},reject:e=>{clearTimeout(t);reject(e)}}); ws.send(JSON.stringify({id:i,method,params}));});
const tok=(await send("Runtime.evaluate",{expression:`(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,returnByValue:true})).result.value;
const msgs=await(await fetch("https://discord.com/api/v9/channels/1544904386587787334/messages?limit=8",{headers:{Authorization:tok}})).json();
for (const m of msgs) {
  console.log(JSON.stringify({
    id:m.id, u:m.author?.username, bot:m.author?.bot,
    content:(m.content||"").slice(0,80),
    embedTitle:m.embeds?.[0]?.title,
    embedDesc:(m.embeds?.[0]?.description||"").slice(0,200),
    comps:(m.components||[]).map(row=>row.components?.map(c=>({t:c.type,label:c.label,placeholder:c.placeholder})))
  }));
}
// Keep newest Ticket Tool panel(s), delete older duplicates
const panels=msgs.filter(m=>m.author?.username==="Ticket Tool");
if(panels.length>1){
  for(const m of panels.slice(1)){
    const d=await fetch(`https://discord.com/api/v9/channels/1544904386587787334/messages/${m.id}`,{method:"DELETE",headers:{Authorization:tok}});
    console.log("del", m.id, d.status);
  }
}
// Guild vanity
const g=await(await fetch("https://discord.com/api/v9/guilds/1534136666984419348?with_counts=true",{headers:{Authorization:tok}})).json();
console.log("vanity", g.vanity_url_code, "premium_tier", g.premium_tier, "features", (g.features||[]).filter(f=>/VANITY|INVITE|DOMAIN|COMMUNITY/i.test(f)));
// Try set vanity
const v=await fetch("https://discord.com/api/v9/guilds/1534136666984419348/vanity-url",{method:"PATCH",headers:{Authorization:tok,"Content-Type":"application/json"},body:JSON.stringify({code:"merelymesmp"})});
const vt=await v.text();
console.log("set vanity", v.status, vt.slice(0,300));
ws.close();
