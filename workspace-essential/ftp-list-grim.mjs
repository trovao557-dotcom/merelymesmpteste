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
const listing = execFileSync("curl.exe", ["-sS", "--ftp-pasv", `${base}/plugins/`], { encoding: "utf8" });
const names = listing
  .split(/\r?\n/)
  .map((l) => {
    const parts = l.trim().split(/\s+/);
    return parts[parts.length - 1];
  })
  .filter(Boolean);
const interesting = names.filter((n) => /grim|chatfilter|ChatFilter|Grim/i.test(n));
console.log("interesting names:", interesting);

const out = join(tmpdir(), "mm-grim");
mkdirSync(out, { recursive: true });

for (const name of interesting) {
  if (name.endsWith(".jar")) continue;
  try {
    const l = execFileSync("curl.exe", ["-sS", "--ftp-pasv", `${base}/plugins/${name}/`], { encoding: "utf8" });
    console.log("\n=== DIR", name, "===");
    console.log(l);
  } catch (e) {
    console.log("fail list", name, String(e.stderr || e.message).slice(0, 80));
  }
}

// try common config paths
const tries = [
  "plugins/GrimAC/config.yml",
  "plugins/GrimAC/messages.yml",
  "plugins/GrimAC/punishments.yml",
  "plugins/grimac/config.yml",
  "plugins/ChatFilter/config.yml",
  "plugins/ChatFilter/messages.yml",
  "plugins/ChatFilter/words.yml",
  "plugins/ChatFilter/filter.yml",
];
for (const t of tries) {
  try {
    const local = join(out, t.replace(/\//g, "_"));
    execFileSync("curl.exe", ["-sS", "--ftp-pasv", "-o", local, `${base}/${t}`], { stdio: "pipe" });
    const txt = readFileSync(local, "utf8");
    if (txt.length > 10 && !/550|Failed/i.test(txt.slice(0, 50))) {
      console.log("\nGOT", t, "len", txt.length);
      console.log(txt.slice(0, 600));
    }
  } catch {}
}
