const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab =
  list.find((t) => t.url && t.url.includes("tickettool.xyz/login")) ||
  list.find((t) => t.id === "BF40601819B22C5A865DDCF34A176E0E");
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
    setTimeout(() => {
      if (pending.has(i)) {
        pending.delete(i);
        reject(Error("timeout"));
      }
    }, 20000);
    ws.send(JSON.stringify({ id: i, method, params }));
  });
const r = await send("Runtime.evaluate", {
  expression: `(() => {
    const html = document.documentElement.outerHTML;
    const oauth = [...html.matchAll(/https:\\/\\/discord\\.com\\/[^"'\\s]+/g)].map(m=>m[0]).filter(u=>/oauth|authorize/i.test(u)).slice(0,10);
    const clientIds = [...html.matchAll(/client_id[=:]["']?(\\d+)/gi)].map(m=>m[1]).slice(0,10);
    const redirects = [...html.matchAll(/redirect[^"'\\s]{0,80}/gi)].map(m=>m[0]).slice(0,15);
    // Try vue app config
    const app = document.querySelector('#app');
    return { oauth, clientIds, redirects, url: location.href, btn: !!document.querySelector('button') };
  })()`,
  returnByValue: true,
});
console.log(JSON.stringify(r.result.value, null, 2));

// Click login with real mouse events via CDP
await send("Input.enable").catch(() => {});
const box = (
  await send("Runtime.evaluate", {
    expression: `(()=>{const b=[...document.querySelectorAll('button')].find(x=>/Login with discord/i.test(x.innerText||'')); if(!b) return null; const r=b.getBoundingClientRect(); return {x:Math.floor(r.left+r.width/2),y:Math.floor(r.top+r.height/2)};})()`,
    returnByValue: true,
  })
).result.value;
console.log("box", box);
if (box) {
  await send("Input.dispatchMouseEvent", {
    type: "mousePressed",
    x: box.x,
    y: box.y,
    button: "left",
    clickCount: 1,
  });
  await send("Input.dispatchMouseEvent", {
    type: "mouseReleased",
    x: box.x,
    y: box.y,
    button: "left",
    clickCount: 1,
  });
}
await new Promise((r) => setTimeout(r, 4000));
const after = (
  await send("Runtime.evaluate", {
    expression: `JSON.stringify({url:location.href, text:(document.body.innerText||'').slice(0,500)})`,
    returnByValue: true,
  })
).result.value;
console.log("after", after);
ws.close();

// List all tabs for new oauth popup
const list2 = await (await fetch("http://127.0.0.1:9222/json/list")).json();
console.log(
  "tabs",
  list2
    .filter((t) => t.type === "page")
    .slice(0, 8)
    .map((t) => t.url.slice(0, 120))
);
