import { execFileSync } from "node:child_process";
import { writeFileSync, readFileSync, mkdirSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";

const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab = list.find((t) => t.id === "40F71275E444B4E2C3D15A2C2DB2930C")
  || list.find((t) => String(t.url).includes("8699357") && !String(t.url).includes("console"));
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
const base = ftp.replace(/\/$/, "");
const out = join(tmpdir(), "mm-hitsearch");
mkdirSync(out, { recursive: true });

const dirs = [
  "plugins/SKHolograms/",
  "plugins/SKHolograms/holograms/",
  "plugins/DeluxeMenus/",
  "plugins/DeluxeMenus/gui_menus/",
  "plugins/TAB/",
  "plugins/SKCommands/",
  "plugins/SKInfo/",
];

for (const dir of dirs) {
  try {
    const listing = execFileSync("curl.exe", ["-sS", "--ftp-pasv", `${base}/${dir}`], { encoding: "utf8" });
    const files = listing
      .split(/\r?\n/)
      .map((l) => l.trim().split(/\s+/).pop())
      .filter((n) => n && /\.(yml|yaml|txt|sk)$/i.test(n));
    for (const f of files.slice(0, 40)) {
      const local = join(out, f);
      try {
        execFileSync("curl.exe", ["-sS", "--ftp-pasv", "-o", local, `${base}/${dir}${f}`], { stdio: "pipe" });
        const txt = readFileSync(local, "utf8");
        if (/STOREHIT|StoreHit|HIT.?SMP|storehit|SOTREHIT/i.test(txt)) {
          console.log("FOUND", dir + f);
          console.log(
            txt
              .split(/\r?\n/)
              .filter((l) => /STOREHIT|StoreHit|HIT|storehit|SOTRE/i.test(l))
              .slice(0, 10)
              .join("\n")
          );
        }
      } catch {}
    }
  } catch (e) {
    console.log("skip", dir);
  }
}
console.log("search done");
