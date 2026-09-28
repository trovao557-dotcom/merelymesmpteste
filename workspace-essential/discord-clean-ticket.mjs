const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>t.url&&t.url.includes("discord.com/channels/153413"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
let id=1; const pending=new Map();
ws.onmessage=ev=>{const m=JSON.parse(ev.data); if(m.id==null)return; const p=pending.get(m.id); if(!p)return; pending.delete(m.id); m.error?p.reject(Error(m.error.message)):p.resolve(m.result);};
const send=(method,params={})=>new Promise((resolve,reject)=>{const i=id++; const t=setTimeout(()=>{if(pending.has(i)){pending.delete(i);reject(Error("to"));}},30000); pending.set(i,{resolve:v=>{clearTimeout(t);resolve(v)},reject:e=>{clearTimeout(t);reject(e)}}); ws.send(JSON.stringify({id:i,method,params}));});
const tok=(await send("Runtime.evaluate",{expression:`(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,returnByValue:true})).result.value;
const msgs=await(await fetch("https://discord.com/api/v9/channels/1544904386587787334/messages?limit=15",{headers:{Authorization:tok}})).json();
const panels=msgs.filter(m=>m.author?.username==="Ticket Tool");
console.log("panels", panels.length);
for (const m of panels.slice(1)) {
  await fetch(`https://discord.com/api/v9/channels/1544904386587787334/messages/${m.id}`,{method:"DELETE",headers:{Authorization:tok}});
  console.log("del", m.id);
  await new Promise(r=>setTimeout(r,350));
}
const keep=panels[0];
console.log(JSON.stringify({
  title: keep?.embeds?.[0]?.title,
  desc: keep?.embeds?.[0]?.description,
  comps: keep?.components?.map(r=>r.components?.map(c=>({type:c.type,label:c.label,placeholder:c.placeholder,options:c.options?.map(o=>o.label)})))
},null,2));
ws.close();
