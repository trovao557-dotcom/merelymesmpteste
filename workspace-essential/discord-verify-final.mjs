const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>t.url&&t.url.includes("discord.com/channels/153413"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
let id=1; const pending=new Map();
ws.onmessage=ev=>{const m=JSON.parse(ev.data); if(m.id==null)return; const p=pending.get(m.id); if(!p)return; pending.delete(m.id); m.error?p.reject(Error(m.error.message)):p.resolve(m.result);};
const send=(method,params={})=>new Promise((resolve,reject)=>{const i=id++; const t=setTimeout(()=>{if(pending.has(i)){pending.delete(i);reject(Error("to"));}},30000); pending.set(i,{resolve:v=>{clearTimeout(t);resolve(v)},reject:e=>{clearTimeout(t);reject(e)}}); ws.send(JSON.stringify({id:i,method,params}));});
const tok=(await send("Runtime.evaluate",{expression:`(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,returnByValue:true})).result.value;
const msgs=await(await fetch("https://discord.com/api/v9/channels/1544904386587787334/messages?limit=5",{headers:{Authorization:tok}})).json();
for(const m of msgs.filter(x=>x.author?.username==='Ticket Tool')){
  console.log(JSON.stringify({id:m.id, title:m.embeds?.[0]?.title, desc:m.embeds?.[0]?.description, label:m.components?.[0]?.components?.[0]?.label},null,2));
}
const ip=await(await fetch("https://discord.com/api/v9/channels/1544904335530528788/messages?limit=3",{headers:{Authorization:tok}})).json();
console.log("iplinks", ip[0]?.content?.slice(0,400));
ws.close();
