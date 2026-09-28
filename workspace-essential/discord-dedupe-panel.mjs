const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>t.url&&t.url.includes("discord.com/channels/153413"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
let id=1; const pending=new Map();
ws.onmessage=ev=>{const m=JSON.parse(ev.data); if(m.id==null)return; const p=pending.get(m.id); if(!p)return; pending.delete(m.id); m.error?p.reject(Error(m.error.message)):p.resolve(m.result);};
const send=(method,params={})=>new Promise((resolve,reject)=>{const i=id++; const t=setTimeout(()=>{if(pending.has(i)){pending.delete(i);reject(Error("to"));}},25000); pending.set(i,{resolve:v=>{clearTimeout(t);resolve(v)},reject:e=>{clearTimeout(t);reject(e)}}); ws.send(JSON.stringify({id:i,method,params}));});
const tok=(await send("Runtime.evaluate",{expression:`(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,returnByValue:true})).result.value;
const msgs=await(await fetch("https://discord.com/api/v9/channels/1544904386587787334/messages?limit=10",{headers:{Authorization:tok}})).json();
const panels=msgs.filter(m=>m.author?.username==="Ticket Tool");
console.log("panels", panels.length, panels.map(m=>m.id));
// keep newest, delete older duplicate
if (panels.length>1) {
  const older=panels.slice(1);
  for (const m of older) {
    const del=await fetch(`https://discord.com/api/v9/channels/1544904386587787334/messages/${m.id}`,{method:"DELETE",headers:{Authorization:tok}});
    console.log("del panel", m.id, del.status);
  }
}
const left=await(await fetch("https://discord.com/api/v9/channels/1544904386587787334/messages?limit=5",{headers:{Authorization:tok}})).json();
console.log("left", left.map(m=>({u:m.author?.username, comps:(m.components||[]).length, embeds:(m.embeds||[]).length, title:m.embeds?.[0]?.title, desc:(m.embeds?.[0]?.description||"").slice(0,80)})));
ws.close();
