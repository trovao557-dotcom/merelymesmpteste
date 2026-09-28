import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { join } from "node:path";
const out="C:\\Users\\MerelyMe\\Documents\\MerelyMeSMP\\tab-live";
const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>String(t.url).includes("8699357")&&!String(t.url).includes("files")&&!String(t.url).includes("console"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
let id=1; const pending=new Map();
ws.onmessage=ev=>{const m=JSON.parse(ev.data); if(m.id==null)return; const p=pending.get(m.id); if(!p)return; pending.delete(m.id); m.error?p.reject(Error(m.error.message)):p.resolve(m.result);};
const send=(method,params={})=>new Promise((resolve,reject)=>{const i=id++; pending.set(i,{resolve,reject}); ws.send(JSON.stringify({id:i,method,params}));});
const r=await send("Runtime.evaluate",{expression:`([...document.querySelectorAll('a')].map(a=>a.href).find(h=>/^ftp:/i.test(h||'')&&!h.includes('***')))`,returnByValue:true});
ws.close();
const base=r.result.value.replace(/\/$/,'');
// search more places
const files=[
  "server.properties",
  "plugins/TAB/config.yml",
  "plugins/SKInfo/settings.yml",
  "plugins/SKAnnounce/settings.yml",
  "plugins/SKHolograms/config.yml",
];
for (const f of files) {
  try {
    const local=join(out,"search_"+f.replace(/\//g,"_"));
    execFileSync("curl.exe",["-sS","--ftp-pasv","-o",local,`${base}/${f}`],{stdio:"pipe"});
    const txt=readFileSync(local,"utf8");
    const hits=txt.split(/\r?\n/).filter(l=>/store\.hit|Store\.hit|STOREHIT|hit\.smp|storehit/i.test(l));
    if (hits.length) console.log(f, hits);
    // also any line with .hit.
    const hits2=txt.split(/\r?\n/).filter(l=>/\.hit\.|hit\.smp/i.test(l));
    if (hits2.length) console.log("dot", f, hits2);
  } catch {}
}
// list TAB for subdirs
try {
  console.log(execFileSync("curl.exe",["-sS","--ftp-pasv",`${base}/plugins/TAB/`],{encoding:"utf8"}));
} catch {}
