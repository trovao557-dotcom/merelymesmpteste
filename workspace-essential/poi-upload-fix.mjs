import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";

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
const scripts = `${base}/plugins/Skript/scripts`;
const localRoot = "C:\\Users\\MerelyMe\\Documents\\MerelyMeSMP";
const files = [
  "poi-refill.sk",
  "poi-config.sk",
  "poi-utils.sk",
  "poi-loot-normal.sk",
  "poi-loot-red.sk",
  "poi-loot-red2.sk",
  "poi-loot-yellow.sk",
  "poi-scan.sk",
  "poi-scan2.sk",
  "poi-cmds.sk",
  "poi-timer.sk",
];

for (const f of files) {
  const local = `${localRoot}\\${f}`;
  try {
    execFileSync("curl.exe", ["-sS", "--ftp-pasv", "-T", local, `${scripts}/${f}`], {
      encoding: "utf8",
      stdio: ["ignore", "pipe", "pipe"],
    });
    console.log("OK", f, readFileSync(local).length, "bytes");
  } catch (e) {
    const err = (e.stderr || e.message || "").toString().slice(0, 200);
    console.log("FAIL", f, err.replace(ftp, "ftp://***"));
  }
}
console.log("DONE");
