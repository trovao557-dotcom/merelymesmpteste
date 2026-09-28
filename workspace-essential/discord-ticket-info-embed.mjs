const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>t.url&&t.url.includes("discord.com/channels/153413"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
let id=1; const pending=new Map();
ws.onmessage=ev=>{const m=JSON.parse(ev.data); if(m.id==null)return; const p=pending.get(m.id); if(!p)return; pending.delete(m.id); m.error?p.reject(Error(m.error.message)):p.resolve(m.result);};
const send=(method,params={})=>new Promise((resolve,reject)=>{const i=id++; const t=setTimeout(()=>{if(pending.has(i)){pending.delete(i);reject(Error("to"));}},30000); pending.set(i,{resolve:v=>{clearTimeout(t);resolve(v)},reject:e=>{clearTimeout(t);reject(e)}}); ws.send(JSON.stringify({id:i,method,params}));});
const tok=(await send("Runtime.evaluate",{expression:`(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,returnByValue:true})).result.value;
const CH="1544904386587787334";
// delete non-bot info messages we may have posted
const msgs=await(await fetch(`https://discord.com/api/v9/channels/${CH}/messages?limit=20`,{headers:{Authorization:tok}})).json();
for (const m of msgs) {
  if (!m.author?.bot && /NEED HELP|Support Tickets|Open Ticket|Categories/i.test(m.content||"")) {
    await fetch(`https://discord.com/api/v9/channels/${CH}/messages/${m.id}`,{method:"DELETE",headers:{Authorization:tok}});
    await new Promise(r=>setTimeout(r,300));
  }
}
const embed={
  title: "MerelyMeSMP Support",
  description: [
    "Click **Open Ticket** below and fill the form in **English**.",
    "",
    "**Categories (choose one in the form)**",
    "⚖️ Unban Appeal",
    "🚨 Report Player",
    "💸 Refund",
    "🎫 General Support",
    "🎬 Media Apply",
    "💳 Payment Support",
    "",
    "You will be asked for:",
    "1. Category",
    "2. Your Minecraft nick",
    "3. What happened"
  ].join("\n"),
  color: 0xFEE75C
};
const post=await fetch(`https://discord.com/api/v9/channels/${CH}/messages`,{
  method:"POST",
  headers:{Authorization:tok,"Content-Type":"application/json"},
  body: JSON.stringify({ embeds: [embed] })
});
console.log("info embed", post.status);
const left=await(await fetch(`https://discord.com/api/v9/channels/${CH}/messages?limit=5`,{headers:{Authorization:tok}})).json();
for (const m of left) {
  console.log(m.author?.username, m.embeds?.[0]?.title || m.components?.[0]?.components?.[0]?.label || (m.content||"").slice(0,40));
}
ws.close();
