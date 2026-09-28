import { readFileSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";

const PORT = 9222;
const tabId = process.argv[2];
const file = process.argv[3] || join(process.cwd(), "discord-finish-oauth.js");
const expr = readFileSync(file, "utf8");

const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
const tab =
  list.find((t) => t.id === tabId) ||
  list.find((t) => t.type === "page" && Number(tabId) === list.filter((x) => x.type === "page").indexOf(t) + 1) ||
  list.filter((t) => t.type === "page")[Number(tabId) - 1];
if (!tab) throw new Error("tab not found: " + tabId);

const ws = new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((res, rej) => {
  ws.addEventListener("open", res);
  ws.addEventListener("error", rej);
});
let nextId = 1;
const pending = new Map();
ws.addEventListener("message", (ev) => {
  const msg = JSON.parse(String(ev.data));
  if (msg.id == null) return;
  const p = pending.get(msg.id);
  if (!p) return;
  pending.delete(msg.id);
  msg.error ? p.reject(new Error(msg.error.message)) : p.resolve(msg.result);
});
function send(method, params = {}) {
  const id = nextId++;
  return new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject });
    setTimeout(() => {
      if (pending.has(id)) {
        pending.delete(id);
        reject(new Error("timeout " + method));
      }
    }, 60000);
    ws.send(JSON.stringify({ id, method, params }));
  });
}
await send("Runtime.enable").catch(() => {});
const r = await send("Runtime.evaluate", {
  expression: expr,
  returnByValue: true,
  awaitPromise: true,
  userGesture: true,
});
if (r.exceptionDetails) {
  console.error(r.exceptionDetails.exception?.description || r.exceptionDetails.text);
  process.exit(1);
}
console.log(JSON.stringify(r.result.value, null, 2));
ws.close();
