const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>t.url&&t.url.includes("discord.com/channels/153413"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
let id=1; const pending=new Map();
ws.onmessage=ev=>{const m=JSON.parse(ev.data); if(m.id==null)return; const p=pending.get(m.id); if(!p)return; pending.delete(m.id); m.error?p.reject(Error(m.error.message)):p.resolve(m.result);};
const send=(method,params={})=>new Promise((resolve,reject)=>{const i=id++; const t=setTimeout(()=>{if(pending.has(i)){pending.delete(i);reject(Error("to"));}},30000); pending.set(i,{resolve:v=>{clearTimeout(t);resolve(v)},reject:e=>{clearTimeout(t);reject(e)}}); ws.send(JSON.stringify({id:i,method,params}));});
const tok=(await send("Runtime.evaluate",{expression:`(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,returnByValue:true})).result.value;
const api=async(path)=>{const res=await fetch("https://discord.com/api/v9"+path,{headers:{Authorization:tok}}); return {status:res.status, body:await res.json().catch(()=>null)};};
const roles=(await api("/guilds/1534136666984419348/roles")).body;
console.log("RANK_ROLES", (roles||[]).filter(r=>/knight|warrior|macer|prime|clipper|member|tip4|bots/i.test(r.name)).map(r=>({n:r.name,id:r.id,pos:r.position})));
// search members for tip4serv bot - try known tip4serv bot ids from docs/web
const candidates=["691645378212331520","723469003038064640","852255808401244191"];
for(const id of candidates){
  const m=await api(`/guilds/1534136666984419348/members/${id}`);
  console.log("member", id, m.status, m.body?.user?.username||m.body?.message);
}
const chans=(await api("/guilds/1534136666984419348/channels")).body;
console.log("MARKET", (chans||[]).filter(c=>/ah-sell|player-trade|MARKET/i.test(c.name)).map(c=>({n:c.name,id:c.id,t:c.type})));
ws.close();
