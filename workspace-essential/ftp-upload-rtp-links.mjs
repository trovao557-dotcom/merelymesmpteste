import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";

const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab =
  list.find((t) => String(t.url).includes("8699357") && !String(t.url).includes("console")) ||
  list.find((t) => String(t.url).includes("8699357"));
if (!tab) throw new Error("open g-portal tab");
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
if (!ftp || ftp.includes("***")) {
  console.log("NO_FTP");
  process.exit(2);
}
const base = ftp.replace(/\/$/, "");
const localRoot = "C:\\Users\\MerelyMe\\Documents\\EconomySMP\\plugins";
const files = [
  ["SKRtp/settings.yml", "SKRtp/settings.yml"],
  ["SKInfo/settings.yml", "SKInfo/settings.yml"],
  ["SKMod/settings.yml", "SKMod/settings.yml"],
  ["TAB/animations.yml", "TAB/animations.yml"],
  ["SKAnnounce/settings.yml", "SKAnnounce/settings.yml"],
  ["SKPointShop/categories/ranks.yml", "SKPointShop/categories/ranks.yml"],
];
for (const [local, remote] of files) {
  const path = `${localRoot}\\${local.replace(/\//g, "\\")}`;
  try {
    execFileSync("curl.exe", ["-sS", "--ftp-pasv", "-T", path, `${base}/plugins/${remote}`], {
      stdio: ["ignore", "pipe", "pipe"],
    });
    console.log("OK", remote, readFileSync(path).length);
  } catch (e) {
    console.log("FAIL", remote, String(e.stderr || e.message).slice(0, 150).replace(ftp, "ftp://***"));
  }
}
console.log("DONE");
