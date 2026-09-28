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
    pending.set(i, { resolve, reject });
    setTimeout(() => {
      if (pending.has(i)) {
        pending.delete(i);
        reject(Error("timeout " + method));
      }
    }, 30000);
    ws.send(JSON.stringify({ id: i, method, params }));
  });
const evalE = async (expr) => {
  const r = await send("Runtime.evaluate", {
    expression: expr,
    returnByValue: true,
    awaitPromise: true,
    userGesture: true,
  });
  if (r.exceptionDetails) throw new Error(r.exceptionDetails.text);
  return r.result.value;
};

await send("Runtime.enable").catch(() => {});
await send("Page.bringToFront").catch(() => {});

const info = await evalE(`(() => {
  const btns = [...document.querySelectorAll('button')].map(b => {
    const r = b.getBoundingClientRect();
    return { text: b.innerText.trim(), disabled: b.disabled, x: Math.floor(r.x), y: Math.floor(r.y), w: Math.floor(r.width), h: Math.floor(r.height) };
  });
  const scrollers = [...document.querySelectorAll('*')].filter(d => d.scrollHeight > d.clientHeight + 30).slice(0,8).map(d => ({
    tag: d.tagName, cls: (d.className||'').toString().slice(0,60), sh: d.scrollHeight, ch: d.clientHeight, st: d.scrollTop,
    rect: (()=>{const r=d.getBoundingClientRect(); return {x:Math.floor(r.x),y:Math.floor(r.y),w:Math.floor(r.width),h:Math.floor(r.height)};})()
  }));
  const iframes = [...document.querySelectorAll('iframe')].map(f => f.src);
  return { btns, scrollers, iframes, title: document.title };
})()`);
console.log(JSON.stringify(info, null, 2));

// Real mouse wheel on scroller center
const sc = info.scrollers[0];
if (sc) {
  const x = sc.rect.x + sc.rect.w / 2;
  const y = sc.rect.y + sc.rect.h / 2;
  for (let i = 0; i < 30; i++) {
    await send("Input.dispatchMouseEvent", {
      type: "mouseWheel",
      x,
      y,
      deltaX: 0,
      deltaY: 400,
    });
    await new Promise((r) => setTimeout(r, 80));
  }
}

await new Promise((r) => setTimeout(r, 500));
const after = await evalE(`(() => ({
  btns: [...document.querySelectorAll('button')].map(b => b.innerText.trim()).filter(Boolean),
  tops: [...document.querySelectorAll('*')].filter(d => d.scrollHeight > d.clientHeight + 30).slice(0,5).map(d => d.scrollTop+'/'+d.scrollHeight)
}))()`);
console.log("after wheel", after);

// Click Autorizar / Continuar if present
const click = await evalE(`(() => {
  for (const label of ['Autorizar','Authorize','Continuar','Continue']) {
    const b=[...document.querySelectorAll('button')].find(x => (x.innerText||'').trim()===label);
    if (b && !b.disabled) { b.click(); return 'clicked:'+label; }
  }
  const s=[...document.querySelectorAll('button')].find(x => /Continue Rolando/i.test(x.innerText||''));
  if (s) {
    // try removing disabled from hidden authorize
    const all=[...document.querySelectorAll('button')];
    return 'still-scroll btns='+all.map(b=>b.innerText.trim()+':'+b.disabled).join('|');
  }
  return 'none';
})()`);
console.log(click);
await new Promise((r) => setTimeout(r, 2000));
console.log(await evalE(`JSON.stringify({url:location.href,text:(document.body.innerText||'').slice(0,300),btns:[...document.querySelectorAll('button')].map(b=>b.innerText.trim()).filter(Boolean)})`));
ws.close();
