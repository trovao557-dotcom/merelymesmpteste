import { execFileSync } from "node:child_process";
import { writeFileSync, mkdirSync } from "node:fs";
const PORT=9222;
const list=await(await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
const tab=list.find(t=>t.type==="page"&&String(t.url).includes("8699357"));
if(!tab) throw new Error("no gportal");
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise(r=>ws.addEventListener("open",r));
let id=1; const pending=new Map();
ws.addEventListener("message",ev=>{const m=JSON.parse(String(ev.data)); if(m.id&&pending.has(m.id)){const p=pending.get(m.id); pending.delete(m.id); m.error?p.reject(new Error(m.error.message)):p.resolve(m.result);}});
const send=(method,params={})=>new Promise((resolve,reject)=>{pending.set(id,{resolve,reject}); ws.send(JSON.stringify({id,method,params})); id++;});
const r=await send("Runtime.evaluate",{expression:`(()=>{const a=[...document.querySelectorAll('a')].find(x=>(x.href||'').startsWith('ftp://')); return a?a.href:null;})()`,returnByValue:true});
const base=(r.result.value||"").replace(/\/$/,"");
if(!base){ console.log("NO_FTP"); ws.close(); process.exit(1); }
mkdirSync("C:/Users/MerelyMe/Documents/MerelyMeSMP/_tip4serv",{recursive:true});
const out=execFileSync("curl.exe",["-s","--ftp-pasv","--list-only",`${base}/plugins/Tip4Serv/`],{encoding:"utf8"});
console.log("FILES", out.split(/\r?\n/).filter(Boolean).join(","));
for(const f of out.split(/\r?\n/).filter(Boolean)){
  if(!/\.(yml|yaml|txt|json|properties)$/i.test(f)) continue;
  if(/key|secret|token|password/i.test(f) && !/config/i.test(f)) { console.log("skip secretish", f); continue; }
  try{
    const data=execFileSync("curl.exe",["-s","--ftp-pasv",`${base}/plugins/Tip4Serv/${f}`]);
    // redact keys before write for api key files
    let text=data.toString("utf8");
    if(/api[_-]?key|key:/i.test(text)){
      text=text.replace(/(api[_-]?key\s*[:=]\s*)["']?[^"'#\n]+/ig,"$1***REDACTED***");
      text=text.replace(/(key\s*[:=]\s*)["']?[A-Za-z0-9_-]{10,}/ig,"$1***REDACTED***");
    }
    writeFileSync(`C:/Users/MerelyMe/Documents/MerelyMeSMP/_tip4serv/${f}`, text);
    console.log("ok", f, text.length);
  }catch(e){ console.log("fail", f); }
}
ws.close();
