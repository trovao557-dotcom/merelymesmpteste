const GUILD="1534136666984419348";
const CREATE_TICKET="1544904386587787334";
const TICKET_STAFF="1544904309882232842";
const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>t.url&&t.url.includes("1544904386587787334"))||list.find(t=>t.url&&t.url.includes("discord.com/channels/153413"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
let id=1; const pending=new Map();
ws.onmessage=ev=>{const m=JSON.parse(ev.data); if(m.id==null)return; const p=pending.get(m.id); if(!p)return; pending.delete(m.id); m.error?p.reject(Error(m.error.message)):p.resolve(m.result);};
const send=(method,params={})=>new Promise((resolve,reject)=>{const i=id++; const t=setTimeout(()=>{if(pending.has(i)){pending.delete(i);reject(Error("to "+method));}},45000); pending.set(i,{resolve:v=>{clearTimeout(t);resolve(v)},reject:e=>{clearTimeout(t);reject(e)}}); ws.send(JSON.stringify({id:i,method,params}));});
await send("Page.bringToFront").catch(()=>{});
const tok=(await send("Runtime.evaluate",{expression:`(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,returnByValue:true})).result.value;
if(!tok) throw new Error("no token");
const api=async(path,opts={})=>{const res=await fetch("https://discord.com/api/v9"+path,{...opts,headers:{Authorization:tok,"Content-Type":"application/json",...(opts.headers||{})}}); const text=await res.text(); let body; try{body=JSON.parse(text);}catch{body=text;} return {status:res.status,body};};
const forum=await api(`/channels/${CREATE_TICKET}`,{method:"PATCH",body:JSON.stringify({type:15,topic:"Open a ticket — pick a tag and describe your issue. Staff will reply.",available_tags:[{name:"Support",moderated:false},{name:"Appeal",moderated:false},{name:"Buy",moderated:false},{name:"Bug",moderated:false},{name:"Staff App",moderated:false}]})});
console.log("forum", forum.status, typeof forum.body==="object"? (forum.body.message||forum.body.type||"ok") : String(forum.body).slice(0,200));
if(forum.status>=400){
  const msg=await api(`/channels/${CREATE_TICKET}/messages`,{method:"POST",body:JSON.stringify({content:"**TICKETS READY**\n\n**Ticket Tool** is in the server.\nFinish the panel (30s):\n1. https://tickettool.xyz/dashboard — Login with Discord\n2. Select MerelyMe SMP\n3. Create panel → send to #create-ticket\n4. Support role = Ticket Staff\n\nMember autorole is already on via Carl-bot."})});
  console.log("msg", msg.status);
} else {
  console.log("forum name", forum.body?.name, "tags", (forum.body?.available_tags||[]).map(t=>t.name).join(","));
}
for (const [n,bid] of [["Carl","235148962103951360"],["TicketTool","557628352828014614"],["Statbot","491769129318088714"]]) {
  const m=await api(`/guilds/${GUILD}/members/${bid}`);
  console.log(n, m.status===200?m.body.user.username:"MISSING");
}
const roles=await api(`/guilds/${GUILD}/roles`);
console.log("roles", roles.body.sort((a,b)=>b.position-a.position).map(r=>r.position+" "+r.name).join(" | "));
ws.close();
