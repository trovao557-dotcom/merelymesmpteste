const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>String(t.url).includes("tip4serv.com"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.addEventListener("open",r);ws.addEventListener("error",j);});
let id=1; const pending=new Map();
ws.addEventListener("message",ev=>{const m=JSON.parse(String(ev.data)); if(m.id&&pending.has(m.id)){const p=pending.get(m.id); pending.delete(m.id); m.error?p.reject(new Error(m.error.message)):p.resolve(m.result);}});
const send=(method,params={})=>new Promise((resolve,reject)=>{pending.set(id,{resolve,reject}); ws.send(JSON.stringify({id,method,params})); id++;});

await send("Page.navigate",{url:"https://tip4serv.com/dashboard/product?id=2"});
await new Promise(r=>setTimeout(r,4000));

// Force show servers list and click Discord
const r1=await send("Runtime.evaluate",{expression:`(() => {
  try {
    const list=document.querySelector('#servers_add_2, .servers_add');
    if(list){ list.style.display='block'; list.classList.add('show'); }
    // Tip4Serv jQuery handler
    const link=document.querySelector('a#379102_2') || document.querySelector('a.discord_server');
    if(!link) return {err:'no link'};
    if(window.jQuery){ jQuery(link).trigger('click'); return {via:'jq', id:link.id}; }
    link.click();
    return {via:'click', id:link.id};
  } catch(e){ return {err:String(e)} }
})()`,returnByValue:true});
console.log("r1", r1.result.value);
await new Promise(r=>setTimeout(r,3000));
const r2=await send("Runtime.evaluate",{expression:`(() => {
  const text=document.body.innerText||'';
  return {
    has379: text.includes('#379102') || text.includes('379102'),
    section: text.match(/#379102[\\s\\S]{0,2000}/)?.[0]?.slice(0,1500),
    roleEls: [...document.querySelectorAll('select,input,label,[class*=role]')].filter(e=>/379102|discord|role/i.test(e.outerHTML+e.innerText)).map(e=>({tag:e.tagName,name:e.name,t:(e.innerText||'').slice(0,60),html:e.outerHTML.slice(0,180)})).slice(0,25)
  };
})()`,returnByValue:true});
console.log(JSON.stringify(r2.result.value,null,2).slice(0,5000));
ws.close();
