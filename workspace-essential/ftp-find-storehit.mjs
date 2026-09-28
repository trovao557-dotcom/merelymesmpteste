import { execFileSync } from "node:child_process";
import { writeFileSync, readFileSync, mkdirSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";

const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
let tab = list.find((t) => String(t.url).includes("8699357") && !String(t.url).includes("console"));
if (!tab) tab = list.find((t) => String(t.url).includes("g-portal.com"));
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

// navigate to overview if needed
await send("Page.navigate", { url: "https://www.g-portal.com/eur/server/minecraft-ram/8699357" });
await new Promise((r) => setTimeout(r, 6000));

const r = await send("Runtime.evaluate", {
  expression: `(() => {
    const ftps = [...document.querySelectorAll('a')].map(a=>a.href).filter(h=>/^ftp:/i.test(h||''));
    return ftps.find(h => h && !h.includes('***')) || null;
  })()`,
  returnByValue: true,
});
const ftp = r.result?.value;
if (!ftp) {
  console.log("NO_FTP", tab.url);
  ws.close();
  process.exit(2);
}
const base = ftp.replace(/\/$/, "");
ws.close();

// Grep STOREHIT via downloading likely configs
const targets = [
  "plugins/Tip4serv/config.yml",
  "plugins/SKInfo/settings.yml",
  "plugins/SKAnnounce/settings.yml",
  "plugins/TAB/animations.yml",
  "plugins/SKCommands/settings.yml",
  "plugins/DeluxeMenus/gui_menus/store.yml",
  "server.properties",
];
const out = join(tmpdir(), "mm-search");
mkdirSync(out, { recursive: true });

for (const t of targets) {
  try {
    const local = join(out, t.replace(/\//g, "_"));
    execFileSync("curl.exe", ["-sS", "--ftp-pasv", "-o", local, `${base}/${t}`], { stdio: "pipe" });
    const txt = readFileSync(local, "utf8");
    if (/STOREHIT|StoreHit|HIT.?SMP|storehit|tip4serv|merelymesmpstore/i.test(txt)) {
      console.log("MATCH", t);
      const lines = txt.split(/\r?\n/).filter((l) => /STOREHIT|StoreHit|storehit|tip4serv|merelymesmpstore|store/i.test(l));
      console.log(lines.slice(0, 15).join("\n"));
    } else {
      console.log("ok-no-hit", t, txt.length);
    }
  } catch (e) {
    console.log("miss", t);
  }
}

// list Tip4serv + SKBounty
for (const dir of ["plugins/Tip4serv/", "plugins/SKBounty/", "plugins/SKInfo/"]) {
  try {
    const listing = execFileSync("curl.exe", ["-sS", "--ftp-pasv", `${base}/${dir}`], { encoding: "utf8" });
    console.log("DIR", dir, listing.split(/\r?\n/).filter(Boolean).slice(0, 15).join(" | "));
  } catch {
    console.log("no dir", dir);
  }
}

console.log("FTP_OK");
