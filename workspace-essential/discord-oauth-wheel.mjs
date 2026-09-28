import { writeFileSync } from "node:fs";

const PORT = 9222;
const tabId = process.argv[2];
const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
const pages = list.filter((t) => t.type === "page");
const tab = pages.find((t) => t.id === tabId) || pages[Number(tabId) - 1];
if (!tab) throw new Error("tab not found");

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
    const t = setTimeout(() => {
      pending.delete(id);
      reject(new Error("timeout " + method));
    }, 20000);
    pending.set(id, {
      resolve: (v) => {
        clearTimeout(t);
        resolve(v);
      },
      reject: (e) => {
        clearTimeout(t);
        reject(e);
      },
    });
    ws.send(JSON.stringify({ id, method, params }));
  });
}
async function evalExpr(expression) {
  const r = await send("Runtime.evaluate", {
    expression,
    returnByValue: true,
    awaitPromise: true,
    userGesture: true,
  });
  if (r.exceptionDetails)
    throw new Error(r.exceptionDetails.exception?.description || r.exceptionDetails.text);
  return r.result.value;
}

await send("Runtime.enable").catch(() => {});
await send("Input.enable").catch(() => {});

// Find scrollable area center
const box = await evalExpr(`(() => {
  const scrollers = [...document.querySelectorAll('div')].filter(d => d.scrollHeight > d.clientHeight + 40);
  const best = scrollers.sort((a,b) => (b.scrollHeight-b.clientHeight) - (a.scrollHeight-a.clientHeight))[0];
  if (!best) return null;
  const r = best.getBoundingClientRect();
  return { x: Math.floor(r.left + r.width/2), y: Math.floor(r.top + r.height/2), sh: best.scrollHeight, ch: best.clientHeight, st: best.scrollTop, count: scrollers.length };
})()`);

const log = { box, steps: [] };
if (box) {
  for (let i = 0; i < 25; i++) {
    await send("Input.dispatchMouseEvent", {
      type: "mouseWheel",
      x: box.x,
      y: box.y,
      deltaX: 0,
      deltaY: 600,
    });
    await new Promise((r) => setTimeout(r, 120));
  }
}

for (let i = 0; i < 40; i++) {
  const state = await evalExpr(`(() => {
    const scrollers = [...document.querySelectorAll('div')].filter(d => d.scrollHeight > d.clientHeight + 20);
    scrollers.forEach(d => { d.scrollTop = d.scrollHeight; d.dispatchEvent(new Event('scroll', {bubbles:true})); });
    const scrollBtn = [...document.querySelectorAll('button')].find(x => /Continue Rolando|Continuar a rolar/i.test(x.innerText||''));
    if (scrollBtn) { scrollBtn.click(); return { action: 'scroll-click', btns: [...document.querySelectorAll('button')].map(b=>b.innerText.trim()).filter(Boolean) }; }
    const next = [...document.querySelectorAll('button')].find(x => /^(Continuar|Continue)$/i.test((x.innerText||'').trim()));
    if (next && !next.disabled) { next.click(); return { action: 'continuar' }; }
    const auth = [...document.querySelectorAll('button')].find(x => /^(Autorizar|Authorize)$/i.test((x.innerText||'').trim()));
    if (auth && !auth.disabled) { auth.click(); return { action: 'autorizar' }; }
    return { action: 'done', url: location.href, text: (document.body.innerText||'').slice(0,400), btns: [...document.querySelectorAll('button')].map(b=>b.innerText.trim()).filter(Boolean) };
  })()`);
  log.steps.push(state);
  if (state.action === "autorizar" || state.action === "done") break;
  if (state.action === "continuar") {
    await new Promise((r) => setTimeout(r, 1500));
    continue;
  }
  await new Promise((r) => setTimeout(r, 250));
}

await new Promise((r) => setTimeout(r, 2000));
log.final = await evalExpr(`JSON.stringify({url:location.href, text:(document.body.innerText||'').slice(0,500), btns:[...document.querySelectorAll('button')].map(b=>b.innerText.trim()).filter(Boolean)})`);
console.log(JSON.stringify(log, null, 2));
ws.close();
