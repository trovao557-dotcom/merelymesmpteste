const PRODUCTS=[
  {id:1, roleId:"1545039141773770772", name:"Macer"},
  {id:0, roleId:"1544904299627159632", name:"Prime"},
];
const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>String(t.url).includes("tip4serv.com"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.addEventListener("open",r);ws.addEventListener("error",j);});
let id=1; const pending=new Map();
ws.addEventListener("message",ev=>{const m=JSON.parse(String(ev.data)); if(m.id&&pending.has(m.id)){const p=pending.get(m.id); pending.delete(m.id); m.error?p.reject(new Error(m.error.message)):p.resolve(m.result);}});
const send=(method,params={})=>new Promise((resolve,reject)=>{pending.set(id,{resolve,reject}); ws.send(JSON.stringify({id,method,params})); id++;});

for(const p of PRODUCTS){
  console.log("===", p.name);
  await send("Page.navigate",{url:`https://tip4serv.com/dashboard/product?id=${p.id}`});
  await new Promise(r=>setTimeout(r,4000));
  await send("Runtime.evaluate",{expression:`(() => {
    if(!(document.body.innerText||'').includes('GIVE ROLE')){
      const list=document.querySelector('#servers_add_${p.id}'); if(list) list.style.display='block';
      const link=document.querySelector('a[id="379102_${p.id}"]');
      if(link){ if(window.jQuery) jQuery(link).click(); else link.click(); }
    }
    return true;
  })()`,returnByValue:true});
  await new Promise(r=>setTimeout(r,2500));
  const set=await send("Runtime.evaluate",{expression:`(() => {
    const roleId=${JSON.stringify(p.roleId)};
    const name=${JSON.stringify(p.name)};
    const toggle=document.querySelector('#select_pay_discord_roles_379102 .vscomp-toggle-button');
    toggle?.click();
    // click matching option by data-value
    let opt=[...document.querySelectorAll('.vscomp-option')].find(el=>el.getAttribute('data-value')===roleId);
    if(!opt) opt=[...document.querySelectorAll('.vscomp-option')].find(el=>new RegExp(name,'i').test(el.innerText||''));
    if(opt){ opt.click(); }
    else {
      const hidden=document.querySelector('input[name="payment_role_add[379102]"]');
      if(hidden){ hidden.value=roleId; hidden.dispatchEvent(new Event('change',{bubbles:true})); }
    }
    // close dropdown
    document.body.click();
    const ch=document.querySelector('select[name="payment_message_channel[379102]"]');
    if(ch){ ch.value='1544904368157884476'; ch.dispatchEvent(new Event('change',{bubbles:true})); }
    return {
      clicked: !!opt,
      optText: opt?.innerText?.slice(0,40),
      hidden: document.querySelector('input[name="payment_role_add[379102]"]')?.value,
      label: document.querySelector('#select_pay_discord_roles_379102 .vscomp-value')?.innerText
    };
  })()`,returnByValue:true});
  console.log("set", set.result.value);
  await send("Runtime.evaluate",{expression:`(() => { const s=[...document.querySelectorAll('button')].find(b=>/^\\s*Save\\s*$/i.test(b.innerText||'')); s?.click(); return !!s; })()`,returnByValue:true});
  await new Promise(r=>setTimeout(r,3000));
  // reload verify
  await send("Page.navigate",{url:`https://tip4serv.com/dashboard/product?id=${p.id}`});
  await new Promise(r=>setTimeout(r,3500));
  const ver=await send("Runtime.evaluate",{expression:`(() => ({
    hasGive:(document.body.innerText||'').includes('GIVE ROLE'),
    role: document.querySelector('input[name="payment_role_add[379102]"]')?.value,
    label: document.querySelector('#select_pay_discord_roles_379102 .vscomp-value')?.innerText
  }))()`,returnByValue:true});
  console.log("verify", ver.result.value);
}
ws.close();
