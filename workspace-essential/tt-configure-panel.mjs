const tabId = "BF40601819B22C5A865DDCF34A176E0E";
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
    }, 30000);
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

const result = await evalE(`(() => {
  const setNative = (el, value) => {
    const proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    const setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
    setter ? setter.call(el, value) : (el.value = value);
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  };

  // Rename panel
  const nameInput = [...document.querySelectorAll('input[type=text]')].find(i => i.placeholder === 'New Panel');
  if (nameInput) setNative(nameInput, 'MerelyMe Support');

  // Support team roles multi-select
  const roleSelect = [...document.querySelectorAll('select')].find(s =>
    [...s.options].some(o => /Ticket Staff/.test(o.text)) && s.multiple
  );
  const picked = [];
  if (roleSelect) {
    [...roleSelect.options].forEach(o => {
      const want = /Ticket Staff|Mod|Helper|Admin|Owner/.test(o.text) && !/everyone|carl|Statbot|Ticket Tool|Jockie|Member|Prime|Media/.test(o.text);
      // Ticket Staff, Helper, Mod, Admin, Owner
      const want2 = /🎫┃Ticket Staff|🛠️┃Mod|🧡┃Helper|⚡┃Admin|👑┃Owner/.test(o.text);
      o.selected = want2;
      if (o.selected) picked.push(o.text);
    });
    roleSelect.dispatchEvent(new Event('change', { bubbles: true }));
  }

  // Category SUPPORT
  const catSelect = [...document.querySelectorAll('select')].find(s =>
    [...s.options].some(o => /SUPPORT/.test(o.text)) && s.multiple
  );
  const cats = [];
  if (catSelect) {
    [...catSelect.options].forEach(o => {
      o.selected = /🎫┃SUPPORT/.test(o.text);
      if (o.selected) cats.push(o.text);
    });
    catSelect.dispatchEvent(new Event('change', { bubbles: true }));
  }

  // Panel message content
  const msg = [...document.querySelectorAll('textarea')].find(t => t.placeholder === 'Message Content ');
  if (msg) setNative(msg, '**NEED HELP?**\\nOpen a ticket below for buys, appeals, bugs, or staff apps.\\nStaff will reply ASAP.');

  const title = [...document.querySelectorAll('input[type=text]')].find(i => i.placeholder === 'Title Text');
  if (title) setNative(title, 'MerelyMeSMP Support');

  const desc = [...document.querySelectorAll('textarea')].find(t => t.placeholder === 'Embed Description');
  if (desc) setNative(desc, 'Click the button to open a private ticket.\\n\\n• Buy / payment\\n• Ban / mute appeals\\n• Bugs & reports\\n• Staff applications');

  const color = [...document.querySelectorAll('input[type=text]')].find(i => i.placeholder === 'Embed Color');
  if (color) setNative(color, '#E11D48');

  return { picked, cats, hasName: !!nameInput, hasMsg: !!msg };
})()`);
console.log("configured", result);

// Click Save
const save = await evalE(`(() => {
  const b = [...document.querySelectorAll('button,a')].find(x => /^(Save)$/i.test((x.innerText||'').trim()));
  if (!b) return 'no-save';
  b.click();
  return 'saved';
})()`);
console.log(save);
await new Promise((r) => setTimeout(r, 2500));

// Click Send
const sendClick = await evalE(`(() => {
  const b = [...document.querySelectorAll('button,a')].find(x => /^(Send)$/i.test((x.innerText||'').trim()));
  if (!b) return {ok:false, btns:[...document.querySelectorAll('button')].map(x=>x.innerText.trim()).filter(Boolean).slice(0,30)};
  b.click();
  return {ok:true};
})()`);
console.log("send", sendClick);
await new Promise((r) => setTimeout(r, 2000));

const afterSend = await evalE(`(() => ({
  text: (document.body.innerText||'').slice(0,2500),
  selects: [...document.querySelectorAll('select')].map(s => ({
    multi: s.multiple,
    val: [...s.selectedOptions].map(o=>o.text).slice(0,8),
    opts: [...s.options].map(o=>o.text).slice(0,12)
  })).slice(0,8)
}))()`);
console.log(JSON.stringify(afterSend, null, 2));
ws.close();
