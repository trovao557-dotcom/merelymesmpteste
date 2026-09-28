const TAB="846D0A1EFB6158B071D94EF7A4C37A66";
const CH="1544904386587787334";
const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const disc=list.find(t=>t.url&&t.url.includes("discord.com/channels/153413"));
const tt=list.find(t=>t.id===TAB);
const connect=async(tab)=>{
  const ws=new WebSocket(tab.webSocketDebuggerUrl);
  await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
  let id=1; const pending=new Map();
  ws.onmessage=ev=>{const m=JSON.parse(ev.data); if(m.id==null)return; const p=pending.get(m.id); if(!p)return; pending.delete(m.id); m.error?p.reject(Error(m.error.message)):p.resolve(m.result);};
  const send=(method,params={})=>new Promise((resolve,reject)=>{const i=id++; const t=setTimeout(()=>{if(pending.has(i)){pending.delete(i);reject(Error("to"));}},45000); pending.set(i,{resolve:v=>{clearTimeout(t);resolve(v)},reject:e=>{clearTimeout(t);reject(e)}}); ws.send(JSON.stringify({id:i,method,params}));});
  const evalE=async(expression)=>{const r=await send("Runtime.evaluate",{expression,returnByValue:true,awaitPromise:true,userGesture:true}); if(r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description||r.exceptionDetails.text); return r.result.value;};
  return {ws,evalE};
};
const d=await connect(disc);
const tok=await d.evalE(`(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`);
const msgs=await(await fetch(`https://discord.com/api/v9/channels/${CH}/messages?limit=10`,{headers:{Authorization:tok}})).json();
for(const m of msgs.filter(x=>x.author?.username==='Ticket Tool')){
  await fetch(`https://discord.com/api/v9/channels/${CH}/messages/${m.id}`,{method:"DELETE",headers:{Authorization:tok}});
  console.log("deleted panel", m.id);
  await new Promise(r=>setTimeout(r,350));
}
d.ws.close();

const t=await connect(tt);
const H=`const setNative=(el,value)=>{if(!el)return; const proto=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:el.tagName==='SELECT'?HTMLSelectElement.prototype:HTMLInputElement.prototype; const setter=Object.getOwnPropertyDescriptor(proto,'value')?.set; setter?setter.call(el,value):(el.value=value); el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true}));};`;
// Navigate to panel message editor
await t.evalE(`(()=>{const b=[...document.querySelectorAll('button,a')].find(x=>/^Back$/i.test((x.innerText||'').trim())); if(b)b.click(); return 1;})()`);
await new Promise(r=>setTimeout(r,700));
await t.evalE(`(()=>{const e=[...document.querySelectorAll('button,a')].find(x=>/Edit Panel Message/i.test(x.innerText||'')); if(e){e.click(); return 'edit-panel'} const card=[...document.querySelectorAll('.card-box')].find(el=>/message used to create tickets/i.test(el.innerText||'')); if(card){card.click(); return 'card'} return 'no';})()`);
await new Promise(r=>setTimeout(r,1200));
const edited=await t.evalE(`(()=>{
  ${H}
  setNative(document.querySelector('#embed_title'), 'MerelyMeSMP Support');
  setNative(document.querySelector('#embed_description'), 'Click **Open Ticket** and fill the form in **English**.\\n\\n**Categories**\\n⚖️ Unban Appeal\\n🚨 Report Player\\n💸 Refund\\n🎫 General Support\\n🎬 Media Apply\\n💳 Payment Support');
  setNative(document.querySelector('#embed_color'), '#FEE75C');
  setNative(document.querySelector('#message_content'), '**Need help?** Open a ticket below.');
  return {title:document.querySelector('#embed_title')?.value, desc:(document.querySelector('#embed_description')?.value||'').slice(0,60), hash:location.hash};
})()`);
console.log("edited", edited);
await t.evalE(`(()=>{const b=[...document.querySelectorAll('button')].find(x=>/^Save$/i.test(x.innerText.trim())); if(b)b.click(); return 1;})()`);
await new Promise(r=>setTimeout(r,2500));
await t.evalE(`(()=>{
  ${H}
  const ch=document.querySelector('#sendChannel');
  if(ch){[...ch.options].forEach(o=>{if(/create-ticket/i.test(o.text)){ch.value=o.value;o.selected=true;}}); ch.dispatchEvent(new Event('change',{bubbles:true}));}
  const save=[...document.querySelectorAll('button')].find(x=>/^Save$/i.test(x.innerText.trim()));
  if(save) save.click();
  return ch?[...ch.selectedOptions].map(o=>o.text):null;
})()`);
await new Promise(r=>setTimeout(r,2000));
await t.evalE(`(()=>{const b=[...document.querySelectorAll('button,a')].find(x=>/^Send$/i.test((x.innerText||'').trim())); if(b)b.click(); return !!b;})()`);
await new Promise(r=>setTimeout(r,1500));
console.log(await t.evalE(`(()=>{
  const ch=document.querySelector('#sendChannel');
  if(ch){[...ch.options].forEach(o=>{if(/create-ticket/i.test(o.text)){ch.value=o.value;o.selected=true;}}); ch.dispatchEvent(new Event('change',{bubbles:true}));}
  const c=[...document.querySelectorAll('button')].find(b=>/^(Send|Confirm)$/i.test(b.innerText.trim()));
  if(c)c.click();
  return (document.body.innerText||'').slice(-250);
})()`));
await new Promise(r=>setTimeout(r,2500));
t.ws.close();

const d2=await connect(list.find(x=>x.url&&x.url.includes("discord.com/channels/153413"))||disc);
const tok2=await d2.evalE(`(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`);
const m2=await(await fetch(`https://discord.com/api/v9/channels/${CH}/messages?limit=3`,{headers:{Authorization:tok2}})).json();
const p=m2.find(x=>x.author?.username==='Ticket Tool');
console.log(JSON.stringify({title:p?.embeds?.[0]?.title, desc:p?.embeds?.[0]?.description, label:p?.components?.[0]?.components?.[0]?.label},null,2));
d2.ws.close();
