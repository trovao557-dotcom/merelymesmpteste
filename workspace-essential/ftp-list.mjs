import { execFileSync } from "node:child_process";

const PORT = 9222;
const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
const tab = list.find((t) => t.type === "page" && String(t.url).includes("8699357") && !String(t.url).includes("console"));
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
await send("Page.navigate", { url: "https://www.g-portal.com/eur/server/minecraft-ram/8699357" });
await new Promise((r) => setTimeout(r, 6000));
const r = await send("Runtime.evaluate", {
  expression: `(() => { window.scrollTo(0, document.body.scrollHeight); const a=[...document.querySelectorAll('a')].find(x=>(x.innerText||'').includes('ftp://')); return a?a.href:null; })()`,
  returnByValue: true,
});
ws.close();
const base = r.result.value.replace(/\/$/, "");
for (const path of ["/plugins/SKAuction/", "/plugins/"]) {
  try {
    const out = execFileSync("curl.exe", ["-s", "--ftp-pasv", "--list-only", `${base}${path}`], { encoding: "utf8" });
    console.log("==", path, "==");
    console.log(out);
  } catch (e) {
    console.log("fail", path, e.status);
  }
}
