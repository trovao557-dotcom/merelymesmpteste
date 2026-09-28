const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab =
  list.find((t) => String(t.url).includes("tip4serv.com/dashboard/product")) ||
  list.find((t) => String(t.url).includes("tip4serv.com/dashboard"));

async function withTab(fn) {
  const ws = new WebSocket(tab.webSocketDebuggerUrl);
  await new Promise((r, j) => {
    ws.addEventListener("open", r);
    ws.addEventListener("error", j);
  });
  let id = 1;
  const pending = new Map();
  ws.addEventListener("message", (ev) => {
    const m = JSON.parse(String(ev.data));
    if (m.id && pending.has(m.id)) {
      const p = pending.get(m.id);
      pending.delete(m.id);
      m.error ? p.reject(new Error(m.error.message)) : p.resolve(m.result);
    }
  });
  const send = (method, params = {}) =>
    new Promise((resolve, reject) => {
      pending.set(id, { resolve, reject });
      ws.send(JSON.stringify({ id, method, params }));
      id++;
    });
  try {
    return await fn(send);
  } finally {
    ws.close();
  }
}

const PRODUCTS = [
  { id: 3, name: "KNIGHT", role: "Knight" },
  { id: 2, name: "WARRIOR", role: "Warrior" },
  { id: 1, name: "MACER", role: "Macer" },
  { id: 0, name: "PRIME", role: "Prime" },
];

for (const p of PRODUCTS) {
  const result = await withTab(async (send) => {
    await send("Page.navigate", {
      url: `https://tip4serv.com/dashboard/product?id=${p.id}`,
    });
    await new Promise((r) => setTimeout(r, 3500));

    // Inspect UI for adding Discord server
    const state = await send("Runtime.evaluate", {
      expression: `(() => {
        const text = document.body.innerText || '';
        const links = [...document.querySelectorAll('a,button,div,li,span')].map(e => (e.innerText||'').trim()).filter(t => /Discord|Add |Choose a server|379102|MerelyMe SMP|role/i.test(t) && t.length < 90);
        return {
          title: document.querySelector('h1,h2,.title')?.innerText,
          hasDiscordServer: /#379102|Discord\\n/i.test(text),
          snippet: (text.match(/Server & Discord cmds[\\s\\S]{0,2200}/)||[])[0],
          links: [...new Set(links)].slice(0, 40)
        };
      })()`,
      returnByValue: true,
    });
    return state.result.value;
  });
  console.log("\\n===", p.name, "===");
  console.log(JSON.stringify(result, null, 2).slice(0, 2500));
}
