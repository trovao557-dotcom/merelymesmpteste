import { readFileSync } from "node:fs";

const PORT = 9222;
const cmd = process.argv[2];
if (!cmd) throw new Error("usage: send-console-v2.mjs <command>");

const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
let tab = list.find((t) => t.type === "page" && String(t.url).includes("8699357/console"));
if (!tab) {
  tab = list.find((t) => t.type === "page" && String(t.url).includes("8699357"));
}
if (!tab) throw new Error("no tab");

const ws = new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((res, rej) => {
  ws.addEventListener("open", res);
  ws.addEventListener("error", rej);
});
let id = 1;
function send(method, params) {
  return new Promise((resolve, reject) => {
    const i = id++;
    const t = setTimeout(() => reject(new Error("timeout " + method)), 15000);
    const on = (ev) => {
      const msg = JSON.parse(String(ev.data));
      if (msg.id !== i) return;
      ws.removeEventListener("message", on);
      clearTimeout(t);
      msg.error ? reject(new Error(msg.error.message)) : resolve(msg.result);
    };
    ws.addEventListener("message", on);
    ws.send(JSON.stringify({ id: i, method, params }));
  });
}
await send("Runtime.enable");
if (!String(tab.url).includes("console")) {
  await send("Page.navigate", { url: "https://www.g-portal.com/eur/server/minecraft-ram/8699357/console" });
  await new Promise((r) => setTimeout(r, 5000));
}

const expr = `(() => {
  const inputs = [...document.querySelectorAll('input')];
  const input = inputs.find(i => i.name === 'console-input-message')
    || inputs.find(i => /command|comando|console/i.test((i.placeholder||'') + (i.name||'')))
    || inputs.find(i => i.type === 'text' && !i.hidden);
  if (!input) return { ok: false, err: 'no input', inputs: inputs.map(i => ({ name: i.name, type: i.type, ph: i.placeholder })) };
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
  setter.call(input, ${JSON.stringify(cmd)});
  input.dispatchEvent(new Event('input', { bubbles: true }));
  input.dispatchEvent(new Event('change', { bubbles: true }));
  const btn = [...document.querySelectorAll('button')].find(b => /ENVIAR|SEND|Send/i.test((b.innerText||'').trim()));
  if (!btn) return { ok: false, err: 'no send btn', cmd: input.value };
  btn.click();
  return { ok: true, cmd: input.value, btn: btn.innerText.trim() };
})()`;

const r = await send("Runtime.evaluate", { expression: expr, returnByValue: true, userGesture: true, awaitPromise: false });
console.log(JSON.stringify(r.result?.value || r, null, 2));
ws.close();
