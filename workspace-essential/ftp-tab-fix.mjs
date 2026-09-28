import { execFileSync } from "node:child_process";
import { readFileSync, copyFileSync } from "node:fs";

const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab =
  list.find((t) => String(t.url).includes("8699357") && !String(t.url).includes("files") && !String(t.url).includes("console"));
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
const r = await send("Runtime.evaluate", {
  expression: `([...document.querySelectorAll('a')].map(a=>a.href).find(h=>/^ftp:/i.test(h||'')&&!h.includes('***')))`,
  returnByValue: true,
});
ws.close();
const base = r.result.value.replace(/\/$/, "");
const local = "C:\\Users\\MerelyMe\\Documents\\MerelyMeSMP\\tab-live\\animations.yml";
// also sync local EconomySMP copy
try {
  copyFileSync(local, "C:\\Users\\MerelyMe\\Documents\\EconomySMP\\plugins\\TAB\\animations.yml");
} catch {}
execFileSync("curl.exe", ["-sS", "--ftp-pasv", "-T", local, `${base}/plugins/TAB/animations.yml`], {
  stdio: "pipe",
});
console.log("uploaded TAB animations.yml", readFileSync(local).length);
