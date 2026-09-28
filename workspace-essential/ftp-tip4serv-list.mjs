import { execFileSync } from "node:child_process";
const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>t.type==="page"&&String(t.url).includes("8699357"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise(r=>ws.addEventListener("open",r));
let id=1; const pending=new Map();
ws.addEventListener("message",ev=>{const m=JSON.parse(String(ev.data)); if(m.id&&pending.has(m.id)){const p=pending.get(m.id); pending.delete(m.id); m.error?p.reject(new Error(m.error.message)):p.resolve(m.result);}});
const send=(method,params={})=>new Promise((resolve,reject)=>{pending.set(id,{resolve,reject}); ws.send(JSON.stringify({id,method,params})); id++;});
const r=await send("Runtime.evaluate",{expression:`(()=>{const a=[...document.querySelectorAll('a')].find(x=>(x.href||'').startsWith('ftp://')); return a?a.href:null;})()`,returnByValue:true});
const base=(r.result.value||"").replace(/\/$/,"");
function listDir(path){
  try{return execFileSync("curl.exe",["-s","--ftp-pasv","--list-only",`${base}${path}`],{encoding:"utf8"});}
  catch(e){return `ERR ${e.status}`;}
}
console.log("plugins tip*", listDir("/plugins/").split(/\r?\n/).filter(x=>/tip|Tip|TIP|store/i.test(x)).join(" | "));
console.log("all plugins sample", listDir("/plugins/").split(/\r?\n/).filter(Boolean).slice(0,80).join(", "));
for(const d of ["/plugins/Tip4Serv/","/plugins/tip4serv/","/plugins/Tip4serv/"]){
  console.log(d, listDir(d).slice(0,300));
}
ws.close();
