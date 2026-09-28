const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>String(t.url).includes("product?id=2"))||list.find(t=>String(t.url).includes("tip4serv.com/dashboard/product"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.addEventListener("open",r);ws.addEventListener("error",j);});
let id=1; const pending=new Map();
ws.addEventListener("message",ev=>{const m=JSON.parse(String(ev.data)); if(m.id&&pending.has(m.id)){const p=pending.get(m.id); pending.delete(m.id); m.error?p.reject(new Error(m.error.message)):p.resolve(m.result);}});
const send=(method,params={})=>new Promise((resolve,reject)=>{pending.set(id,{resolve,reject}); ws.send(JSON.stringify({id,method,params})); id++;});
await send("Page.navigate",{url:"https://tip4serv.com/dashboard/product?id=2"});
await new Promise(r=>setTimeout(r,4000));
const info=await send("Runtime.evaluate",{expression:`(() => {
  // find all elements mentioning 379102 or Discord server add
  const html=document.documentElement.innerHTML;
  const has379=html.includes('379102');
  const idx=html.indexOf('379102');
  const around=idx>=0?html.slice(Math.max(0,idx-200), idx+400):null;
  const addBtns=[...document.querySelectorAll('a,button,input')].filter(e=>/discord|379102|add server|server_id/i.test((e.outerHTML||'')+(e.innerText||''))).map(e=>({tag:e.tagName,t:(e.innerText||'').slice(0,60),href:e.href||'',name:e.name,onclick:(e.getAttribute('onclick')||'').slice(0,120),html:e.outerHTML.slice(0,200)}));
  // Tip4Serv often has hidden server list
  const scripts=[...document.querySelectorAll('script')].map(s=>s.textContent||'').filter(t=>/379102|discord/i.test(t)).map(t=>t.slice(0,300));
  return {has379, around, addBtns:addBtns.slice(0,20), scripts:scripts.slice(0,5), bodyHas:(document.body.innerText.includes('Discord'))};
})()`,returnByValue:true});
console.log(JSON.stringify(info.result.value,null,2).slice(0,6000));
ws.close();
