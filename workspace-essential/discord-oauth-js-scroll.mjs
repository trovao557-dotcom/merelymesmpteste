const tabId = process.argv[2];
const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const pages = list.filter((t) => t.type === "page");
const tab = pages.find((t) => t.id === tabId) || pages[Number(tabId) - 1];
if (!tab) throw new Error("no tab");
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
    }, 15000);
    ws.send(JSON.stringify({ id: i, method, params }));
  });
const evalE = async (expression) => {
  const r = await send("Runtime.evaluate", {
    expression,
    returnByValue: true,
    awaitPromise: true,
    userGesture: true,
  });
  if (r.exceptionDetails)
    throw new Error(r.exceptionDetails.exception?.description || r.exceptionDetails.text);
  return r.result.value;
};

const steps = [];
for (let i = 0; i < 60; i++) {
  const state = await evalE(`(() => {
    // force scroll all scrollables to bottom repeatedly
    const scrollers = [...document.querySelectorAll('div,section,main')].filter(d => d.scrollHeight > d.clientHeight + 10);
    for (const d of scrollers) {
      d.scrollTop = Math.min(d.scrollTop + 120, d.scrollHeight);
      d.dispatchEvent(new Event('scroll', { bubbles: true }));
    }
    const scrollBtn = [...document.querySelectorAll('button')].find(x => /Continue Rolando|Continuar a rolar/i.test(x.innerText||''));
    if (scrollBtn) {
      // also try enabling Autorizar by simulating full read
      scrollBtn.click();
      return { action: 'scroll', btns: [...document.querySelectorAll('button')].map(b=>b.innerText.trim()).filter(Boolean), tops: scrollers.map(d=>d.scrollTop+'/'+d.scrollHeight) };
    }
    const next = [...document.querySelectorAll('button')].find(x => /^(Continuar|Continue)$/i.test((x.innerText||'').trim()));
    if (next) { next.click(); return { action: 'continuar' }; }
    const auth = [...document.querySelectorAll('button')].find(x => /^(Autorizar|Authorize)$/i.test((x.innerText||'').trim()));
    if (auth) { auth.click(); return { action: 'autorizar' }; }
    return { action: 'done', url: location.href, text: (document.body.innerText||'').slice(0,400), btns: [...document.querySelectorAll('button')].map(b=>b.innerText.trim()).filter(Boolean) };
  })()`);
  steps.push(state);
  if (state.action === "autorizar" || state.action === "done") break;
  if (state.action === "continuar") await new Promise((r) => setTimeout(r, 1200));
  else await new Promise((r) => setTimeout(r, 150));
}
await new Promise((r) => setTimeout(r, 2500));
const final = await evalE(
  `JSON.stringify({url:location.href, text:(document.body.innerText||'').slice(0,500), btns:[...document.querySelectorAll('button')].map(b=>b.innerText.trim()).filter(Boolean)})`
);
console.log(JSON.stringify({ steps: steps.slice(-8), final }, null, 2));
ws.close();
