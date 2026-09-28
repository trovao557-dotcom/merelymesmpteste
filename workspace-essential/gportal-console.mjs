const tabId = "74E6C6925C8D59073CF8667C69B44581";
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
    pending.set(i, { resolve, reject });
    ws.send(JSON.stringify({ id: i, method, params }));
  });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// Navigate to console if needed
await send("Runtime.evaluate", {
  expression: `(() => {
    const links = [...document.querySelectorAll('a,button,[role=button],span')];
    const c = links.find(e => /console|consola|terminal/i.test((e.innerText||'')+(e.href||'')));
    if (c) { c.click(); return 'clicked:'+(c.innerText||c.href||'').slice(0,40); }
    // sidebar
    const side = [...document.querySelectorAll('nav a, aside a, .menu a')].find(e => /console|consola/i.test(e.innerText||e.href||''));
    if (side) { side.click(); return 'side:'+side.innerText; }
    return 'notfound url='+location.href;
  })()`,
  returnByValue: true,
});
await sleep(3000);
const state = await send("Runtime.evaluate", {
  expression: `(() => ({url:location.href, text:(document.body.innerText||'').slice(0,800)}))()`,
  returnByValue: true,
});
console.log(JSON.stringify(state.result?.value, null, 2));
ws.close();
