const GUILD="1534136666984419348";
const BOT_CMDS="1544904353121308672";
const APP="1023897726301249628";
const CMD="1303005348390502451";
const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>t.url&&t.url.includes("discord.com/channels/153413"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
let id=1; const pending=new Map();
ws.onmessage=ev=>{const m=JSON.parse(ev.data); if(m.id==null)return; const p=pending.get(m.id); if(!p)return; pending.delete(m.id); m.error?p.reject(Error(m.error.message)):p.resolve(m.result);};
const send=(method,params={})=>new Promise((resolve,reject)=>{const i=id++; const t=setTimeout(()=>{if(pending.has(i)){pending.delete(i);reject(Error("to"));}},45000); pending.set(i,{resolve:v=>{clearTimeout(t);resolve(v)},reject:e=>{clearTimeout(t);reject(e)}}); ws.send(JSON.stringify({id:i,method,params}));});
const tok=(await send("Runtime.evaluate",{expression:`(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,returnByValue:true})).result.value;
await send("Page.navigate",{url:`https://discord.com/channels/${GUILD}/${BOT_CMDS}`});
await new Promise(r=>setTimeout(r,5000));
const meta=await send("Runtime.evaluate",{expression:`(()=>{
  // find session id from webpack
  let session=null;
  webpackChunkdiscord_app.push([[Math.random()],{},(e)=>{for(const m of Object.values(e.c||{})){try{const s=m?.exports?.default?.getSessionId?.()||m?.exports?.getSessionId?.(); if(s) session=s;}catch{}}}]);
  webpackChunkdiscord_app.pop();
  const box=document.querySelector('[data-slate-editor="true"], div[role="textbox"]');
  return {session, hasBox:!!box, url:location.href};
})()`,returnByValue:true});
console.log("meta", meta.result.value);
const session=meta.result.value?.session;
const idx=await fetch(`https://discord.com/api/v9/guilds/${GUILD}/application-command-index`,{headers:{Authorization:tok}}).then(r=>r.json());
const tiplink=(idx.application_commands||[]).find(c=>c.name==="tiplink"&&c.application_id===APP);
console.log("tiplink schema", JSON.stringify(tiplink,null,2).slice(0,1500));
if(!session){ console.log("NO_SESSION"); ws.close(); process.exit(1); }
const nonce=String(Date.now());
const body={
  type:2,
  application_id:APP,
  guild_id:GUILD,
  channel_id:BOT_CMDS,
  session_id:session,
  data:{version:tiplink.version,id:tiplink.id,name:tiplink.name,type:1,options:[],application_command:tiplink},
  nonce
};
const res=await fetch("https://discord.com/api/v9/interactions",{method:"POST",headers:{Authorization:tok,"Content-Type":"application/json"},body:JSON.stringify(body)});
const text=await res.text();
console.log("interaction", res.status, text.slice(0,500));
await new Promise(r=>setTimeout(r,3000));
const msgs=await fetch(`https://discord.com/api/v9/channels/${BOT_CMDS}/messages?limit=5`,{headers:{Authorization:tok}}).then(r=>r.json());
console.log(msgs.map(m=>({u:m.author?.username,c:(m.content||"").slice(0,200),embeds:(m.embeds||[]).map(e=>(e.description||e.title||"").slice(0,150))})));
ws.close();
