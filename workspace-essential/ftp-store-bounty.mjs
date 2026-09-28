import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";

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
if (!ftp || ftp.includes("***")) {
  console.log("NO_FTP");
  process.exit(2);
}
const base = ftp.replace(/\/$/, "");
const eco = "C:\\Users\\MerelyMe\\Documents\\EconomySMP\\plugins";
const files = [
  [`${eco}\\SKInfo\\settings.yml`, "plugins/SKInfo/settings.yml"],
  [`${eco}\\SKAnnounce\\settings.yml`, "plugins/SKAnnounce/settings.yml"],
  [`${eco}\\TAB\\animations.yml`, "plugins/TAB/animations.yml"],
  [`${eco}\\SKPointShop\\categories\\ranks.yml`, "plugins/SKPointShop/categories/ranks.yml"],
  [`${eco}\\SKBounty\\settings.yml`, "plugins/SKBounty/settings.yml"],
  ["C:\\Users\\MerelyMe\\Documents\\MerelyMeSMP\\tip4serv-config-live.yml", "plugins/Tip4serv/config.yml"],
];
for (const [local, remote] of files) {
  try {
    execFileSync("curl.exe", ["-sS", "--ftp-pasv", "-T", local, `${base}/${remote}`], {
      stdio: ["ignore", "pipe", "pipe"],
    });
    console.log("OK", remote, readFileSync(local).length);
  } catch (e) {
    console.log("FAIL", remote, String(e.stderr || e.message).slice(0, 120).replace(ftp, "ftp://***"));
  }
}

// Also search whole plugins for STOREHIT via listing SKHolograms etc
try {
  const listing = execFileSync("curl.exe", ["-sS", "--ftp-pasv", `${base}/plugins/`], { encoding: "utf8" });
  const hit = listing.split(/\r?\n/).filter((l) => /hit|store/i.test(l));
  console.log("store-ish plugins", hit.slice(0, 30).join("\n"));
} catch {}
console.log("DONE");
