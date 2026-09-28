import { execFileSync } from "node:child_process";
import { readdirSync, statSync } from "node:fs";
import { join } from "node:path";

const PORT = 9222;
const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
let tab = list.find((t) => t.type === "page" && String(t.url).includes("8699357"));
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
const send = (m, p = {}) =>
  new Promise((res, rej) => {
    pending.set(id, { resolve: res, reject: rej });
    ws.send(JSON.stringify({ id, method: m, params: p }));
    id++;
  });
await send("Page.enable");
await send("Page.navigate", { url: "https://www.g-portal.com/eur/server/minecraft-ram/8699357" });
await new Promise((r) => setTimeout(r, 8000));
await send("Runtime.evaluate", { expression: "window.scrollTo(0, document.body.scrollHeight)", returnByValue: true });
await new Promise((r) => setTimeout(r, 2000));
const r = await send("Runtime.evaluate", {
  expression: `(() => { const a = [...document.querySelectorAll('a')].find(x => (x.innerText||'').includes('ftp://')); return a ? a.href : null; })()`,
  returnByValue: true,
});
ws.close();
const base = r.result?.value?.replace(/\/$/, "");
if (!base) throw new Error("no ftp");

const root = "C:/Users/MerelyMe/Documents/MerelyMeSMP/azauction";
const uploads = [
  [`${root}/AzAuctions.jar`, "/plugins/AzAuctions.jar"],
  [`${root}/config.yml`, "/plugins/AzAuctions/config.yml"],
  [`${root}/messages.yml`, "/plugins/AzAuctions/messages.yml"],
  ["C:/Users/MerelyMe/Documents/MerelyMeSMP/ah-perms.sk", "/plugins/Skript/scripts/ah-perms.sk"],
];
for (const f of readdirSync(`${root}/gui`)) {
  uploads.push([`${root}/gui/${f}`, `/plugins/AzAuctions/gui/${f}`]);
}
for (const [local, remote] of uploads) {
  execFileSync("curl.exe", ["-s", "--ftp-pasv", "-T", local, base + remote], { stdio: "inherit" });
  console.log("ok", remote);
}
for (const [from, to] of [
  ["/plugins/SKAuction.jar", "/plugins/SKAuction.jar.off"],
  ["/plugins/CrazyAuctions.jar", "/plugins/CrazyAuctions.jar.off"],
  ["/plugins/CrazyAuctions.jar.disabled", "/plugins/CrazyAuctions.jar.off2"],
]) {
  try {
    execFileSync("curl.exe", ["-s", "--ftp-pasv", "-Q", `RNFR ${from}`, "-Q", `RNTO ${to}`, base], { stdio: "pipe" });
    console.log("renamed", from);
  } catch {
    console.log("skip", from);
  }
}
