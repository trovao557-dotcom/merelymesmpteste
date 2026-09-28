const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab = list.find((t) => t.url && t.url.includes("1544904353121308672"));
const ws = new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r, j) => {
  ws.onopen = r;
  ws.onerror = j;
});
let id = 1;
const pending = new Map();
ws.onmessage = (ev) => {
  const m = JSON.parse(ev.data);
  if (m.id == null) return;
  const p = pending.get(m.id);
  if (!p) return;
  pending.delete(m.id);
  m.error ? p.reject(Error(m.error.message)) : p.resolve(m.result);
};
const send = (method, params = {}) =>
  new Promise((resolve, reject) => {
    const i = id++;
    const t = setTimeout(() => {
      if (pending.has(i)) {
        pending.delete(i);
        reject(Error("to"));
      }
    }, 30000);
    pending.set(i, {
      resolve: (v) => {
        clearTimeout(t);
        resolve(v);
      },
      reject: (e) => {
        clearTimeout(t);
        reject(e);
      },
    });
    ws.send(JSON.stringify({ id: i, method, params }));
  });

const info = await send("Runtime.evaluate", {
  expression: `(() => {
    const btn=[...document.querySelectorAll('button')].find(x=>/Link my server to Tip4Serv/i.test(x.innerText||''));
    if(!btn) return {err:'no'};
    // walk react fiber
    const key=Object.keys(btn).find(k=>k.startsWith('__reactFiber')||k.startsWith('__reactInternalInstance'));
    let fiber=btn[key];
    const found=[];
    for(let i=0;i<40 && fiber;i++){
      const props=fiber.memoizedProps||fiber.pendingProps;
      if(props){
        const interesting={};
        for(const [k,v] of Object.entries(props)){
          if(typeof v==='string' || typeof v==='number' || typeof v==='boolean') interesting[k]=v;
          if(k==='message' && v && typeof v==='object') interesting.messageId=v.id||v.messageId;
          if(k==='customId'||k==='custom_id'||k==='messageId'||k==='channelId'||k==='applicationId') interesting[k]=v;
        }
        if(Object.keys(interesting).length) found.push({type:fiber.type?.displayName||fiber.elementType?.displayName||typeof fiber.type, interesting});
      }
      fiber=fiber.return;
    }
    // also try props on onClick bound
    return {found: found.slice(0,15), outer: btn.outerHTML.slice(0,500)};
  })()`,
  returnByValue: true,
});
console.log(JSON.stringify(info.result.value, null, 2).slice(0, 8000));

// Network: enable and click while listening
await send("Network.enable");
const auth = await send("Runtime.evaluate", {
  expression: `(()=>{
    const tok=(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})();
    let sid=null;
    webpackChunkdiscord_app.push([[Symbol()],{},(req)=>{for(const m of Object.values(req.c||{})){try{const fn=m?.exports?.default?.getSessionId||m?.exports?.getSessionId||m?.exports?.Z?.getSessionId; if(fn){const v=fn(); if(typeof v==='string'&&v.length>8) sid=v;}}catch{}}}]);
    webpackChunkdiscord_app.pop();
    return {tok,sid};
  })()`,
  returnByValue: true,
});

const reqs = [];
ws.addEventListener("message", (ev) => {
  const m = JSON.parse(String(ev.data));
  if (m.method === "Network.requestWillBeSent") {
    const u = m.params?.request?.url || "";
    if (/interaction|tip4|oauth|authorize/i.test(u)) {
      reqs.push({ url: u, post: (m.params.request.postData || "").slice(0, 500) });
    }
  }
});

await send("Runtime.evaluate", {
  expression: `(() => { const b=[...document.querySelectorAll('button')].find(x=>/Link my server to Tip4Serv/i.test(x.innerText||'')); b.click(); return true; })()`,
  returnByValue: true,
  userGesture: true,
});
await new Promise((r) => setTimeout(r, 2500));
console.log("reqs", JSON.stringify(reqs, null, 2));
console.log("session", auth.result.value.sid?.slice(0, 8));
ws.close();
