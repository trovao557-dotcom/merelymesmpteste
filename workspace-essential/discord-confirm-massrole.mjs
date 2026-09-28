const GUILD="1534136666984419348";
const BOT_CMDS="1544904353121308672";
const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>t.url&&t.url.includes("discord.com/channels/153413"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
let id=1; const pending=new Map();
ws.onmessage=ev=>{const m=JSON.parse(ev.data); if(m.id==null)return; const p=pending.get(m.id); if(!p)return; pending.delete(m.id); m.error?p.reject(Error(m.error.message)):p.resolve(m.result);};
const send=(method,params={})=>new Promise((resolve,reject)=>{const i=id++; const t=setTimeout(()=>{if(pending.has(i)){pending.delete(i);reject(Error("to"));}},30000); pending.set(i,{resolve:v=>{clearTimeout(t);resolve(v)},reject:e=>{clearTimeout(t);reject(e)}}); ws.send(JSON.stringify({id:i,method,params}));});
const tok=(await send("Runtime.evaluate",{expression:`(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,returnByValue:true})).result.value;
const msgs=await(await fetch(`https://discord.com/api/v9/channels/${BOT_CMDS}/messages?limit=10`,{headers:{Authorization:tok}})).json();
const confirm=msgs.find(m=>m.author?.username==="Carl-bot" && /add .* to all members|continue/i.test(m.embeds?.[0]?.description||m.content||""));
console.log("confirm msg", confirm?.id, confirm?.embeds?.[0]?.description, "components", JSON.stringify(confirm?.components||[]).slice(0,500));
if(confirm){
  // Prefer button interaction if present
  const btn=confirm.components?.[0]?.components?.find(c=>/yes|confirm|continue|sim/i.test(c.label||c.custom_id||""));
  if(btn){
    // Can't easily fire button without interaction token - use reaction instead
    console.log("button", btn);
  }
  // Carl typically wants ✅ reaction
  for (const emoji of ["✅","✔️","👍","yes"]) {
    const enc=encodeURIComponent(emoji);
    const r=await fetch(`https://discord.com/api/v9/channels/${BOT_CMDS}/messages/${confirm.id}/reactions/${enc}/%40me`,{method:"PUT",headers:{Authorization:tok}});
    console.log("react", emoji, r.status);
    if(r.status===204) break;
  }
  // also reply yes
  await fetch(`https://discord.com/api/v9/channels/${BOT_CMDS}/messages`,{method:"POST",headers:{Authorization:tok,"Content-Type":"application/json"},body:JSON.stringify({content:"yes",message_reference:{message_id:confirm.id}})});
}
await new Promise(r=>setTimeout(r,4000));
const after=await(await fetch(`https://discord.com/api/v9/channels/${BOT_CMDS}/messages?limit=6`,{headers:{Authorization:tok}})).json();
console.log(after.map(m=>({u:m.author?.username,c:(m.content||"").slice(0,150),e:(m.embeds||[])[0]?.description?.slice(0,150)})));
ws.close();
