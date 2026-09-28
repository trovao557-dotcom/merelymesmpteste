const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>String(t.url).includes("tip4serv.com"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.addEventListener("open",r);ws.addEventListener("error",j);});
let id=1; const pending=new Map();
ws.addEventListener("message",ev=>{const m=JSON.parse(String(ev.data)); if(m.id&&pending.has(m.id)){const p=pending.get(m.id); pending.delete(m.id); m.error?p.reject(new Error(m.error.message)):p.resolve(m.result);}});
const send=(method,params={})=>new Promise((resolve,reject)=>{pending.set(id,{resolve,reject}); ws.send(JSON.stringify({id,method,params})); id++;});
await send("Page.navigate",{url:"https://tip4serv.com/dashboard/product?id=0"});
await new Promise(r=>setTimeout(r,4000));
await send("Runtime.evaluate",{expression:`(() => {
  if(!(document.body.innerText||'').includes('GIVE ROLE')){
    const list=document.querySelector('#servers_add_0'); if(list) list.style.display='block';
    const link=document.querySelector('a[id="379102_0"]');
    if(link){ if(window.jQuery) jQuery(link).click(); else link.click(); }
  }
  return true;
})()`,returnByValue:true});
await new Promise(r=>setTimeout(r,2500));
const set=await send("Runtime.evaluate",{expression:`(() => {
  const roleId='1544904299627159632';
  document.querySelector('#select_pay_discord_roles_379102 .vscomp-toggle-button')?.click();
  const opts=[...document.querySelectorAll('.vscomp-option')];
  const opt=opts.find(el=>el.getAttribute('data-value')===roleId) || opts.find(el=>/Prime/i.test(el.innerText||''));
  opt?.click();
  // force hidden
  const hidden=document.querySelector('input[name="payment_role_add[379102]"]');
  if(hidden){
    hidden.value=roleId;
    // VirtualSelect sometimes uses comma values
    hidden.setAttribute('value', roleId);
  }
  // try API
  const ele=document.querySelector('#select_pay_discord_roles_379102');
  try{
    if(ele?.virtualSelect?.setValue) ele.virtualSelect.setValue([roleId]);
    else if(window.VirtualSelect?.setValue) {}
  }catch{}
  const ch=document.querySelector('select[name="payment_message_channel[379102]"]');
  if(ch){ ch.value='1544904368157884476'; ch.dispatchEvent(new Event('change',{bubbles:true})); }
  return {opt:opt?.innerText?.slice(0,40), hidden:hidden?.value, label:document.querySelector('#select_pay_discord_roles_379102 .vscomp-value')?.innerText, allPrime:opts.filter(o=>/Prime/i.test(o.innerText||'')).map(o=>({t:o.innerText.trim().slice(0,30),v:o.getAttribute('data-value')} ))};
})()`,returnByValue:true});
console.log("set", JSON.stringify(set.result.value,null,2));
await send("Runtime.evaluate",{expression:`(() => { [...document.querySelectorAll('button')].find(b=>/^\\s*Save\\s*$/i.test(b.innerText||''))?.click(); return true; })()`,returnByValue:true});
await new Promise(r=>setTimeout(r,3000));
await send("Page.reload");
await new Promise(r=>setTimeout(r,4000));
const ver=await send("Runtime.evaluate",{expression:`(() => ({role:document.querySelector('input[name="payment_role_add[379102]"]')?.value, label:document.querySelector('#select_pay_discord_roles_379102 .vscomp-value')?.innerText, hasGive:(document.body.innerText||'').includes('GIVE ROLE')}))()`,returnByValue:true});
console.log("verify", ver.result.value);
ws.close();
