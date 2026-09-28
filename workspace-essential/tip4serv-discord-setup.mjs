import { writeFileSync } from "node:fs";

const PORT = 9222;
const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();

async function cdp(tabId, expression) {
  const tab = list.find((t) => t.id === tabId) || list.find((t) => String(t.url || "").includes(tabId));
  if (!tab) throw new Error("no tab " + tabId);
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
  const r = await send("Runtime.evaluate", { expression, returnByValue: true, awaitPromise: true });
  ws.close();
  if (r.exceptionDetails) throw new Error(r.exceptionDetails.text || "eval fail");
  return r.result.value;
}

// 1) Tip4Serv modal: get /tiplink payload
const serversTab = list.find((t) => String(t.url).includes("tip4serv.com/dashboard/my-servers"));
const modalInfo = await cdp(
  serversTab.id,
  `(() => {
    const modal = [...document.querySelectorAll('.modal')].find(m => getComputedStyle(m).display !== 'none' && m.innerText.includes('DISCORD'));
    if (!modal) return { err: 'no modal' };
    const inputs = [...modal.querySelectorAll('input,textarea,code,pre')].map(el => ({
      tag: el.tagName,
      v: (el.value || el.textContent || '').trim(),
      nearby: (el.closest('div')?.innerText || '').slice(0, 120)
    }));
    return { text: modal.innerText.slice(0, 1200), inputs };
  })()`
);
console.log("MODAL", JSON.stringify(modalInfo, null, 2).slice(0, 2000));

// 2) OAuth continue
const oauthTab = list.find((t) => String(t.url).includes("client_id=1023897726301249628"));
if (oauthTab) {
  const o = await cdp(
    oauthTab.id,
    `(() => {
      const b = [...document.querySelectorAll('button,a')].find(x => /Continuar|Continue|Authorize|Autorizar/i.test(x.innerText||''));
      if (b) { b.click(); return 'clicked ' + b.innerText.trim(); }
      return { url: location.href, text: (document.body.innerText||'').slice(0,800) };
    })()`
  );
  console.log("OAUTH", o);
}

// 3) Products: find real edit URLs from onclick / data attrs
const prodTab = list.find((t) => String(t.url).includes("my-products"));
const products = await cdp(
  prodTab.id,
  `(() => {
    return [...document.querySelectorAll('tr[class*=product_]')].map(tr => {
      const id = tr.id;
      const title = (tr.innerText || '').replace(/\\s+/g, ' ').trim().slice(0, 80);
      const hrefs = [...tr.querySelectorAll('a')].map(a => a.href);
      const onclick = tr.getAttribute('ondblclick') || '';
      const editBtn = [...tr.querySelectorAll('*')].find(el => /edit|pencil|fa-pencil|fa-edit/i.test(el.className + el.innerHTML));
      return { id, title, hrefs, onclick, editHtml: editBtn ? editBtn.outerHTML.slice(0, 150) : null };
    });
  })()`
);
console.log("PRODUCTS", JSON.stringify(products, null, 2));

writeFileSync(
  "C:/Users/MerelyMe/Documents/MerelyMeSMP/_tip4serv/discord-connect-notes.json",
  JSON.stringify(
    {
      note: "Do not store API keys here",
      commandHint: "/tiplink",
      botInvite: "client_id=1023897726301249628",
      products: (products || []).map((p) => ({ id: p.id, title: p.title })),
      modalText: modalInfo?.text?.replace(/23255s[a-f0-9.]+/gi, "[REDACTED_KEY]"),
    },
    null,
    2
  )
);
console.log("notes written");
