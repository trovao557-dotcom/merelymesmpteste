const tabId = "051AE27262C7721CB6117357EC79ADD2";
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

const setVal = (name, val) => `
  (() => {
    const el = document.querySelector('input[name="${name}"],textarea[name="${name}"]');
    if (!el) return 'missing:${name}';
    const proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
    const setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
    setter && setter.call(el, ${JSON.stringify(val)});
    el.dispatchEvent(new Event('input', {bubbles:true}));
    el.dispatchEvent(new Event('change', {bubbles:true}));
    return 'ok:${name}=' + el.value.slice(0,60);
  })()`;

const about = `MerelyMeSMP — Pure Mace PvP · Season 1
IP: merelymesmp.com
Discord: https://discord.gg/NEybDXCkhA
Store: https://merelymesmpstore.tip4serv.com
Twitch: https://twitch.tv/merelyme`;

const results = [];
for (const [n, v] of [
  ["server_name", "MerelyMeSMP"],
  ["server_ip", "merelymesmp.com"],
  ["discord", "https://discord.gg/NEybDXCkhA"],
  ["twitch", "https://www.twitch.tv/merelyme"],
  ["meta_description", "Pure Mace PvP – MerelyMeSMP Season 1. Join merelymesmp.com · Discord discord.gg/NEybDXCkhA · Store merelymesmpstore.tip4serv.com"],
  ["about_text", about],
  ["about_text_changed", "1"],
  ["title", "MerelyMeSMP Store"],
  ["sub_title", "Official Minecraft Store — Discord discord.gg/NEybDXCkhA"],
]) {
  const r = await send("Runtime.evaluate", { expression: setVal(n, v), returnByValue: true });
  results.push(r.result?.value);
}
console.log(results.join("\n"));

const save = await send("Runtime.evaluate", {
  expression: `(() => {
    const btn = [...document.querySelectorAll('button,input[type=submit]')].find(b => /save|salvar/i.test(b.innerText||b.value||''));
    btn?.click();
    return btn ? (btn.innerText||btn.value) : 'no save';
  })()`,
  returnByValue: true,
});
console.log("save", save.result?.value);
await new Promise((r) => setTimeout(r, 2500));
const verify = await send("Runtime.evaluate", {
  expression: `(() => ({
    msg: (document.body.innerText||'').match(/success|saved|error/i)?.[0],
    discord: document.querySelector('input[name=discord]')?.value,
    ip: document.querySelector('input[name=server_ip]')?.value,
    about: (document.querySelector('textarea[name=about_text]')?.value||'').slice(0,120)
  }))()`,
  returnByValue: true,
});
console.log(JSON.stringify(verify.result?.value, null, 2));
ws.close();
