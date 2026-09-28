const tabId = process.argv[2];
const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab = list.find((t) => t.id === tabId);
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
    const t = setTimeout(() => {
      if (pending.has(i)) {
        pending.delete(i);
        reject(Error("timeout " + method));
      }
    }, 8000);
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

await send("Page.bringToFront").catch(() => {});
await send("Input.setIgnoreInputEvents", { ignore: false }).catch(() => {});

// Single wheel bursts with short timeout - if Input broken, fail fast
let wheels = 0;
for (let i = 0; i < 20; i++) {
  try {
    await send("Input.dispatchMouseEvent", {
      type: "mouseWheel",
      x: 250,
      y: 300,
      deltaX: 0,
      deltaY: 500,
    });
    wheels++;
  } catch (e) {
    console.log("wheel fail", i, e.message);
    break;
  }
  await new Promise((r) => setTimeout(r, 100));
}
console.log("wheels", wheels);

const r = await send("Runtime.evaluate", {
  expression: `(() => {
    const el = [...document.querySelectorAll('div')].find(d => d.scrollHeight > d.clientHeight + 50);
    const btns = [...document.querySelectorAll('button')].map(b => ({t:b.innerText.trim(), d:b.disabled}));
    const auth = [...document.querySelectorAll('button')].find(x => /^(Autorizar|Authorize|Continuar|Continue)$/i.test((x.innerText||'').trim()) && !x.disabled);
    if (auth) { auth.click(); return {clicked: auth.innerText, st: el&&el.scrollTop, btns}; }
    return {clicked:null, st: el&&(el.scrollTop+'/'+el.scrollHeight), btns};
  })()`,
  returnByValue: true,
  userGesture: true,
});
console.log(JSON.stringify(r.result.value, null, 2));
await new Promise((x) => setTimeout(x, 2000));
const fin = await send("Runtime.evaluate", {
  expression: `JSON.stringify({url:location.href.slice(0,120), text:(document.body.innerText||'').slice(0,250), btns:[...document.querySelectorAll('button')].map(b=>b.innerText.trim()+':'+b.disabled)})`,
  returnByValue: true,
});
console.log(fin.result.value);
ws.close();
