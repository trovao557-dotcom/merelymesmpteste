const TAB_ID = "B4DD980A29D559B0C6D08E4C7DB72E9D";
const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab = list.find(t => t.id === TAB_ID) || list.find(t => String(t.url).includes("tip4serv.com"));
const ws = new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.addEventListener("open",r); ws.addEventListener("error",j);});
let id=1; const pending=new Map();
ws.addEventListener("message",ev=>{const m=JSON.parse(String(ev.data)); if(m.id&&pending.has(m.id)){const p=pending.get(m.id); pending.delete(m.id); m.error?p.reject(new Error(m.error.message)):p.resolve(m.result);}});
const send=(method,params={})=>new Promise((resolve,reject)=>{pending.set(id,{resolve,reject}); ws.send(JSON.stringify({id,method,params})); id++;});
await send("Page.navigate",{url:"https://tip4serv.com/dashboard/product?id=2"});
await new Promise(r=>setTimeout(r,4000));
const state=await send("Runtime.evaluate",{expression:`(() => {
  const text=document.body.innerText||'';
  // click choose server / add discord
  const add=[...document.querySelectorAll('a,button,div')].find(e=>/Connect another server|Add Discord|Choose a server|Add all/i.test(e.innerText||'') && (e.innerText||'').length<80);
  return {
    product: (text.match(/Edit product[^\n]+|WARRIOR[^\n]*/)||[])[0],
    snippet: (text.match(/Server & Discord cmds[\s\S]{0,2500}/)||[])[0],
    addText: add?.innerText,
    serverOptions: [...document.querySelectorAll('select option, .dropdown-menu a, .dropdown-menu li, [data-server]')].map(e=>e.innerText.trim()).filter(Boolean).slice(0,30),
    inputs: [...document.querySelectorAll('input,select,textarea')].map(e=>({n:e.name||e.id,t:e.type||e.tagName,v:(e.value||'').slice(0,80)})).filter(x=>/discord|role|server|command|379102/i.test(x.n+x.v)).slice(0,40)
  };
})()`,returnByValue:true});
console.log(JSON.stringify(state.result.value,null,2));
// Try open server chooser
const click=await send("Runtime.evaluate",{expression:`(() => {
  const el=[...document.querySelectorAll('a,button')].find(e=>/Connect another server or API|Choose a server/i.test(e.innerText||''));
  if(el){el.click(); return 'clicked '+el.innerText.slice(0,60);}
  // toggle dropdown near CHOOSE A SERVER
  const dd=[...document.querySelectorAll('.dropdown-toggle, [data-toggle=dropdown]')].find(e=>/378958|MerelyMe|CHOOSE|server/i.test((e.innerText||'')+(e.parentElement?.innerText||'')));
  if(dd){dd.click(); return 'dd '+dd.innerText.slice(0,60);}
  return 'none';
})()`,returnByValue:true});
console.log('click', click.result.value);
await new Promise(r=>setTimeout(r,1000));
const after=await send("Runtime.evaluate",{expression:`(() => ({
  menu:[...document.querySelectorAll('.dropdown-menu li, .dropdown-menu a, .open *')].map(e=>e.innerText.trim()).filter(t=>t&&t.length<80).slice(0,40),
  text:(document.body.innerText||'').match(/Discord[\s\S]{0,400}|379102[\s\S]{0,200}/)?.[0]
}))()`,returnByValue:true});
console.log(JSON.stringify(after.result.value,null,2));
ws.close();
