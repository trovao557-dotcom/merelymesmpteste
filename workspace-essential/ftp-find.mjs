const tabId = "74E6C6925C8D59073CF8667C69B44581";
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
    ws.send(JSON.stringify({ id: i, method, params }));
  });

const expr = `(() => {
  const html = document.documentElement.innerHTML;
  const ftps = html.match(/ftp:\\/\\/[^"'\\s<>]+/gi) || [];
  // find password field near FTP
  const allText = document.body.innerText || '';
  // try clicking eye / show buttons
  const buttons = [...document.querySelectorAll('button, [role=button], svg, i')];
  let clicked = 0;
  for (const b of buttons) {
    const label = ((b.getAttribute('aria-label')||'') + (b.title||'') + (b.className||'')).toLowerCase();
    if (/eye|reveal|show|visibility|password|senha/.test(label)) {
      try { b.click(); clicked++; } catch(e) {}
    }
  }
  return { ftps: ftps.slice(0,8).map(f => f.replace(/:[^@/]+@/, ':***@')), clicked, hasStars: allText.includes('●') };
})()`;

const r = await send("Runtime.evaluate", { expression: expr, returnByValue: true });
console.log(JSON.stringify(r.result?.value, null, 2));

await new Promise((x) => setTimeout(x, 1000));

const r2 = await send("Runtime.evaluate", {
  expression: `(() => {
    const ftps = [...document.querySelectorAll('a')].map(a=>a.href).filter(h=>/^ftp:/i.test(h||''));
    // also check clipboard buttons / copy attributes
    const attrs = [...document.querySelectorAll('[data-clipboard-text],[data-copy],[data-value]')].map(e => e.getAttribute('data-clipboard-text')||e.getAttribute('data-copy')||e.getAttribute('data-value'));
    return { ftps: ftps.map(f => ({masked:f.includes('***'), len:f.length, host:(f.match(/@([^:/]+)/)||[])[1]})), attrs: attrs.filter(Boolean).map(a => a.slice(0,40)) };
  })()`,
  returnByValue: true,
});
console.log(JSON.stringify(r2.result?.value, null, 2));
ws.close();
