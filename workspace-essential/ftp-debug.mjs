const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab = list.find((t) => String(t.url).includes("8699357") && !String(t.url).includes("console"));
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
    return [...document.querySelectorAll('a')].map(a=>a.href).filter(h=>/^ftp:/i.test(h||'')).map(h => {
      const m = h.match(/^ftp:\\/\\/([^:]+):([^@]*)@([^:/]+):(\\d+)/);
      return {
        hasUser: !!m,
        user: m?.[1]?.slice(0,20),
        passLen: m?.[2]?.length || 0,
        passIsStars: m?.[2] === '***' || m?.[2] === '%2A%2A%2A',
        includesStars: h.includes('***'),
        host: m?.[3],
        port: m?.[4],
        len: h.length
      };
    });
  })()`,
  returnByValue: true,
});
console.log(JSON.stringify(info.result?.value, null, 2));
ws.close();
