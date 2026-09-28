import { readFileSync } from "node:fs";
import { execFileSync } from "node:child_process";

const PORT = 9222;
const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
let tab = list.find((t) => t.type === "page" && String(t.url).includes("8699357"));
if (!tab) throw new Error("no g-portal tab — open server 8699357 in Brave agent");

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
await send("Page.navigate", { url: "https://www.g-portal.com/eur/server/minecraft-ram/8699357" });
await sleep(6000);
await send("Runtime.evaluate", {
  expression: "window.scrollTo(0, document.body.scrollHeight)",
  returnByValue: true,
});
await sleep(1500);

const r = await send("Runtime.evaluate", {
  expression: `(() => {
    const a = [...document.querySelectorAll('a')].find(x => (x.innerText||'').includes('ftp://'));
    return a ? a.href : null;
  })()`,
  returnByValue: true,
});
ws.close();

const ftpBase = r.result?.value;
if (!ftpBase) throw new Error("FTP link not found on G-Portal");

const base = ftpBase.replace(/\/$/, "");
const scripts = `${base}/plugins/Skript/scripts`;
const local = "C:\\Users\\MerelyMe\\Documents\\MerelyMeSMP\\poi-refill.sk";

execFileSync("curl.exe", ["-s", "--ftp-pasv", "-T", local, `${scripts}/poi-refill.sk`], {
  stdio: "inherit",
});
console.log("uploaded poi-refill.sk bytes", readFileSync(local).length);

const old = [
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
const stubDir = "C:\\Users\\MerelyMe\\Documents\\MerelyMeSMP\\poi-stubs";

for (const f of old) {
  const stub = `${stubDir}\\${f}`;
  execFileSync("curl.exe", ["-s", "--ftp-pasv", "-T", stub, `${scripts}/${f}`], {
    stdio: "inherit",
  });
  console.log("stubbed", f);
}

console.log("POI deploy done — run /sk reload poi-refill in-game");
