const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>t.url&&t.url.includes("discord.com/channels/153413"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
let id=1; const pending=new Map();
ws.onmessage=ev=>{const m=JSON.parse(ev.data); if(m.id==null)return; const p=pending.get(m.id); if(!p)return; pending.delete(m.id); m.error?p.reject(Error(m.error.message)):p.resolve(m.result);};
const send=(method,params={})=>new Promise((resolve,reject)=>{const i=id++; const t=setTimeout(()=>{if(pending.has(i)){pending.delete(i);reject(Error("to"));}},15000); pending.set(i,{resolve:v=>{clearTimeout(t);resolve(v)},reject:e=>{clearTimeout(t);reject(e)}}); ws.send(JSON.stringify({id:i,method,params}));});
const tok=(await send("Runtime.evaluate",{expression:`(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,returnByValue:true})).result.value;
const api=async(p,o={})=>{const r=await fetch("https://discord.com/api/v9"+p,{...o,headers:{Authorization:tok,"Content-Type":"application/json",...(o.headers||{})}}); const t2=await r.text(); let b; try{b=JSON.parse(t2);}catch{b=t2;} return {status:r.status,body:b};};
const msgs=(await api("/channels/1544904353121308672/messages?limit=20")).body;
if(Array.isArray(msgs)){
  const recent=msgs.slice(0,15).map(m=>({author:m.author?.username,content:(m.content||"").slice(0,120),embeds:(m.embeds||[]).map(e=>(e.description||e.title||"").slice(0,80))}));
  console.log(JSON.stringify(recent,null,2));
}
ws.close();
