const GUILD="1534136666984419348";
const CH="1544904386587787334";
const TAB="846D0A1EFB6158B071D94EF7A4C37A66";
const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const disc=list.find(t=>t.url&&t.url.includes("discord.com/channels/153413"));
const tt=list.find(t=>t.id===TAB);
const connect=async(tab)=>{
  const ws=new WebSocket(tab.webSocketDebuggerUrl);
  await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
  let id=1; const pending=new Map();
  ws.onmessage=ev=>{const m=JSON.parse(ev.data); if(m.id==null)return; const p=pending.get(m.id); if(!p)return; pending.delete(m.id); m.error?p.reject(Error(m.error.message)):p.resolve(m.result);};
  const send=(method,params={})=>new Promise((resolve,reject)=>{const i=id++; const t=setTimeout(()=>{if(pending.has(i)){pending.delete(i);reject(Error("to"));}},30000); pending.set(i,{resolve:v=>{clearTimeout(t);resolve(v)},reject:e=>{clearTimeout(t);reject(e)}}); ws.send(JSON.stringify({id:i,method,params}));});
  const evalE=async(expression)=>{const r=await send("Runtime.evaluate",{expression,returnByValue:true,awaitPromise:true,userGesture:true}); if(r.exceptionDetails) throw new Error(r.exceptionDetails.text); return r.result.value;};
  return {ws,send,evalE};
};
const d=await connect(disc);
const tok=(await d.evalE(`(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`));
const msgs=await(await fetch(`https://discord.com/api/v9/channels/${CH}/messages?limit=5`,{headers:{Authorization:tok}})).json();
const panel=msgs.find(m=>m.author?.username==="Ticket Tool");
console.log("panel msg", panel?.id, panel?.embeds?.[0]?.description?.slice(0,80), panel?.components?.[0]?.components?.[0]?.label);
const url=`https://discord.com/channels/${GUILD}/${CH}/${panel.id}`;
d.ws.close();

const t=await connect(tt);
const setNative=`const setNative=(el,value)=>{if(!el)return; const proto=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype; const setter=Object.getOwnPropertyDescriptor(proto,'value')?.set; setter?setter.call(el,value):(el.value=value); el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true}));};`;
await t.evalE(`(() => { const b=[...document.querySelectorAll('button,a')].find(x=>/^Back$/i.test((x.innerText||'').trim())); if(b) b.click(); return 1; })()`);
await new Promise(r=>setTimeout(r,800));
await t.evalE(`(() => {
  ${setNative}
  setNative(document.querySelector('#panelUpdateUrl'), ${JSON.stringify(url)});
  setNative(document.querySelector('#embed_title'), 'MerelyMeSMP Support');
  setNative(document.querySelector('#embed_description'), 'Click **Open Ticket** and fill the form in **English**.\\n\\n**Categories**\\n⚖️ Unban Appeal\\n🚨 Report Player\\n💸 Refund\\n🎫 General Support\\n🎬 Media Apply\\n💳 Payment Support');
  setNative(document.querySelector('#embed_color'), '#FEE75C');
  setNative(document.querySelector('#message_content'), '**Need help?** Open a ticket below.');
  const save=[...document.querySelectorAll('button')].find(x=>/^Save$/i.test(x.innerText.trim()));
  if(save) save.click();
  return document.querySelector('#panelUpdateUrl')?.value;
})()`);
await new Promise(r=>setTimeout(r,2500));
await t.evalE(`(() => { const b=document.querySelector('#updatePanelButton') || [...document.querySelectorAll('button,a')].find(x=>/Update Panel/i.test(x.innerText||'')); if(b) b.click(); return !!b; })()`);
await new Promise(r=>setTimeout(r,3000));
console.log("after update", await t.evalE(`(() => (document.body.innerText||'').slice(-400))()`));
t.ws.close();

// re-check message
const list2=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const disc2=list2.find(t=>t.url&&t.url.includes("discord.com/channels/153413"));
const d2=await connect(disc2);
const tok2=(await d2.evalE(`(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`));
const m2=await(await fetch(`https://discord.com/api/v9/channels/${CH}/messages/${panel.id}`,{headers:{Authorization:tok2}})).json();
console.log(JSON.stringify({title:m2.embeds?.[0]?.title, desc:m2.embeds?.[0]?.description, label:m2.components?.[0]?.components?.[0]?.label},null,2));
d2.ws.close();
