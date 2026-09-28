const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>t.url&&t.url.includes("discord.com/channels/153413"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
let id=1; const pending=new Map();
ws.onmessage=ev=>{const m=JSON.parse(ev.data); if(m.id==null)return; const p=pending.get(m.id); if(!p)return; pending.delete(m.id); m.error?p.reject(Error(m.error.message)):p.resolve(m.result);};
const send=(method,params={})=>new Promise((resolve,reject)=>{const i=id++; const t=setTimeout(()=>{pending.delete(i);reject(Error("to"));},20000); pending.set(i,{resolve:v=>{clearTimeout(t);resolve(v)},reject:e=>{clearTimeout(t);reject(e)}}); ws.send(JSON.stringify({id:i,method,params}));});
const tok=(await send("Runtime.evaluate",{expression:`(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,returnByValue:true})).result.value;
const api=async(p)=>{const r=await fetch("https://discord.com/api/v9"+p,{headers:{Authorization:tok}}); return r.json();};
const chans=await api("/guilds/1534136666984419348/channels");
const hits=chans.filter(c=>/store|hit|tip|shop/i.test(c.name||"")).map(c=>({name:c.name,id:c.id,type:c.type}));
console.log("channels", JSON.stringify(hits,null,2));
// scan recent messages in store + ip-links + announcements
for (const id of ["1544904368157884476","1544904335530528788","1544904332242325504"]) {
  try {
    const msgs=await api(`/channels/${id}/messages?limit=20`);
    if(!Array.isArray(msgs)) continue;
    const m=msgs.filter(x=>/STOREHIT|HIT.?SMP|tip4serv|store/i.test(x.content||"")).map(x=>({ch:id,c:(x.content||"").slice(0,120)}));
    if(m.length) console.log(JSON.stringify(m,null,2));
  } catch {}
}
ws.close();
