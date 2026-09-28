const IPLINKS="1544904335530528788";
const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>t.url&&t.url.includes("discord.com/channels/153413"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
let id=1; const pending=new Map();
ws.onmessage=ev=>{const m=JSON.parse(ev.data); if(m.id==null)return; const p=pending.get(m.id); if(!p)return; pending.delete(m.id); m.error?p.reject(Error(m.error.message)):p.resolve(m.result);};
const send=(method,params={})=>new Promise((resolve,reject)=>{const i=id++; const t=setTimeout(()=>{if(pending.has(i)){pending.delete(i);reject(Error("to"));}},30000); pending.set(i,{resolve:v=>{clearTimeout(t);resolve(v)},reject:e=>{clearTimeout(t);reject(e)}}); ws.send(JSON.stringify({id:i,method,params}));});
const tok=(await send("Runtime.evaluate",{expression:`(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,returnByValue:true})).result.value;
const msgs=await(await fetch(`https://discord.com/api/v9/channels/${IPLINKS}/messages?limit=30`,{headers:{Authorization:tok}})).json();
console.log("msgs", msgs.map(m=>({id:m.id,c:(m.content||"").slice(0,120)})));
for(const m of msgs){
  const c=m.content||"";
  if(/g-portal|eu8740752|OLD|CONNECT|IP:/i.test(c) && m.id!=="1545061196929957958"){
    // delete outdated ip posts by us
    if(m.author?.id==="848312667411054603" || m.author?.username?.includes("merely")){
      const d=await fetch(`https://discord.com/api/v9/channels/${IPLINKS}/messages/${m.id}`,{method:"DELETE",headers:{Authorization:tok}});
      console.log("del", m.id, d.status, c.slice(0,60));
      await new Promise(r=>setTimeout(r,400));
    }
  }
}
// Pin the new IP message
const pin=await fetch(`https://discord.com/api/v9/channels/${IPLINKS}/pins/1545061196929957958`,{method:"PUT",headers:{Authorization:tok}});
console.log("pin", pin.status);
ws.close();
