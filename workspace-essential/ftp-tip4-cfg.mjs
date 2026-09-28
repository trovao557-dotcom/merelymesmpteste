import { execFileSync } from "node:child_process";
import { writeFileSync, readFileSync, mkdirSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";

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
const out = join(tmpdir(), "mm-tip4cfg");
mkdirSync(out, { recursive: true });

const listing = execFileSync("curl.exe", ["-sS", "--ftp-pasv", `${base}/plugins/Tip4serv/`], {
  encoding: "utf8",
});
console.log(listing);

for (const name of listing.split(/\r?\n/).map((l) => l.split(/\s+/).pop()).filter((n) => n && /\.(yml|yaml|txt|json)$/i.test(n))) {
  const local = join(out, name);
  execFileSync("curl.exe", ["-sS", "--ftp-pasv", "-o", local, `${base}/plugins/Tip4serv/${name}`], {
    stdio: "pipe",
  });
  const txt = readFileSync(local, "utf8");
  console.log("===", name, "len", txt.length, "===");
  console.log(txt.slice(0, 1200));
}
