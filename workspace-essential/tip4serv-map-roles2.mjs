const PRODUCTS=[
  {id:3, role:/Knight/i, name:"KNIGHT"},
  {id:2, role:/Warrior/i, name:"WARRIOR"},
  {id:1, role:/Macer/i, name:"MACER"},
  {id:0, role:/Prime/i, name:"PRIME"},
];
const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>String(t.url).includes("tip4serv.com"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.addEventListener("open",r);ws.addEventListener("error",j);});
let id=1; const pending=new Map();
ws.addEventListener("message",ev=>{const m=JSON.parse(String(ev.data)); if(m.id&&pending.has(m.id)){const p=pending.get(m.id); pending.delete(m.id); m.error?p.reject(new Error(m.error.message)):p.resolve(m.result);}});
const send=(method,params={})=>new Promise((resolve,reject)=>{pending.set(id,{resolve,reject}); ws.send(JSON.stringify({id,method,params})); id++;});

for(const p of PRODUCTS){
  console.log("\\n===", p.name, "===");
  await send("Page.navigate",{url:`https://tip4serv.com/dashboard/product?id=${p.id}`});
  await new Promise(r=>setTimeout(r,4000));
  const add=await send("Runtime.evaluate",{expression:`(() => {
    try{
      const list=document.querySelector('#servers_add_${p.id}, .servers_add');
      if(list){ list.style.display='block'; list.style.visibility='visible'; }
      const link=document.querySelector('a[id="379102_${p.id}"]') || [...document.querySelectorAll('a.discord_server')].find(a=>a.id.startsWith('379102_'));
      if(!link) return {err:'no link', has:document.documentElement.innerHTML.includes('379102')};
      if(window.jQuery) jQuery(link).click();
      else link.click();
      return {ok:true, id:link.id};
    }catch(e){return {err:String(e)}}
  })()`,returnByValue:true});
  console.log("add", add.result.value);
  await new Promise(r=>setTimeout(r,2500));
  const ui=await send("Runtime.evaluate",{expression:`(() => {
    const text=document.body.innerText||'';
    const section=text.match(/#379102[\\s\\S]{0,2500}/)?.[0];
    const selects=[...document.querySelectorAll('select')].map(s=>({name:s.name, opts:[...s.options].map(o=>({t:o.text,v:o.value})).slice(0,40)})).filter(s=>/379102|role|discord/i.test(s.name+JSON.stringify(s.opts)));
    const checks=[...document.querySelectorAll('input[type=checkbox]')].map(i=>({name:i.name,v:i.value,label:(i.closest('label')||i.parentElement)?.innerText?.slice(0,80)}));
    return {has379:text.includes('379102'), section:section?.slice(0,1200), selects, checks:checks.filter(c=>/role|Warrior|Knight|Macer|Prime|discord|379102/i.test(JSON.stringify(c))).slice(0,30)};
  })()`,returnByValue:true});
  console.log("ui", JSON.stringify(ui.result.value,null,2).slice(0,3000));

  // pick role
  const pick=await send("Runtime.evaluate",{expression:`(() => {
    const re=${p.role};
    for(const s of document.querySelectorAll('select')){
      for(const o of [...s.options]){
        if(re.test(o.text)){
          o.selected=true; s.value=o.value;
          if(window.jQuery){ try{ jQuery(s).val([...s.selectedOptions].map(x=>x.value)).trigger('change'); }catch{ jQuery(s).val(o.value).trigger('change'); } }
          s.dispatchEvent(new Event('change',{bubbles:true}));
          return {ok:true, name:s.name, text:o.text, value:o.value, multi:s.multiple};
        }
      }
    }
    for(const lab of document.querySelectorAll('label, li, span, div')){
      const t=(lab.innerText||'').trim();
      if(re.test(t) && t.length<40){
        const inp=lab.querySelector('input[type=checkbox]');
        if(inp){ inp.checked=true; inp.dispatchEvent(new Event('change',{bubbles:true})); return {ok:true, via:'check', t}; }
        lab.click(); return {ok:true, via:'click', t};
      }
    }
    return {ok:false};
  })()`,returnByValue:true});
  console.log("pick", pick.result.value);

  // save
  await send("Runtime.evaluate",{expression:`(() => { const s=[...document.querySelectorAll('button')].find(b=>/^\\s*Save\\s*$/i.test(b.innerText||'')); s?.click(); return !!s; })()`,returnByValue:true});
  await new Promise(r=>setTimeout(r,2500));
  const after=await send("Runtime.evaluate",{expression:`(() => ({flash:(document.body.innerText||'').match(/success|saved|error/i)?.[0], has379:document.body.innerText.includes('379102'), section:(document.body.innerText||'').match(/#379102[\\s\\S]{0,500}/)?.[0]})`,returnByValue:true});
  console.log("after", after.result.value);
}
ws.close();
