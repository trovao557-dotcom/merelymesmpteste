import { execFileSync } from "node:child_process";
import { writeFileSync, readFileSync, mkdirSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";

const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>t.id==="40F71275E444B4E2C3D15A2C2DB2930C")||list.find(t=>String(t.url).includes("8699357")&&!String(t.url).includes("files")&&!String(t.url).includes("console"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
let id=1; const pending=new Map();
ws.onmessage=ev=>{const m=JSON.parse(ev.data); if(m.id==null)return; const p=pending.get(m.id); if(!p)return; pending.delete(m.id); m.error?p.reject(Error(m.error.message)):p.resolve(m.result);};
const send=(method,params={})=>new Promise((resolve,reject)=>{const i=id++; pending.set(i,{resolve,reject}); ws.send(JSON.stringify({id:i,method,params}));});
const r=await send("Runtime.evaluate",{expression:`([...document.querySelectorAll('a')].map(a=>a.href).find(h=>/^ftp:/i.test(h||'')&&!h.includes('***')))`,returnByValue:true});
ws.close();
const base=r.result.value.replace(/\/$/,'');
const listing=execFileSync("curl.exe",["-sS","--ftp-pasv",`${base}/plugins/`],{encoding:"utf8"});
const lines=listing.split(/\r?\n/).filter(Boolean);
for (const l of lines) {
  if (/grim|chatfil/i.test(l)) console.log(l);
}
const out=join(tmpdir(),"mm-ac");
mkdirSync(out,{recursive:true});
for (const dir of ["GrimAC","grimac","Grim","ChatFilter","chatfilter"]) {
  try {
    const l=execFileSync("curl.exe",["-sS","--ftp-pasv",`${base}/plugins/${dir}/`],{encoding:"utf8"});
    console.log("LIST",dir,l.slice(0,500));
  } catch(e) { console.log("no",dir); }
}
