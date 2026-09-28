const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab = list.find((t) => String(t.url).includes("tip4serv.com/dashboard/my-servers"));
const ws = new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r) => ws.addEventListener("open", r));
let id = 1;
const pending = new Map();
ws.addEventListener("message", (ev) => {
  const m = JSON.parse(String(ev.data));
  if (m.id && pending.has(m.id)) {
    const p = pending.get(m.id);
    pending.delete(m.id);
    m.error ? p.reject(new Error(m.error.message)) : p.resolve(m.result);
  }
});
const send = (method, params = {}) =>
  new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject });
    ws.send(JSON.stringify({ id, method, params }));
    id++;
  });

await send("Runtime.evaluate", {
  expression: `(() => { const btn=[...document.querySelectorAll('button')].find(x=>/Connect a Discord Server/i.test(x.innerText||'')); if(btn) btn.click(); return !!btn; })()`,
  returnByValue: true,
});
await new Promise((r) => setTimeout(r, 1500));

const info = await send("Runtime.evaluate", {
  expression: `(() => {
    const modal=[...document.querySelectorAll('.modal')].find(m=>getComputedStyle(m).display!=='none');
    if(!modal) return {err:'no modal'};
    const inputs=[...modal.querySelectorAll('input')].map(i => ({
      len: (i.value||'').length,
      starts: (i.value||'').slice(0,12),
      fullIfShort: (i.value||'').length < 40 ? i.value : null
    }));
    return { text: modal.innerText.slice(0,900), inputs };
  })()`,
  returnByValue: true,
});
console.log(JSON.stringify(info.result.value, null, 2));
ws.close();
