import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const out = "C:\\Users\\MerelyMe\\Documents\\MerelyMeSMP\\ac-configs";
const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab =
  list.find((t) => t.id === "40F71275E444B4E2C3D15A2C2DB2930C") ||
  list.find((t) => String(t.url).includes("8699357") && !String(t.url).includes("files") && !String(t.url).includes("console"));
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
  expression: `([...document.querySelectorAll('a')].map(a=>a.href).find(h=>/^ftp:/i.test(h||'')&&!h.includes('***')))`,
  returnByValue: true,
});
ws.close();
if (!r.result?.value) {
  console.log("NO_FTP");
  process.exit(2);
}
const base = r.result.value.replace(/\/$/, "");

// whitelist official links
const wlLocal = join(out, "plugins__ChatFilter__whitelisted.yml");
try {
  execFileSync("curl.exe", ["-sS", "--ftp-pasv", "-o", wlLocal, `${base}/plugins/ChatFilter/whitelisted.yml`], {
    stdio: "pipe",
  });
  let wl = readFileSync(wlLocal, "utf8");
  if (!/merelymesmp\.com/i.test(wl)) {
    wl +=
      "\n  merely_official:\n    Enabled: true\n    Regex: merelymesmp\\.com\n    AddedBy: MerelyMe\n  merely_discord:\n    Enabled: true\n    Regex: discord\\.gg/NEybDXCkhA\n    AddedBy: MerelyMe\n";
    writeFileSync(wlLocal, wl);
    console.log("whitelist updated");
  } else {
    console.log("whitelist already has merely");
  }
} catch (e) {
  console.log("whitelist skip");
}

const uploads = [
  [join(out, "plugins__GrimAC__punishments.yml"), "plugins/GrimAC/punishments.yml"],
  [join(out, "plugins__ChatFilter__config.yml"), "plugins/ChatFilter/config.yml"],
  [join(out, "plugins__ChatFilter__messages_en.properties"), "plugins/ChatFilter/messages_en.properties"],
  [join(out, "plugins__ChatFilter__wordFilters.yml"), "plugins/ChatFilter/wordFilters.yml"],
  [wlLocal, "plugins/ChatFilter/whitelisted.yml"],
];

for (const [local, remote] of uploads) {
  try {
    execFileSync("curl.exe", ["-sS", "--ftp-pasv", "-T", local, `${base}/${remote}`], {
      stdio: ["ignore", "pipe", "pipe"],
    });
    console.log("OK", remote, readFileSync(local).length);
  } catch (e) {
    console.log("FAIL", remote, String(e.stderr || e.message).slice(0, 80).replace(/ftp:\/\/[^@]+@/i, "ftp://***@"));
  }
}
console.log("DONE");
