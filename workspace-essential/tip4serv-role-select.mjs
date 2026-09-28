const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>String(t.url).includes("product?id="))||list.find(t=>String(t.url).includes("tip4serv.com"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.addEventListener("open",r);ws.addEventListener("error",j);});
let id=1; const pending=new Map();
ws.addEventListener("message",ev=>{const m=JSON.parse(String(ev.data)); if(m.id&&pending.has(m.id)){const p=pending.get(m.id); pending.delete(m.id); m.error?p.reject(new Error(m.error.message)):p.resolve(m.result);}});
const send=(method,params={})=>new Promise((resolve,reject)=>{pending.set(id,{resolve,reject}); ws.send(JSON.stringify({id,method,params})); id++;});
await send("Page.navigate",{url:"https://tip4serv.com/dashboard/product?id=2"});
await new Promise(r=>setTimeout(r,3500));
// ensure discord added
await send("Runtime.evaluate",{expression:`(() => { const list=document.querySelector('#servers_add_2'); if(list) list.style.display='block'; const link=document.querySelector('a[id="379102_2"]'); if(link && !document.body.innerText.includes('GIVE ROLE')){ if(window.jQuery) jQuery(link).click(); else link.click(); } return true; })()`,returnByValue:true});
await new Promise(r=>setTimeout(r,2500));
const info=await send("Runtime.evaluate",{expression:`(() => {
  const giveLabel=[...document.querySelectorAll('label,div,span,th,td,h4,h5')].find(e=>/GIVE ROLE/i.test(e.innerText||'') && e.innerText.length<40);
  const near=giveLabel?.closest('.form-group, .x_panel, .row, fieldset, div') || giveLabel?.parentElement?.parentElement;
  const html=near?.innerHTML?.slice(0,2000);
  const allSelects=[...document.querySelectorAll('select')].map(s=>({name:s.name, id:s.id, cls:s.className, opts:[...s.options].length, sample:[...s.options].slice(0,5).map(o=>o.text)}));
  const roleSelects=allSelects.filter(s=>/role/i.test(s.name+s.id+s.cls));
  // select2 containers
  const s2=[...document.querySelectorAll('.select2-selection, .select2')].map(e=>e.outerHTML.slice(0,200));
  return {giveText:giveLabel?.innerText, html, roleSelects, allSelects:allSelects.filter(s=>/379102|role/i.test(s.name)), s2:s2.slice(0,10)};
})()`,returnByValue:true});
console.log(JSON.stringify(info.result.value,null,2).slice(0,7000));
ws.close();
