import { execFileSync } from "node:child_process";
import { writeFileSync, readFileSync, mkdirSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";

const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab =
  list.find((t) => String(t.url).includes("8699357") && !String(t.url).includes("console") && !String(t.url).includes("files")) ||
  list.find((t) => String(t.url).includes("8699357") && !String(t.url).includes("console"));
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

if (!String(tab.url).endsWith("8699357") && !String(tab.url).includes("8699357?")) {
  await send("Page.navigate", { url: "https://www.g-portal.com/eur/server/minecraft-ram/8699357" });
  await new Promise((r) => setTimeout(r, 5000));
}

const r = await send("Runtime.evaluate", {
  expression: `(() => {
    const ftps = [...document.querySelectorAll('a')].map(a=>a.href).filter(h=>/^ftp:/i.test(h||''));
    return ftps.find(h => h && !h.includes('***')) || null;
  })()`,
  returnByValue: true,
});
ws.close();
const ftp = r.result?.value;
if (!ftp) {
  console.log("NO_FTP");
  process.exit(2);
}
const base = ftp.replace(/\/$/, "");
const listing = execFileSync("curl.exe", ["-sS", "--ftp-pasv", `${base}/plugins/`], { encoding: "utf8" });
const grim = listing.split(/\r?\n/).filter((l) => /grim|anticheat|vulcan|matrix|spartan|themis|karhu|intave/i.test(l));
console.log("AC plugins:\n" + (grim.join("\n") || "none"));
console.log("---");
const msg = listing.split(/\r?\n/).filter((l) => /SKMsg|chat|filter|swear/i.test(l));
console.log("chat plugins:\n" + msg.join("\n"));

// list GrimAC folder if exists
for (const dir of ["GrimAC", "Grim", "grimac", "GrimAntiCheat"]) {
  try {
    const l = execFileSync("curl.exe", ["-sS", "--ftp-pasv", `${base}/plugins/${dir}/`], { encoding: "utf8" });
    if (l && !/550|Failed/i.test(l)) {
      console.log("DIR", dir);
      console.log(l);
      writeFileSync(join(tmpdir(), "grim-dir.txt"), dir);
    }
  } catch {}
}
