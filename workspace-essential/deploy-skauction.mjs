import { execFileSync } from "node:child_process";
import { existsSync } from "node:fs";

const PORT = 9222;
const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
let tab = list.find((t) => t.type === "page" && String(t.url).includes("8699357"));
if (!tab) tab = list.find((t) => t.type === "page");
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
if (!String(tab.url).includes("8699357")) {
  await send("Page.navigate", { url: "https://www.g-portal.com/eur/server/minecraft-ram/8699357" });
  await new Promise((r) => setTimeout(r, 10000));
}
await send("Runtime.evaluate", { expression: "window.scrollTo(0, document.body.scrollHeight)", returnByValue: true });
await new Promise((r) => setTimeout(r, 3000));
const r = await send("Runtime.evaluate", {
  expression: `(() => { const a = [...document.querySelectorAll('a')].find(x => (x.innerText||'').includes('ftp://')); return a ? a.href : null; })()`,
  returnByValue: true,
});
ws.close();
const base = r.result?.value?.replace(/\/$/, "");
if (!base) throw new Error("no ftp");

const smp = "C:/Users/MerelyMe/Documents/EconomySMP/plugins/SKAuction";
const work = "C:/Users/MerelyMe/Documents/MerelyMeSMP";

const uploads = [
  [`${work}/ah-price-hover/AhPriceHover.jar`, "/plugins/AhPriceHover.jar"],
  [`${smp}/settings.yml`, "/plugins/SKAuction/settings.yml"],
  [`${smp}/menu-browse.yml`, "/plugins/SKAuction/menu-browse.yml"],
  [`${smp}/menu-mine.yml`, "/plugins/SKAuction/menu-mine.yml"],
  [`${smp}/menu-confirm.yml`, "/plugins/SKAuction/menu-confirm.yml"],
  [`${smp}/menu-history.yml`, "/plugins/SKAuction/menu-history.yml"],
  [`${work}/ah-perms.sk`, "/plugins/Skript/scripts/ah-perms.sk"],
];

for (const [local, remote] of uploads) {
  if (!existsSync(local)) {
    console.log("skip missing", local);
    continue;
  }
  execFileSync("curl.exe", ["-s", "--ftp-pasv", "-T", local, base + remote], { stdio: "inherit" });
  console.log("ok", remote);
}

const renames = [
  ["/plugins/SKAuction.jar.off", "/plugins/SKAuction.jar"],
  ["/plugins/SKAuction.jar.old", "/plugins/SKAuction.jar.bak"],
  ["/plugins/DonutAuctionHouse.jar", "/plugins/DonutAuctionHouse.jar.off"],
  ["/plugins/MerelyAhLore.jar", "/plugins/MerelyAhLore.jar.disabled"],
];
for (const [from, to] of renames) {
  try {
    execFileSync("curl.exe", ["-s", "--ftp-pasv", "-Q", `RNFR ${from}`, "-Q", `RNTO ${to}`, base], { stdio: "pipe" });
    console.log("renamed", from, "->", to);
  } catch {
    console.log("skip rename", from);
  }
}

console.log("deploy done — restart server");
