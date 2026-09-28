const tabId = "56B8CB5FA13441BE8BC7C19C120D3938";
const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab = list.find((t) => t.id === tabId) || list.find((t) => String(t.url).includes("/console"));
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
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const prep = await send("Runtime.evaluate", {
  expression: `(() => {
    const input = document.querySelector('input[type=text], textarea, input:not([type=hidden])');
    const buttons = [...document.querySelectorAll('button')].map(b => b.innerText.trim()).filter(Boolean).slice(0,20);
    return {
      input: input ? {tag:input.tagName, ph:input.placeholder, id:input.id, cls:input.className.slice(0,80)} : null,
      buttons,
      text: (document.body.innerText||'').slice(0,500)
    };
  })()`,
  returnByValue: true,
});
console.log("prep", JSON.stringify(prep.result?.value, null, 2));

// Try to send command
const sent = await send("Runtime.evaluate", {
  expression: `(() => {
    const input = document.querySelector('input[type=text], textarea, input:not([type=hidden])')
      || [...document.querySelectorAll('input')].find(i => /command|cmd|console/i.test(i.placeholder+i.className+i.id));
    if (!input) return 'no input';
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')?.set
      || Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value')?.set;
    setter && setter.call(input, 'sk reload poi-refill.sk');
    input.dispatchEvent(new Event('input', {bubbles:true}));
    input.dispatchEvent(new Event('change', {bubbles:true}));
    // press Enter
    input.dispatchEvent(new KeyboardEvent('keydown', {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true}));
    input.dispatchEvent(new KeyboardEvent('keyup', {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true}));
    // click send
    const btn = [...document.querySelectorAll('button')].find(b => /send|enviar|submit|>|➤/i.test(b.innerText+b.className));
    btn && btn.click();
    return {ok:true, btn:btn?.innerText||null, val:input.value};
  })()`,
  returnByValue: true,
});
console.log("sent", JSON.stringify(sent.result?.value, null, 2));
await sleep(4000);
const after = await send("Runtime.evaluate", {
  expression: `(() => (document.body.innerText||'').match(/poi-refill|POI v5|Loaded|error|Can't understand|Reloaded/gi)?.slice(0,20) || (document.body.innerText||'').slice(-800))()`,
  returnByValue: true,
});
console.log("after", JSON.stringify(after.result?.value, null, 2));
ws.close();
