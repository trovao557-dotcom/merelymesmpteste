import { execFileSync } from "node:child_process";

const PORT = 9222;
const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
const tab = list.find((t) => t.type === "page" && String(t.url).includes("8699357"));
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
const r = await send("Runtime.evaluate", {
  expression: `(() => {
    const a = [...document.querySelectorAll('a')].find(x => (x.innerText||'').includes('ftp://'));
    return a ? a.href : null;
  })()`,
  returnByValue: true,
});
ws.close();

const base = r.result.value.replace(/\/$/, "");
const scripts = `${base}/plugins/Skript/scripts`;
const old = ["poi-loot-red.sk", "poi-cmds.sk", "poi-timer.sk"];

for (const f of old) {
  for (const args of [
    ["-s", "--ftp-pasv", "-X", "DELE", `${scripts}/${f}`],
    ["-s", "--ftp-pasv", "-Q", `DELE ${f}`, `${scripts}/`],
    ["-s", "--ftp-pasv", "--request", "DELE", `${scripts}/${f}`],
  ]) {
    try {
      const out = execFileSync("curl.exe", args, { encoding: "utf8", stdio: "pipe" });
      console.log("ok", f, args[1], args[2], out || "(empty)");
      break;
    } catch (e) {
      console.log("fail", f, args.join(" "), "status", e.status);
    }
  }
}
