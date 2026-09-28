const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab = list.find((t) => t.url && t.url.includes("1544904353121308672"));
const ws = new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r, j) => {
  ws.onopen = r;
  ws.onerror = j;
});
let id = 1;
const pending = new Map();
const events = [];
ws.onmessage = (ev) => {
  const m = JSON.parse(ev.data);
  if (m.method) events.push(m.method + " " + JSON.stringify(m.params || {}).slice(0, 200));
  if (m.id == null) return;
  const p = pending.get(m.id);
  if (!p) return;
  pending.delete(m.id);
  m.error ? p.reject(Error(m.error.message)) : p.resolve(m.result);
};
const send = (method, params = {}) =>
  new Promise((resolve, reject) => {
    const i = id++;
    const t = setTimeout(() => {
      if (pending.has(i)) {
        pending.delete(i);
        reject(Error("to " + method));
      }
    }, 20000);
    pending.set(i, {
      resolve: (v) => {
        clearTimeout(t);
        resolve(v);
      },
      reject: (e) => {
        clearTimeout(t);
        reject(e);
      },
    });
    ws.send(JSON.stringify({ id: i, method, params }));
  });

await send("Page.enable");
await send("Page.setWindowOpenHandler", { behavior: { type: "allow" } }).catch(() => {});
await send("Runtime.evaluate", {
  expression: `window.__opens=[]; const o=window.open; window.open=function(...a){window.__opens.push(a); return o.apply(this,a);}; 'hooked'`,
});

const box = await send("Runtime.evaluate", {
  expression: `(() => {
    const b=[...document.querySelectorAll('button')].find(x=>/Link my server to Tip4Serv/i.test(x.innerText||''));
    if(!b) return null;
    const r=b.getBoundingClientRect();
    return {x:r.x+r.width/2,y:r.y+r.height/2,w:r.width,h:r.height, text:b.innerText};
  })()`,
  returnByValue: true,
});
console.log("box", box.result.value);
if (!box.result.value) {
  ws.close();
  process.exit(1);
}
const { x, y } = box.result.value;

// Try real mouse click
try {
  await send("Input.dispatchMouseEvent", { type: "mouseMoved", x, y });
  await send("Input.dispatchMouseEvent", { type: "mousePressed", x, y, button: "left", clickCount: 1 });
  await send("Input.dispatchMouseEvent", { type: "mouseReleased", x, y, button: "left", clickCount: 1 });
  console.log("mouse click ok");
} catch (e) {
  console.log("mouse fail", e.message);
  await send("Runtime.evaluate", {
    expression: `(() => { const b=[...document.querySelectorAll('button')].find(x=>/Link my server to Tip4Serv/i.test(x.innerText||'')); b.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true,view:window})); return true; })()`,
    returnByValue: true,
    userGesture: true,
  });
}

await new Promise((r) => setTimeout(r, 3000));
const opens = await send("Runtime.evaluate", {
  expression: `({opens: window.__opens, href: location.href, text:(document.body.innerText||'').match(/Link my server[\\s\\S]{0,500}|success|authorized|login/i)?.[0]})`,
  returnByValue: true,
});
console.log("after", opens.result.value);
console.log("events", events.slice(-10));
ws.close();

// List tabs again for new tip4serv oauth
const list2 = await (await fetch("http://127.0.0.1:9222/json/list")).json();
console.log(
  "tabs",
  list2
    .filter((t) => t.type === "page")
    .slice(0, 12)
    .map((t) => t.url.slice(0, 120))
);
