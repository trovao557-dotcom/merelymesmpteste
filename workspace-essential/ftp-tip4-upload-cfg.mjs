import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";

const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab = list.find((t) => String(t.url).includes("8699357") && !String(t.url).includes("console"));
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
  expression: `(() => {
    const ftps = [...document.querySelectorAll('a')].map(a=>a.href).filter(h=>/^ftp:/i.test(h||''));
    return ftps.find(h => h && !h.includes('***')) || null;
  })()`,
  returnByValue: true,
});
ws.close();
const ftp = r.result?.value;
const base = ftp.replace(/\/$/, "");
const local = "C:\\Users\\MerelyMe\\Documents\\MerelyMeSMP\\tip4serv-config-live.yml";
execFileSync("curl.exe", ["-sS", "--ftp-pasv", "-T", local, `${base}/plugins/Tip4serv/config.yml`], {
  stdio: "pipe",
});
console.log("uploaded Tip4serv/config.yml", readFileSync(local).length);
