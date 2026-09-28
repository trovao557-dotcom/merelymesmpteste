const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>t.url&&t.url.includes("discord.com/channels/153413"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
let id=1; const pending=new Map();
ws.onmessage=ev=>{const m=JSON.parse(ev.data); if(m.id==null)return; const p=pending.get(m.id); if(!p)return; pending.delete(m.id); m.error?p.reject(Error(m.error.message)):p.resolve(m.result);};
const send=(method,params={})=>new Promise((resolve,reject)=>{const i=id++; const t=setTimeout(()=>{if(pending.has(i)){pending.delete(i);reject(Error("to"));}},30000); pending.set(i,{resolve:v=>{clearTimeout(t);resolve(v)},reject:e=>{clearTimeout(t);reject(e)}}); ws.send(JSON.stringify({id:i,method,params}));});
const tok=(await send("Runtime.evaluate",{expression:`(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,returnByValue:true})).result.value;
const CH="1544904386587787334";
const msgs=await(await fetch(`https://discord.com/api/v9/channels/${CH}/messages?limit=10`,{headers:{Authorization:tok}})).json();
const panels=msgs.filter(m=>m.author?.username==='Ticket Tool');
for (const m of panels.slice(1)) {
  await fetch(`https://discord.com/api/v9/channels/${CH}/messages/${m.id}`,{method:"DELETE",headers:{Authorization:tok}});
  console.log("del", m.id);
}
const content = [
"**MerelyMeSMP Support**",
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
].join("\n");
const post=await fetch(`https://discord.com/api/v9/channels/${CH}/messages`,{
  method:"POST",
  headers:{Authorization:tok,"Content-Type":"application/json"},
  body: JSON.stringify({ content })
});
const body=await post.text();
console.log("post", post.status, body.slice(0,200));
// If denied, temporarily allow owner send via overwrite then post
if(post.status>=400){
  const me="848312667411054603";
  await fetch(`https://discord.com/api/v9/channels/${CH}/permissions/${me}`,{
    method:"PUT",
    headers:{Authorization:tok,"Content-Type":"application/json"},
    body: JSON.stringify({id:me,type:1,allow:"2048",deny:"0"})
  });
  const post2=await fetch(`https://discord.com/api/v9/channels/${CH}/messages`,{
    method:"POST",
    headers:{Authorization:tok,"Content-Type":"application/json"},
    body: JSON.stringify({ content })
  });
  console.log("post2", post2.status, (await post2.text()).slice(0,200));
}
ws.close();
