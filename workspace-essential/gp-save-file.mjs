import { readFileSync } from "node:fs";

const PORT = 9222;
const editUrl = process.argv[2];
const filePath = process.argv[3];
if (!editUrl || !filePath) throw new Error("usage: gp-save-file.mjs <editUrl> <localPath>");

const content = readFileSync(filePath, "utf8").replace(/\r\n/g, "\n");
const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
const tab =
  list.find((t) => t.type === "page" && String(t.url).includes("files?edit=")) ||
  list.find((t) => t.type === "page" && String(t.url).includes("8699357")) ||
  list.find((t) => t.type === "page");
if (!tab) throw new Error("no tab");

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
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

await send("Page.enable");
await send("Runtime.enable");
await send("Page.navigate", { url: editUrl });
await sleep(3000);

const escaped = JSON.stringify(content);
const setRes = await send("Runtime.evaluate", {
  expression: `(() => new Promise(resolve => {
    const v = ${escaped};
    let n = 0;
    const tick = () => {
      const ta = document.querySelector('textarea.inputarea') || document.querySelector('textarea');
      if (ta) {
        const setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set;
        ta.focus();
        setter.call(ta, v);
        ta.dispatchEvent(new Event('input', { bubbles: true }));
        ta.dispatchEvent(new Event('change', { bubbles: true }));
        const models = window.monaco?.editor?.getModels?.() || [];
        if (models[0]) models[0].setValue(v);
        return resolve({ ok: ta.value.includes(v.slice(0, 40)), len: ta.value.length, expect: v.length });
      }
      if (++n > 40) return resolve({ ok: false, err: 'no textarea after wait' });
      setTimeout(tick, 500);
    };
    tick();
  }))()`,
  returnByValue: true,
  awaitPromise: true,
});
console.log("set", JSON.stringify(setRes.result?.value));
if (!setRes.result?.value?.ok) process.exit(1);

await sleep(500);
const saveRes = await send("Runtime.evaluate", {
  expression: `(() => {
    const b = [...document.querySelectorAll('button')].find(x => /GUARDAR|SAVE/i.test((x.innerText||'').trim()));
    if (!b) return { ok: false, err: 'no save btn' };
    b.click();
    return { ok: true, text: b.innerText.trim() };
  })()`,
  returnByValue: true,
});
console.log("save", JSON.stringify(saveRes.result?.value));
await sleep(4000);

await send("Runtime.evaluate", {
  expression: `(() => {
    const b = [...document.querySelectorAll('button')].find(x => /REFRESH/i.test((x.innerText||'').trim()));
    if (b) b.click();
    return { ok: !!b };
  })()`,
  returnByValue: true,
});
await sleep(4000);

const verifyRes = await send("Runtime.evaluate", {
  expression: `(() => {
    const v = document.querySelector('textarea')?.value || '';
    return { len: v.length, tail: v.slice(-120) };
  })()`,
  returnByValue: true,
});
console.log("verify", JSON.stringify(verifyRes.result?.value));
ws.close();
