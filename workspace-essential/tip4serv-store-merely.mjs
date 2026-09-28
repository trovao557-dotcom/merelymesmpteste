const tabId = "4816642A23A9AB118A4708F421438871";
const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab = list.find((t) => t.id === tabId) || list.find((t) => String(t.url).includes("my-settings"));
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

const setVal = (name, val) => `(() => {
  const el = document.querySelector('input[name="${name}"],textarea[name="${name}"]');
  if (!el) return 'missing:${name}';
  const proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
  const setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
  setter && setter.call(el, ${JSON.stringify(val)});
  el.dispatchEvent(new Event('input', {bubbles:true}));
  el.dispatchEvent(new Event('change', {bubbles:true}));
  return 'ok:${name}';
})()`;

const updates = [
  ["title", "MerelyMeSMP Store"],
  ["sub_title", "Official store — merelymesmp.com"],
  ["meta_description", "MerelyMeSMP Pure Mace PvP Season 1. Store & join: merelymesmp.com · Discord: discord.gg/NEybDXCkhA"],
  ["server_name", "MerelyMeSMP"],
  ["server_ip", "merelymesmp.com"],
  ["discord", "https://discord.gg/NEybDXCkhA"],
];
for (const [n, v] of updates) {
  const r = await send("Runtime.evaluate", { expression: setVal(n, v), returnByValue: true });
  console.log(r.result?.value);
}
const save = await send("Runtime.evaluate", {
  expression: `(() => {
    const btn = [...document.querySelectorAll('button,input[type=submit]')].find(b => /save|salvar/i.test(b.innerText||b.value||''));
    btn?.click();
    return btn ? (btn.innerText||btn.value) : 'no';
  })()`,
  returnByValue: true,
});
console.log("save", save.result?.value);
await new Promise((r) => setTimeout(r, 2000));
const verify = await send("Runtime.evaluate", {
  expression: `(() => ({
    msg: (document.body.innerText||'').match(/success|saved|error/i)?.[0],
    sub: document.querySelector('input[name=sub_title]')?.value,
    meta: document.querySelector('input[name=meta_description],textarea[name=meta_description]')?.value?.slice(0,100)
  }))()`,
  returnByValue: true,
});
console.log(JSON.stringify(verify.result?.value));
ws.close();
