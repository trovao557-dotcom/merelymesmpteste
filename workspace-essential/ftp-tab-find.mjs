import { execFileSync } from "node:child_process";
import { writeFileSync, readFileSync, mkdirSync } from "node:fs";
import { join } from "node:path";

const out = "C:\\Users\\MerelyMe\\Documents\\MerelyMeSMP\\tab-live";
mkdirSync(out, { recursive: true });

const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab =
  list.find((t) => String(t.url).includes("8699357") && !String(t.url).includes("files") && !String(t.url).includes("console")) ||
  list.find((t) => String(t.url).includes("g-portal.com/eur/server/minecraft-ram/8699357"));
if (!tab) throw new Error("no gportal");
const ws = new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r, j) => { ws.onopen = r; ws.onerror = j; });
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
if (!r.result?.value) { console.log("NO_FTP"); process.exit(2); }
const base = r.result.value.replace(/\/$/, "");

const listing = execFileSync("curl.exe", ["-sS", "--ftp-pasv", `${base}/plugins/TAB/`], { encoding: "utf8" });
console.log(listing);
const files = listing.split(/\r?\n/).map(l => l.trim().split(/\s+/).pop()).filter(n => n && /\.(yml|yaml|txt)$/i.test(n));
for (const f of files) {
  const local = join(out, f);
  execFileSync("curl.exe", ["-sS", "--ftp-pasv", "-o", local, `${base}/plugins/TAB/${f}`], { stdio: "pipe" });
  const txt = readFileSync(local, "utf8");
  if (/hit|store|STORE|smp/i.test(txt)) {
    const lines = txt.split(/\r?\n/).filter(l => /hit|store\.|STOREHIT|Store\.hit|storehit/i.test(l));
    console.log("===", f, "===");
    console.log(lines.slice(0, 40).join("\n") || "(match but no line?)");
  }
}
