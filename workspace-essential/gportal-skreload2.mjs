const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab = list.find((t) => String(t.url).includes("/console"));
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

const info = await send("Runtime.evaluate", {
  expression: `(() => {
    const inputs = [...document.querySelectorAll('input,textarea')].map(i => ({
      tag: i.tagName, type: i.type, id: i.id, name: i.name, ph: i.placeholder,
      cls: (i.className||'').toString().slice(0,60), disabled: i.disabled, visible: !!(i.offsetParent || i.getClientRects().length)
    }));
    return inputs;
  })()`,
  returnByValue: true,
});
console.log(JSON.stringify(info.result?.value, null, 2));

// Try fill command field near ENVIAR
const sent = await send("Runtime.evaluate", {
  expression: `(() => {
    const enviar = [...document.querySelectorAll('button')].find(b => /ENVIAR/i.test(b.innerText||''));
    if (!enviar) return 'no enviar';
    // find nearest input
    let root = enviar.parentElement;
    for (let i=0;i<6 && root;i++) {
      const inp = root.querySelector('input:not([type=checkbox]):not([type=hidden]):not([type=password]), textarea');
      if (inp) {
        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value')?.set
          || Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype,'value')?.set;
        setter.call(inp, 'sk reload poi-refill.sk');
        inp.dispatchEvent(new Event('input',{bubbles:true}));
        inp.focus();
        enviar.click();
        return {filled:true, id:inp.id, ph:inp.placeholder, cls:String(inp.className).slice(0,80)};
      }
      root = root.parentElement;
    }
    return 'no input near enviar';
  })()`,
  returnByValue: true,
});
console.log("sent", JSON.stringify(sent.result?.value, null, 2));
ws.close();
