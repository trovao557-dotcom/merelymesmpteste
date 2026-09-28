const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>t.url&&t.url.includes("discord.com/channels/153413"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
let id=1;const pending=new Map();
ws.onmessage=ev=>{const m=JSON.parse(ev.data);if(m.id==null)return;const p=pending.get(m.id);if(!p)return;pending.delete(m.id);m.error?p.reject(Error(m.error.message)):p.resolve(m.result);};
const send=(method,params={})=>new Promise((resolve,reject)=>{const i=id++;pending.set(i,{resolve,reject});setTimeout(()=>{if(pending.has(i)){pending.delete(i);reject(Error("to"));}},20000);ws.send(JSON.stringify({id:i,method,params}));});
const tok=(await send("Runtime.evaluate",{expression:`(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,returnByValue:true})).result.value;
const msgs=await(await fetch("https://discord.com/api/v9/channels/1544904353121308672/messages?limit=12",{headers:{Authorization:tok}})).json();
console.log(msgs.map(m=>({u:m.author.username,c:(m.content||"").slice(0,140),embeds:(m.embeds||[]).map(e=>(e.description||e.title||"").slice(0,100))})).map(JSON.stringify).join("\n"));
const roles=await(await fetch("https://discord.com/api/v9/guilds/1534136666984419348/roles",{headers:{Authorization:tok}})).json();
console.log("ROLES", roles.sort((a,b)=>b.position-a.position).map(r=>r.position+" "+r.name).join(" | "));
try{const sb=await(await fetch("https://discord.com/api/v9/guilds/1534136666984419348/members/491769129318088714",{headers:{Authorization:tok}})).json();console.log("STATBOT",sb.user?.username);}catch(e){console.log("STATBOT missing");}
// post ticket msg
await fetch("https://discord.com/api/v9/channels/1544904386587787334/messages",{method:"POST",headers:{Authorization:tok,"Content-Type":"application/json"},body:JSON.stringify({content:"**NEED HELP?** Open a ticket with **Ticket Tool** for buys, appeals, bugs, or staff apps."})});
console.log("ticket msg posted");
ws.close();
