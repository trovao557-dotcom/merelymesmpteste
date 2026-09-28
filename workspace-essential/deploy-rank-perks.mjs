import { readFileSync } from "node:fs";
import { execFileSync } from "node:child_process";

const files = [
  ["C:/Users/MerelyMe/Documents/MerelyMeSMP/enderchest.sk", "/plugins/Skript/scripts/enderchest.sk"],
  ["C:/Users/MerelyMe/Documents/MerelyMeSMP/craft.sk", "/plugins/Skript/scripts/craft.sk"],
  ["C:/Users/MerelyMe/Documents/MerelyMeSMP/grindstone.sk", "/plugins/Skript/scripts/grindstone.sk"],
  ["C:/Users/MerelyMe/Documents/MerelyMeSMP/anvil.sk", "/plugins/Skript/scripts/anvil.sk"],
  ["C:/Users/MerelyMe/Documents/MerelyMeSMP/fix-rank-perms.sk", "/plugins/Skript/scripts/fix-rank-perms.sk"],
  ["C:/Users/MerelyMe/Documents/EconomySMP/plugins/SKCommands/settings.yml", "/plugins/SKCommands/settings.yml"],
];

const PORT = 9222;
const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
let tab = list.find((t) => t.type === "page" && String(t.url).includes("8699357"));
if (!tab) throw new Error("no g-portal tab — open panel in Brave first");

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

const base = r.result?.value?.replace(/\/$/, "");
ws.close();
if (!base) throw new Error("FTP link not found on G-Portal");

for (const [local, remote] of files) {
  execFileSync("curl.exe", ["-s", "--ftp-pasv", "-T", local, base + remote], { stdio: "inherit" });
  console.log("uploaded", remote, "bytes", readFileSync(local).length);
}

console.log("Rank perks deployed — run /sk reload fix-rank-perms in console");
