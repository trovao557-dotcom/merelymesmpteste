const TAB = "846D0A1EFB6158B071D94EF7A4C37A66";
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
        reject(Error("timeout"));
      }
    }, 60000);
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

const H = `
const setNative = (el, value) => {
  if (!el) return false;
  const proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype
    : el.tagName === 'SELECT' ? HTMLSelectElement.prototype
    : HTMLInputElement.prototype;
  const setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
  setter ? setter.call(el, value) : (el.value = value);
  el.dispatchEvent(new Event('input', { bubbles: true }));
  el.dispatchEvent(new Event('change', { bubbles: true }));
  return true;
};
const check = (el, on=true) => { if (!el) return; if (!!el.checked !== on) el.click(); };
const clickBack = () => { const b=[...document.querySelectorAll('button,a')].find(x=>/^Back$/i.test((x.innerText||'').trim())); if(b) b.click(); };
const clickCard = (re) => {
  const card=[...document.querySelectorAll('.card-box,.panel-select')].find(el => re.test((el.innerText||'').trim()) && (el.innerText||'').length < 140);
  if (card) { card.click(); return true; }
  return false;
};
`;

// Disable select menu (broken single option)
await evalE(`(() => { ${H} clickBack(); return 1; })()`);
await sleep(800);
await evalE(`(() => { ${H} return clickCard(/DropDown Style/i); })()`);
await sleep(1000);
await evalE(`(() => { ${H} check(document.querySelector('#selectMenu_enabled'), false); return document.querySelector('#selectMenu_enabled')?.checked; })()`);
console.log("selectMenu disabled");

// Forms: 3 questions
await evalE(`(() => { ${H} clickBack(); return 1; })()`);
await sleep(800);
await evalE(`(() => { ${H} return clickCard(/Form Options|^Forms/i); })()`);
await sleep(1000);

// Ensure 3 text questions
await evalE(`(() => {
  ${H}
  check(document.querySelector('#form_enabled'), true);
  check(document.querySelector('#form_attach'), true);
  setNative(document.querySelector('#form_title'), 'Open a Ticket');

  // Need 3 questions - add if missing
  const count = [...document.querySelectorAll('input[id^=question_title]')].filter(i => /question_title\\d+/.test(i.id)).length;
  return count;
})()`);

let qCount = await evalE(`(() => [...document.querySelectorAll('input[id^=question_title0],input[id^=question_title1],input[id^=question_title2]')].length)()`);
while (qCount < 3) {
  await evalE(`(() => { document.querySelector('#add_question')?.click(); return 1; })()`);
  await sleep(500);
  qCount = await evalE(`(() => [...document.querySelectorAll('input[id^=question_title0],input[id^=question_title1],input[id^=question_title2]')].length)()`);
}

const questions = await evalE(`(() => {
  ${H}
  // Q0 category
  setNative(document.querySelector('#question_title0'), 'What do you need help with?');
  setNative(document.querySelector('#question_placeholder0'), 'Unban Appeal / Report Player / Refund / General Support / Media Apply / Payment Support');
  setNative(document.querySelector('#question_min0'), '3');
  setNative(document.querySelector('#question_max0'), '40');
  check(document.querySelector('#question_required0'), true);
  check(document.querySelector('#question_multiline0'), false);

  // Q1 nick
  setNative(document.querySelector('#question_title1'), 'What is your Minecraft nick?');
  setNative(document.querySelector('#question_placeholder1'), 'Your in-game username');
  setNative(document.querySelector('#question_min1'), '2');
  setNative(document.querySelector('#question_max1'), '16');
  check(document.querySelector('#question_required1'), true);
  check(document.querySelector('#question_multiline1'), false);

  // Q2 what happened
  setNative(document.querySelector('#question_title2'), 'What happened?');
  setNative(document.querySelector('#question_placeholder2'), 'Describe your issue in detail (English only)');
  setNative(document.querySelector('#question_min2'), '10');
  setNative(document.querySelector('#question_max2'), '1000');
  check(document.querySelector('#question_required2'), true);
  check(document.querySelector('#question_multiline2'), true);

  return {
    q0: document.querySelector('#question_title0')?.value,
    q1: document.querySelector('#question_title1')?.value,
    q2: document.querySelector('#question_title2')?.value,
    enabled: document.querySelector('#form_enabled')?.checked
  };
})()`);
console.log("questions", questions);

// Panel message
await evalE(`(() => { ${H} clickBack(); return 1; })()`);
await sleep(800);
await evalE(`(() => { ${H} return clickCard(/Options for the message used to create tickets|^Panel\\b/i); })()`);
await sleep(1000);

await evalE(`(() => {
  ${H}
  setNative(document.querySelector('#embed_title'), 'MerelyMeSMP Support');
  setNative(document.querySelector('#embed_description'),
    'Click **Open Ticket** and fill the form in **English**.\\n\\n' +
    '**Categories**\\n' +
    '⚖️ Unban Appeal\\n' +
    '🚨 Report Player\\n' +
    '💸 Refund\\n' +
    '🎫 General Support\\n' +
    '🎬 Media Apply\\n' +
    '💳 Payment Support'
  );
  setNative(document.querySelector('#embed_color'), '#FEE75C');
  setNative(document.querySelector('#message_content'), '**Need help?** Open a ticket below.');
  setNative(document.querySelector('#panel_name'), 'MerelyMe Support');
  const roles = document.querySelector('#g_SupportTeamRoles');
  if (roles) {
    [...roles.options].forEach(o => { o.selected = /Ticket Staff|🛠️┃Mod|🧡┃Helper|⚡┃Admin|👑┃Owner/.test(o.text); });
    roles.dispatchEvent(new Event('change', { bubbles: true }));
  }
  const cat = document.querySelector('#g_CategoryOpen');
  if (cat) {
    [...cat.options].forEach(o => { o.selected = /SUPPORT/.test(o.text); });
    cat.dispatchEvent(new Event('change', { bubbles: true }));
  }
  return document.querySelector('#embed_title')?.value;
})()`);

// Create ticket button label
await evalE(`(() => { ${H} clickBack(); return 1; })()`);
await sleep(600);
await evalE(`(() => { ${H} return clickCard(/^Buttons\\b|Quick access list of all buttons/i); })()`);
await sleep(800);
await evalE(`(() => {
  ${H}
  const edit = [...document.querySelectorAll('button,a')].find(x => /Edit Button/i.test(x.innerText||'') && x.closest('div') && /Create Ticket/i.test(x.closest('div')?.innerText||''));
  // first Edit Button is Create Ticket
  const first = [...document.querySelectorAll('button,a')].filter(x => /^Edit Button$/i.test((x.innerText||'').trim()))[0];
  if (first) first.click();
  return !!first;
})()`);
await sleep(600);
await evalE(`(() => {
  ${H}
  check(document.querySelector('#button_Enabled'), true);
  setNative(document.querySelector('#button_text'), 'Open Ticket');
  setNative(document.querySelector('#button_emoji'), '🎫');
  return document.querySelector('#button_text')?.value;
})()`);

await evalE(`(() => { const b=[...document.querySelectorAll('button')].find(x=>/^Save$/i.test(x.innerText.trim())); if(b) b.click(); return 'save'; })()`);
await sleep(2500);

// Send / update panel
await evalE(`(() => {
  ${H}
  const ch = document.querySelector('#sendChannel');
  if (ch) {
    [...ch.options].forEach(o => { if (/create-ticket/i.test(o.text)) { ch.value=o.value; o.selected=true; } });
    ch.dispatchEvent(new Event('change', {bubbles:true}));
  }
  const save=[...document.querySelectorAll('button')].find(x=>/^Save$/i.test(x.innerText.trim()));
  if (save) save.click();
  return ch ? [...ch.selectedOptions].map(o=>o.text) : null;
})()`);
await sleep(2000);
await evalE(`(() => { const b=[...document.querySelectorAll('button,a')].find(x=>/^Send$/i.test((x.innerText||'').trim())); if(b) b.click(); return !!b; })()`);
await sleep(1500);
await evalE(`(() => {
  ${H}
  const ch = document.querySelector('#sendChannel');
  if (ch) {
    [...ch.options].forEach(o => { if (/create-ticket/i.test(o.text)) { ch.value=o.value; o.selected=true; } });
    ch.dispatchEvent(new Event('change', {bubbles:true}));
  }
  const c=[...document.querySelectorAll('button')].find(b=>/^(Send|Confirm)$/i.test(b.innerText.trim()));
  if (c) c.click();
  return (document.body.innerText||'').slice(-400);
})()`);

await sleep(2000);
console.log("done text", await evalE(`(() => (document.body.innerText||'').slice(-500))()`));
console.log(
  "verify",
  await evalE(`(() => ({
    q0: document.querySelector('#question_title0')?.value,
    q1: document.querySelector('#question_title1')?.value,
    q2: document.querySelector('#question_title2')?.value,
    formOn: document.querySelector('#form_enabled')?.checked,
    selectOn: document.querySelector('#selectMenu_enabled')?.checked,
    btn: document.querySelector('#button_text')?.value
  }))()`)
);
ws.close();
