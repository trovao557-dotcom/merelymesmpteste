import { execFileSync } from "node:child_process";

const PORT = 9222;
const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
const tab = list.find((t) => t.type === "page" && String(t.url).includes("8699357"));
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
  expression: `(() => { window.scrollTo(0, document.body.scrollHeight); const a=[...document.querySelectorAll('a')].find(x=>(x.innerText||'').includes('ftp://')); return a?a.href:null; })()`,
  returnByValue: true,
});
ws.close();
const base = (await send) ? null : null;
