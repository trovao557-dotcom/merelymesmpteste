const TAB = "846D0A1EFB6158B071D94EF7A4C37A66";
const CREATE_TICKET = "1544904386587787334";

const CATEGORIES = [
  { display: "Unban Appeal", value: "unban_appeal", emoji: "⚖️", desc: "Appeal a ban or mute" },
  { display: "Report Player", value: "report_player", emoji: "🚨", desc: "Report rule-breaking" },
  { display: "Refund", value: "refund", emoji: "💸", desc: "Store / purchase refund" },
  { display: "General Support", value: "general_support", emoji: "🎫", desc: "General help" },
  { display: "Media Apply", value: "media_apply", emoji: "🎬", desc: "Creator / media application" },
  { display: "Payment Support", value: "payment_support", emoji: "💳", desc: "Payment issues" },
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

const helpers = `
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
const check = (el, on=true) => {
  if (!el) return;
  if (el.checked !== on) el.click();
};
`;

// Ensure on forms page
await evalE(`(() => {
  if (!location.hash.includes('form')) {
    const card = [...document.querySelectorAll('.card-box,.panel-select')].find(el => /Form Options|Forms/i.test(el.innerText||'') && el.innerText.length < 100);
    if (card) card.click();
  }
  return location.hash;
})()`);
await sleep(1000);

// Delete extra question 2 if exists
await evalE(`(() => { const b=document.querySelector('#form_question_delete2'); if(b) b.click(); return !!b; })()`);
await sleep(400);

// Set questions 0 and 1: nick + what happened
const q = await evalE(`(() => {
  ${helpers}
  check(document.querySelector('#form_enabled'), true);
  check(document.querySelector('#form_attach'), true);
  setNative(document.querySelector('#form_title'), 'Open a Ticket');

  // Q0 nick
  setNative(document.querySelector('#question_title0'), 'What is your Minecraft nick?');
  setNative(document.querySelector('#question_placeholder0'), 'Your in-game username');
  setNative(document.querySelector('#question_min0'), '2');
  setNative(document.querySelector('#question_max0'), '16');
  check(document.querySelector('#question_required0'), true);
  check(document.querySelector('#question_multiline0'), false);

  // Q1 what happened
  setNative(document.querySelector('#question_title1'), 'What happened?');
  setNative(document.querySelector('#question_placeholder1'), 'Describe your issue in detail (English only)');
  setNative(document.querySelector('#question_min1'), '10');
  setNative(document.querySelector('#question_max1'), '1000');
  check(document.querySelector('#question_required1'), true);
  check(document.querySelector('#question_multiline1'), true);

  return {
    t0: document.querySelector('#question_title0')?.value,
    t1: document.querySelector('#question_title1')?.value,
    form: document.querySelector('#form_title')?.value,
    enabled: document.querySelector('#form_enabled')?.checked
  };
})()`);
console.log("questions", q);

// Add select question for category via add_question_select
await evalE(`(() => { document.querySelector('#add_question_select')?.click(); return 'clicked-select-q'; })()`);
await sleep(800);

// Configure the select question (usually last / template fields)
const selQ = await evalE(`(() => {
  ${helpers}
  // Find highest question_titleN or use question_title without number for template
  const titles = [...document.querySelectorAll('input[id^=question_title]')];
  const last = titles[titles.length - 1];
  if (last) setNative(last, 'What do you need help with?');
  const ph = document.querySelector('#form_dropdown_placeholder') || document.querySelector('#question_placeholder' + (titles.length-1));
  if (document.querySelector('#form_dropdown_placeholder')) setNative(document.querySelector('#form_dropdown_placeholder'), 'Select a category');
  // required on select - form_dropdown might not have required; try question_requiredN
  const req = document.querySelector('#question_required' + (titles.length-1)) || document.querySelector('#question_required');
  check(req, true);
  return { lastId: last?.id, lastVal: last?.value, count: titles.length };
})()`);
console.log("selectQ", selQ);

// Add category items
for (const cat of CATEGORIES) {
  const r = await evalE(`(() => {
    ${helpers}
    const add = document.querySelector('#add_question_item');
    if (add) add.click();
    // fill template fields then they usually append
    setNative(document.querySelector('#form_item_display'), ${JSON.stringify(cat.display)});
    setNative(document.querySelector('#form_item_value'), ${JSON.stringify(cat.value)});
    setNative(document.querySelector('#form_item_description'), ${JSON.stringify(cat.desc)});
    setNative(document.querySelector('#form_item_emoji'), ${JSON.stringify(cat.emoji)});
    return { display: document.querySelector('#form_item_display')?.value };
  })()`);
  console.log("item", cat.display, r);
  await sleep(350);
}

// Also enable panel selectMenu as backup with same categories - go to dropdown style
await evalE(`(() => {
  const back = [...document.querySelectorAll('button,a')].find(x => /^Back$/i.test((x.innerText||'').trim()));
  if (back) back.click();
  return 'back';
})()`);
await sleep(1000);

await evalE(`(() => {
  const card = [...document.querySelectorAll('.card-box,.panel-select')].find(el => /DropDown Style/i.test(el.innerText||'') && el.innerText.length < 120);
  if (card) card.click();
  return !!card;
})()`);
await sleep(1000);

await evalE(`(() => {
  ${helpers}
  check(document.querySelector('#selectMenu_enabled'), true);
  setNative(document.querySelector('#selectMenu_placeholder'), 'Select a category to open a ticket');
  return { enabled: document.querySelector('#selectMenu_enabled')?.checked };
})()`);

// For select menu items - each needs a panel. Clone approach is heavy.
// Instead rely on FORM category select + single Create Ticket button.
// Disable selectMenu if it needs unique panels - keep form only.
await evalE(`(() => {
  ${helpers}
  // Prefer form-based category; disable selectMenu to avoid broken empty dropdown
  check(document.querySelector('#selectMenu_enabled'), false);
  return 'selectMenu off - using form category';
})()`);

// Panel message English
await evalE(`(() => {
  const back = [...document.querySelectorAll('button,a')].find(x => /^Back$/i.test((x.innerText||'').trim()));
  if (back) back.click();
  return 'back2';
})()`);
await sleep(1000);

await evalE(`(() => {
  const card = [...document.querySelectorAll('.card-box,.panel-select')].find(el => /^Panel\\b/i.test((el.innerText||'').trim().split('\\n')[0]) || ((el.innerText||'').includes('Options for the message used to create tickets') && el.innerText.length < 100));
  if (card) card.click();
  return !!card;
})()`);
await sleep(1000);

await evalE(`(() => {
  ${helpers}
  setNative(document.querySelector('#embed_title'), 'MerelyMeSMP Support');
  setNative(document.querySelector('#embed_description'), 'Need help? Select a category and fill the form.\\n\\n• Unban Appeal\\n• Report Player\\n• Refund\\n• General Support\\n• Media Apply\\n• Payment Support\\n\\nPlease answer in **English**.');
  setNative(document.querySelector('#embed_color'), '#FEE75C');
  setNative(document.querySelector('#message_content'), '**Support Tickets** — pick an option below.');
  // support roles + category
  const roleSelect = document.querySelector('#g_SupportTeamRoles');
  if (roleSelect) {
    [...roleSelect.options].forEach(o => { o.selected = /Ticket Staff|Mod|Helper|Admin|Owner/.test(o.text) && !/Member|Prime|Media|Statbot|carl|Ticket Tool|Jockie|everyone/.test(o.text); });
    roleSelect.dispatchEvent(new Event('change', {bubbles:true}));
  }
  const cat = document.querySelector('#g_CategoryOpen');
  if (cat) {
    [...cat.options].forEach(o => { o.selected = /SUPPORT/.test(o.text); });
    cat.dispatchEvent(new Event('change', {bubbles:true}));
  }
  return {
    title: document.querySelector('#embed_title')?.value,
    desc: document.querySelector('#embed_description')?.value?.slice(0,80)
  };
})()`);

// Ticket message with form vars
await evalE(`(() => {
  const back = [...document.querySelectorAll('button,a')].find(x => /^Back$/i.test((x.innerText||'').trim()));
  if (back) back.click();
  return 'back3';
})()`);
await sleep(800);
await evalE(`(() => {
  const card = [...document.querySelectorAll('.card-box,.panel-select')].find(el => /Ticket Message|Edit Ticket Message/i.test(el.innerText||'') && el.innerText.length < 120);
  // Messages card
  const msgCard = [...document.querySelectorAll('.card-box,.panel-select')].find(el => /^Messages\\b/i.test((el.innerText||'').trim().split('\\n')[0]));
  if (msgCard) msgCard.click();
  return !!msgCard;
})()`);
await sleep(800);

// Edit ticket message via Edit Ticket Message button from main
await evalE(`(() => {
  const back = [...document.querySelectorAll('button,a')].find(x => /^Back$/i.test((x.innerText||'').trim()));
  if (back) back.click();
  return 1;
})()`);
await sleep(500);

await evalE(`(() => {
  ${helpers}
  // Try set ticket message content fields if visible
  const areas = [...document.querySelectorAll('textarea')].filter(t => t.id === 'message_content' || t.placeholder === 'Message Content ');
  // Also try Edit Ticket Message
  const edit = [...document.querySelectorAll('button,a')].find(x => /Edit Ticket Message/i.test(x.innerText||''));
  if (edit) edit.click();
  return { edit: !!edit };
})()`);
await sleep(1000);

await evalE(`(() => {
  ${helpers}
  // After edit ticket message opens, set content
  const title = document.querySelector('#embed_title');
  const desc = document.querySelector('#embed_description');
  const content = document.querySelector('#message_content');
  if (title && document.body.innerText.includes('Ticket')) {
    setNative(content, 'Staff will be with you shortly.');
    setNative(title, 'New Ticket');
    setNative(desc, '**Category / Form answers**\\n{create.form.0}\\n{create.form.1}\\n{create.form.2}');
  }
  return {
    visible: (document.body.innerText||'').slice(0,500),
    desc: desc?.value?.slice(0,100)
  };
})()`);

// Save
await evalE(`(() => { const b=[...document.querySelectorAll('button')].find(x=>/^Save$/i.test(x.innerText.trim())); if(b) b.click(); return !!b; })()`);
await sleep(2500);
console.log("saved");

// Set send channel + send
const sendRes = await evalE(`(() => {
  ${helpers}
  const ch = document.querySelector('#sendChannel');
  if (ch) {
    [...ch.options].forEach(o => { if (/create-ticket/i.test(o.text)) { ch.value = o.value; o.selected = true; } });
    ch.dispatchEvent(new Event('change', {bubbles:true}));
  }
  const save = [...document.querySelectorAll('button')].find(x=>/^Save$/i.test(x.innerText.trim()));
  if (save) save.click();
  return { channel: ch ? [...ch.selectedOptions].map(o=>o.text) : null };
})()`);
console.log("pre-send", sendRes);
await sleep(2000);

await evalE(`(() => { const b=[...document.querySelectorAll('button,a')].find(x=>/^Send$/i.test((x.innerText||'').trim())); if(b) b.click(); return !!b; })()`);
await sleep(2000);
await evalE(`(() => {
  ${helpers}
  const ch = document.querySelector('#sendChannel');
  if (ch) {
    [...ch.options].forEach(o => { if (/create-ticket/i.test(o.text)) { ch.value = o.value; o.selected = true; } });
    ch.dispatchEvent(new Event('change', {bubbles:true}));
  }
  const confirm = [...document.querySelectorAll('button')].find(b => /^(Send|Confirm|OK)$/i.test(b.innerText.trim()));
  if (confirm) confirm.click();
  return (document.body.innerText||'').slice(-600);
})()`);

await sleep(2000);
const finalText = await evalE(`(() => (document.body.innerText||'').slice(-800))()`);
console.log("final", finalText);

// Verify form fields one more time
const verify = await evalE(`(() => ({
  form: document.querySelector('#form_title')?.value,
  q0: document.querySelector('#question_title0')?.value,
  q1: document.querySelector('#question_title1')?.value,
  enabled: document.querySelector('#form_enabled')?.checked,
  items: [...document.querySelectorAll('input[id^=form_item_display], input[id*=display]')].map(i=>i.value).filter(Boolean).slice(0,10)
}))()`);
console.log("verify", verify);

ws.close();
