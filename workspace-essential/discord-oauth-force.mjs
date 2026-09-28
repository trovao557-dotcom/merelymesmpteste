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
    }, 60000);
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

// Try CDP wheel with long timeout
let wheels = 0;
for (let i = 0; i < 25; i++) {
  try {
    await send("Input.dispatchMouseEvent", {
      type: "mouseWheel",
      x: 400,
      y: 350,
      deltaX: 0,
      deltaY: 800,
    });
    wheels++;
  } catch (e) {
    console.log("wheel err", e.message);
    break;
  }
  await new Promise((r) => setTimeout(r, 80));
}
console.log("wheels", wheels);

const forced = await send("Runtime.evaluate", {
  expression: `(() => {
    const scrollers = [...document.querySelectorAll('div')].filter(d => d.scrollHeight > d.clientHeight + 20);
    for (const d of scrollers) {
      d.scrollTop = d.scrollHeight;
      d.dispatchEvent(new WheelEvent('wheel', { deltaY: 2000, bubbles: true }));
      d.dispatchEvent(new Event('scroll', { bubbles: true }));
    }
    // unlock authorize if Discord only gates UI
    for (const b of document.querySelectorAll('button')) {
      const t = (b.innerText||'').trim();
      if (/Continue Rolando|Continuar a rolar|Autorizar|Authorize/i.test(t)) {
        b.disabled = false;
        b.removeAttribute('disabled');
        b.setAttribute('aria-disabled','false');
      }
    }
    const auth = [...document.querySelectorAll('button')].find(x => /^(Autorizar|Authorize)$/i.test((x.innerText||'').trim()));
    if (auth) { auth.click(); return {clicked:'auth', text:auth.innerText}; }
    const cont = [...document.querySelectorAll('button')].find(x => /Continue|Rolando|Continuar/i.test(x.innerText||''));
    if (cont && !cont.disabled) { cont.click(); return {clicked:'cont', text:cont.innerText, disabled:cont.disabled}; }
    return {
      clicked:null,
      btns:[...document.querySelectorAll('button')].map(b=>({t:(b.innerText||'').trim(),d:b.disabled})),
      tops: scrollers.map(d=>d.scrollTop+'/'+d.scrollHeight)
    };
  })()`,
  returnByValue: true,
  userGesture: true,
});
console.log("forced", JSON.stringify(forced.result.value));

await new Promise((r) => setTimeout(r, 2000));
const fin = await send("Runtime.evaluate", {
  expression: `({url:location.href, text:(document.body.innerText||'').slice(0,600), btns:[...document.querySelectorAll('button')].map(b=>({t:(b.innerText||'').trim(),d:b.disabled}))})`,
  returnByValue: true,
});
console.log("fin", JSON.stringify(fin.result.value));
ws.close();
