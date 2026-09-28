const tabId = process.argv[2];
const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab = list.find((t) => t.id === tabId);
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
        reject(Error("timeout " + method));
      }
    }, 15000);
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

await send("Page.bringToFront").catch(() => {});

for (let i = 0; i < 15; i++) {
  const r = await send("Runtime.evaluate", {
    expression: `(() => {
      const scrollers = [...document.querySelectorAll('div')].filter(d => d.scrollHeight > d.clientHeight + 40);
      for (const el of scrollers) el.scrollTop = Math.min(el.scrollTop + 400, el.scrollHeight);
      const btns = [...document.querySelectorAll('button')].map(b => ({t:(b.innerText||'').trim(), d:b.disabled}));
      const auth = [...document.querySelectorAll('button')].find(x => /^(Autorizar|Authorize)$/i.test((x.innerText||'').trim()) && !x.disabled);
      if (auth) { auth.click(); return {done:true, clicked:auth.innerText, btns}; }
      const cont = [...document.querySelectorAll('button')].find(x => /Continue|Continuar|Rolando/i.test(x.innerText||''));
      return {done:false, btns, contDisabled: cont?.disabled, st: scrollers.map(s => s.scrollTop+'/'+s.scrollHeight).slice(0,3)};
    })()`,
    returnByValue: true,
    userGesture: true,
  });
  console.log(i, JSON.stringify(r.result.value));
  if (r.result.value?.done) break;
  await new Promise((x) => setTimeout(x, 250));
}

await new Promise((x) => setTimeout(x, 2500));
const fin = await send("Runtime.evaluate", {
  expression: `({url:location.href, text:(document.body.innerText||'').slice(0,500)})`,
  returnByValue: true,
});
console.log("FIN", JSON.stringify(fin.result.value));
ws.close();
