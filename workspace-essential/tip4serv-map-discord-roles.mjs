const PRODUCTS = [
  { id: 3, roleMatch: /Knight/i, label: "KNIGHT" },
  { id: 2, roleMatch: /Warrior/i, label: "WARRIOR" },
  { id: 1, roleMatch: /Macer/i, label: "MACER" },
  { id: 0, roleMatch: /Prime/i, label: "PRIME" },
];

const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab = list.find((t) => String(t.url).includes("tip4serv.com/dashboard/product?id="))
  || list.find((t) => String(t.url).includes("tip4serv.com"));
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

for (const p of PRODUCTS) {
  console.log("\n===", p.label, "===");
  await send("Page.navigate", { url: `https://tip4serv.com/dashboard/product?id=${p.id}` });
  await new Promise((r) => setTimeout(r, 4000));

  const added = await send("Runtime.evaluate", {
    expression: `(() => {
      // already has discord section?
      if (document.querySelector('[data-server_id="379102"], .discord[data-server_id="379102"], #server_379102, [id*="379102"].server-panel')) {
        return {already:true};
      }
      const a = document.querySelector('a.discord_server#379102_${p.id}, a.discord_server[id^="379102_"]');
      if (!a) {
        // open servers modal first
        const open = [...document.querySelectorAll('a,button')].find(e => /Connect another server or API/i.test(e.innerText||''));
        open?.click();
      }
      const link = document.querySelector('a.discord_server#379102_${p.id}') || document.querySelector('a.discord_server[id^="379102_"]');
      if (!link) return {err:'no discord link', htmlHas: document.documentElement.innerHTML.includes('379102')};
      link.click();
      return {clicked: link.id, text: link.innerText};
    })()`,
    returnByValue: true,
  });
  console.log("add server", added.result.value);
  await new Promise((r) => setTimeout(r, 2000));

  const roles = await send("Runtime.evaluate", {
    expression: `(() => {
      const roleMatch = ${p.roleMatch};
      // find role selects/checkboxes for discord
      const checks = [...document.querySelectorAll('input[type=checkbox], select option, label')].map(el => ({
        tag: el.tagName,
        type: el.type,
        name: el.name,
        value: el.value,
        text: (el.innerText||el.parentElement?.innerText||'').trim().slice(0,80),
        checked: el.checked
      })).filter(x => /role|Warrior|Knight|Macer|Prime|Member|379102|discord/i.test(JSON.stringify(x)));
      // Tip4Serv discord role UI often uses select2 / multi select
      const selects = [...document.querySelectorAll('select')].map(s => ({
        name: s.name,
        opts: [...s.options].map(o => ({t:o.text, v:o.value, sel:o.selected})).slice(0,30)
      })).filter(s => /role|discord|379102/i.test(s.name+JSON.stringify(s.opts)));
      const discordSection = (document.body.innerText||'').match(/#379102[\\s\\S]{0,1500}|Discord roles[\\s\\S]{0,800}|Give a role[\\s\\S]{0,800}/i)?.[0];
      return {checks: checks.slice(0,40), selects, discordSection};
    })()`,
    returnByValue: true,
  });
  console.log("roles ui", JSON.stringify(roles.result.value, null, 2).slice(0, 3500));

  // Try select matching role
  const picked = await send("Runtime.evaluate", {
    expression: `(() => {
      const roleMatch = ${p.roleMatch};
      // checkbox labels
      for (const lab of document.querySelectorAll('label')) {
        if (roleMatch.test(lab.innerText||'')) {
          const inp = lab.querySelector('input') || document.getElementById(lab.htmlFor);
          if (inp) { inp.checked = true; inp.dispatchEvent(new Event('change',{bubbles:true})); return {via:'label', text:lab.innerText.slice(0,80)}; }
          lab.click();
          return {via:'label-click', text:lab.innerText.slice(0,80)};
        }
      }
      // select options
      for (const s of document.querySelectorAll('select')) {
        for (const o of s.options) {
          if (roleMatch.test(o.text||'')) {
            o.selected = true;
            s.value = o.value;
            s.dispatchEvent(new Event('change',{bubbles:true}));
            // select2
            if (window.jQuery) {
              try { jQuery(s).val(o.value).trigger('change'); } catch {}
            }
            return {via:'select', name:s.name, text:o.text, value:o.value};
          }
        }
      }
      // click role chips / list items
      const item = [...document.querySelectorAll('li, .role, .select2-results__option, span')].find(e => roleMatch.test(e.innerText||'') && (e.innerText||'').length < 60);
      if (item) { item.click(); return {via:'item', text:item.innerText}; }
      return {via:'none'};
    })()`,
    returnByValue: true,
  });
  console.log("picked", picked.result.value);

  // Also ensure storebought + keep lp command; fix warrior slug
  if (p.label === "WARRIOR") {
    await send("Runtime.evaluate", {
      expression: `(() => {
        const slug = document.querySelector('input[name=product_slug]');
        if (slug && /warriot/i.test(slug.value)) { slug.value = 'warrior-rank'; slug.dispatchEvent(new Event('input',{bubbles:true})); }
        // add storebought if missing
        const cmds = [...document.querySelectorAll('input[name="payment_command[378958][]"]')];
        const has = cmds.some(c => /storebought/i.test(c.value));
        if (!has) {
          // fill empty command slot or clone
          let empty = cmds.find(c => !c.value);
          if (!empty && cmds[0]) {
            // click add command if exists
            const add = [...document.querySelectorAll('a,button')].find(e => /Add command|\\+ command|new command/i.test(e.innerText||''));
            add?.click();
          }
        }
        return {
          slug: document.querySelector('input[name=product_slug]')?.value,
          cmds: [...document.querySelectorAll('input[name="payment_command[378958][]"]')].map(c=>c.value)
        };
      })()`,
      returnByValue: true,
    }).then((r) => console.log("warrior cmds", r.result.value));
  }

  // Save product
  const saved = await send("Runtime.evaluate", {
    expression: `(() => {
      const btn = [...document.querySelectorAll('button,input[type=submit],a')].find(e => /^(Save|SAVE)$/i.test((e.innerText||e.value||'').trim()) || (e.className||'').includes('save'));
      // Tip4Serv save is often top button "Save"
      const save = [...document.querySelectorAll('button')].find(e => /^\\s*Save\\s*$/i.test(e.innerText||''));
      if (save) { save.click(); return 'clicked Save'; }
      if (btn) { btn.click(); return 'clicked '+(btn.innerText||btn.value); }
      return 'no save';
    })()`,
    returnByValue: true,
  });
  console.log("save", saved.result.value);
  await new Promise((r) => setTimeout(r, 2500));
  const ok = await send("Runtime.evaluate", {
    expression: `(() => ({url:location.href, flash:(document.body.innerText||'').match(/success|saved|updated|error|Discord[\\s\\S]{0,200}/i)?.[0]?.slice(0,300), has379:document.body.innerText.includes('379102')}))()`,
    returnByValue: true,
  });
  console.log("after save", ok.result.value);
}

ws.close();
