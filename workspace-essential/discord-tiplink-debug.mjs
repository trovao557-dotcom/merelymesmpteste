const GUILD="1534136666984419348";
const BOT_CMDS="1544904353121308672";
const APP="1023897726301249628";
const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>t.url&&t.url.includes("discord.com/channels/153413"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
let id=1; const pending=new Map();
ws.onmessage=ev=>{const m=JSON.parse(ev.data); if(m.id==null)return; const p=pending.get(m.id); if(!p)return; pending.delete(m.id); m.error?p.reject(Error(m.error.message)):p.resolve(m.result);};
const send=(method,params={})=>new Promise((resolve,reject)=>{const i=id++; const t=setTimeout(()=>{if(pending.has(i)){pending.delete(i);reject(Error("to"));}},30000); pending.set(i,{resolve:v=>{clearTimeout(t);resolve(v)},reject:e=>{clearTimeout(t);reject(e)}}); ws.send(JSON.stringify({id:i,method,params}));});
const tok=(await send("Runtime.evaluate",{expression:`(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,returnByValue:true})).result.value;
const session=(await send("Runtime.evaluate",{expression:`(()=>{let sid=null; webpackChunkdiscord_app.push([[Symbol()],{},(req)=>{for(const m of Object.values(req.c||{})){try{const fn=m?.exports?.default?.getSessionId||m?.exports?.getSessionId||m?.exports?.Z?.getSessionId; if(fn){const v=fn(); if(typeof v==='string'&&v.length>8) sid=v;}}catch{}}}]); webpackChunkdiscord_app.pop(); return sid;})()`,returnByValue:true})).result.value;
const me=await fetch("https://discord.com/api/v9/users/@me",{headers:{Authorization:tok}}).then(r=>r.json());
const member=await fetch(`https://discord.com/api/v9/guilds/${GUILD}/members/${me.id}`,{headers:{Authorization:tok}}).then(r=>r.json());
const tip=await fetch(`https://discord.com/api/v9/guilds/${GUILD}/members/${APP}`,{headers:{Authorization:tok}}).then(r=>r.json());
console.log("me", me.username, "perms bit?", member.roles?.length);
console.log("tip4 bot", tip.user?.username, "roles", tip.roles);
// guild permissions for tip bot
const roles=await fetch(`https://discord.com/api/v9/guilds/${GUILD}/roles`,{headers:{Authorization:tok}}).then(r=>r.json());
const tipRoles=(tip.roles||[]).map(id=>roles.find(r=>r.id===id)).filter(Boolean);
let perms=0n; for(const r of tipRoles){ perms |= BigInt(r.permissions); }
console.log("tip perms", perms.toString(), "manage_roles", Boolean(perms & 0x10000000n), "manage_guild", Boolean(perms & 0x20n), "admin", Boolean(perms & 0x8n));

const idx=await fetch(`https://discord.com/api/v9/guilds/${GUILD}/application-command-index`,{headers:{Authorization:tok}}).then(r=>r.json());
const tiplink=(idx.application_commands||[]).find(c=>c.name==="tiplink"&&c.application_id===APP);
const body={type:2,application_id:APP,guild_id:GUILD,channel_id:BOT_CMDS,session_id:session,data:{version:tiplink.version,id:tiplink.id,name:"tiplink",type:1,options:[]},nonce:String(Date.now()*1000)};
const res=await fetch("https://discord.com/api/v9/interactions",{method:"POST",headers:{Authorization:tok,"Content-Type":"application/json"},body:JSON.stringify(body)});
console.log("tiplink", res.status);

// Wait and scrape Discord UI for ephemeral Tip4Serv message
await send("Page.navigate",{url:`https://discord.com/channels/${GUILD}/${BOT_CMDS}`});
await new Promise(r=>setTimeout(r,4000));
const ui=await send("Runtime.evaluate",{expression:`(()=>({text:(document.body.innerText||'').slice(0,4000)}))()`,returnByValue:true});
console.log("ui snippet", (ui.result.value.text||"").includes("Tip4") , ui.result.value.text.match(/Tip4[\s\S]{0,400}|tiplink[\s\S]{0,400}|linked|connect|store|error|permission/i)?.[0]);
ws.close();
