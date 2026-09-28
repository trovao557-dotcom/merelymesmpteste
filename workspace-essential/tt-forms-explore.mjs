/**
 * Configure Ticket Tool: dropdown categories + English forms (nick / what happened)
 * Then update Discord vanity / ip-links to merelymesmp.com
 */
const TAB = "846D0A1EFB6158B071D94EF7A4C37A66";
const GUILD = "1534136666984419348";
const CREATE_TICKET = "1544904386587787334";
const SUPPORT_CAT = "1544904383106387968";

const CATEGORIES = [
  "Unban Appeal",
  "Report Player",
  "Refund",
  "General Support",
  "Media Apply",
  "Payment Support",
];

const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab = list.find((t) => t.id === TAB);
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
    }, 45000);
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
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const setNative = `
const setNative = (el, value) => {
  if (!el) return;
  const proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype
    : el.tagName === 'SELECT' ? HTMLSelectElement.prototype
    : HTMLInputElement.prototype;
  const setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
  setter ? setter.call(el, value) : (el.value = value);
  el.dispatchEvent(new Event('input', { bubbles: true }));
  el.dispatchEvent(new Event('change', { bubbles: true }));
};
`;

// Go back to panel menu if needed
await evalE(`(() => { const b=[...document.querySelectorAll('button,a')].find(x=>/^Back$/i.test((x.innerText||'').trim())); if(b) b.click(); return !!b; })()`);
await sleep(1500);

// Ensure we have a panel selected - create if needed
let panelState = await evalE(`(() => {
  const sel = [...document.querySelectorAll('select')].find(s => [...s.options].some(o => /Panel/i.test(o.text)));
  if (sel && sel.options.length > 1) {
    sel.selectedIndex = [...sel.options].findIndex(o => o.value);
    sel.dispatchEvent(new Event('change', {bubbles:true}));
    return {has:true, name: sel.options[sel.selectedIndex]?.text};
  }
  return {has:false};
})()`);
if (!panelState.has) {
  await evalE(`(() => { const b=[...document.querySelectorAll('button,a')].find(x=>/^Create$/i.test((x.innerText||'').trim())); if(b) b.click(); return !!b; })()`);
  await sleep(2000);
}

// Rename current panel to MerelyMe Support Hub
await evalE(`(() => {
  ${setNative}
  const nameInput = [...document.querySelectorAll('input[type=text]')].find(i => i.placeholder === 'New Panel');
  if (nameInput) setNative(nameInput, 'MerelyMe Support');
  // support roles
  const roleSelect = [...document.querySelectorAll('select')].find(s => s.multiple && [...s.options].some(o => /Ticket Staff/.test(o.text)));
  if (roleSelect) {
    [...roleSelect.options].forEach(o => { o.selected = /🎫┃Ticket Staff|🛠️┃Mod|🧡┃Helper|⚡┃Admin|👑┃Owner/.test(o.text); });
    roleSelect.dispatchEvent(new Event('change', {bubbles:true}));
  }
  const catSelect = [...document.querySelectorAll('select')].find(s => s.multiple && [...s.options].some(o => /SUPPORT/.test(o.text)));
  if (catSelect) {
    [...catSelect.options].forEach(o => { o.selected = /🎫┃SUPPORT/.test(o.text); });
    catSelect.dispatchEvent(new Event('change', {bubbles:true}));
  }
  return true;
})()`);

// Open Forms
await evalE(`(() => {
  const card = [...document.querySelectorAll('.panel-select-card, .card-box, .panel-select')].find(el => /^Forms\\b/i.test((el.innerText||'').trim().split('\\n')[0]) || ((el.innerText||'').includes('Form Options') && (el.innerText||'').length < 80));
  if (card) { card.click(); return 'forms'; }
  const p = [...document.querySelectorAll('p')].find(x => /^Forms$/i.test(x.innerText.trim()));
  if (p) { (p.closest('.card-box')||p).click(); return 'forms-p'; }
  return 'no';
})()`);
await sleep(1500);

const formsPage = await evalE(`(() => ({text:(document.body.innerText||'').slice(0,2500), hash:location.hash}))()`);
console.log("forms page", formsPage);

// Enable form + add questions
const formSetup = await evalE(`(() => {
  ${setNative}
  const enable = document.querySelector('#form_enabled');
  if (enable && !enable.checked) { enable.click(); enable.checked = true; enable.dispatchEvent(new Event('change',{bubbles:true})); }
  const attach = document.querySelector('#form_attach');
  if (attach && !attach.checked) { attach.click(); attach.checked = true; attach.dispatchEvent(new Event('change',{bubbles:true})); }

  // Try find Add Question / Add button in forms
  const addBtns = [...document.querySelectorAll('button,a')].filter(b => /Add Question|Add Item|Add Field|\\+/i.test((b.innerText||'').trim()));
  return {
    enabled: !!document.querySelector('#form_enabled')?.checked,
    attach: !!document.querySelector('#form_attach')?.checked,
    addBtns: addBtns.map(b => b.innerText.trim()).slice(0,10),
    text: (document.body.innerText||'').slice(0,2000)
  };
})()`);
console.log("formSetup", JSON.stringify(formSetup, null, 2));

ws.close();
