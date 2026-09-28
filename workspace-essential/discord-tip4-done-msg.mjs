const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>t.url&&t.url.includes("discord.com/channels/153413"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
let id=1; const pending=new Map();
ws.onmessage=ev=>{const m=JSON.parse(ev.data); if(m.id==null)return; const p=pending.get(m.id); if(!p)return; pending.delete(m.id); m.error?p.reject(Error(m.error.message)):p.resolve(m.result);};
const send=(method,params={})=>new Promise((resolve,reject)=>{const i=id++; const t=setTimeout(()=>{if(pending.has(i)){pending.delete(i);reject(Error("to"));}},20000); pending.set(i,{resolve:v=>{clearTimeout(t);resolve(v)},reject:e=>{clearTimeout(t);reject(e)}}); ws.send(JSON.stringify({id:i,method,params}));});
const tok=(await send("Runtime.evaluate",{expression:`(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,returnByValue:true})).result.value;
await fetch("https://discord.com/api/v9/channels/1544904353121308672/messages",{method:"POST",headers:{Authorization:tok,"Content-Type":"application/json"},body:JSON.stringify({content:[
"**Tip4Serv Discord sync is LIVE**",
"When someone buys Knight / Warrior / Macer / Prime on the store and **logs in with Discord at checkout**, Tip4Serv gives them the matching Discord role automatically.",
"MC nick → LuckPerms in-game. Discord account → Discord role.",
"Bot role **Tip4Serv.com** is above the rank roles."
].join("\\n")})});
console.log("posted");
ws.close();
