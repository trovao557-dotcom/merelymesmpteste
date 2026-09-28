const PRODUCTS = [
  { id: 3, roleId: "1545039134106845205", name: "KNIGHT" },
  { id: 2, roleId: "1545039138183716955", name: "WARRIOR" },
  { id: 1, roleId: "1545039141773770772", name: "MACER" },
  { id: 0, roleId: "1544904299627159632", name: "PRIME" },
];
const STORE_CHANNEL = "1544904368157884476";

const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab = list.find((t) => String(t.url).includes("tip4serv.com"));
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
  console.log("\n===", p.name, "===");
  await send("Page.navigate", { url: `https://tip4serv.com/dashboard/product?id=${p.id}` });
  await new Promise((r) => setTimeout(r, 4000));

  // Add discord server if missing
  await send("Runtime.evaluate", {
    expression: `(() => {
      if ((document.body.innerText||'').includes('GIVE ROLE')) return {already:true};
      const list=document.querySelector('#servers_add_${p.id}');
      if(list) list.style.display='block';
      const link=document.querySelector('a[id="379102_${p.id}"]');
      if(!link) return {err:'no link'};
      if(window.jQuery) jQuery(link).click(); else link.click();
      return {clicked:true};
    })()`,
    returnByValue: true,
  }).then((r) => console.log("discord", r.result.value));
  await new Promise((r) => setTimeout(r, 2500));

  const setRole = await send("Runtime.evaluate", {
    expression: `(() => {
      const roleId = ${JSON.stringify(p.roleId)};
      const storeCh = ${JSON.stringify(STORE_CHANNEL)};
      // Virtual Select hidden input
      const hidden = document.querySelector('input[name="payment_role_add[379102]"]');
      const ele = document.querySelector('#select_pay_discord_roles_379102');
      let method = 'none';
      if (ele && window.VirtualSelect) {
        try {
          // VirtualSelect instances
          const inst = ele.$virtualSelect || ele._virtualSelect || null;
        } catch {}
      }
      // Open dropdown and click option
      const toggle = document.querySelector('#select_pay_discord_roles_379102 .vscomp-toggle-button');
      toggle?.click();
      // wait sync - options may be in DOM
      const opt = [...document.querySelectorAll('.vscomp-option, [data-value], .vscomp-option-text')].find(el => {
        const v = el.getAttribute('data-value') || '';
        const t = el.innerText || '';
        return v === roleId || /${p.name === "PRIME" ? "Prime" : p.name.charAt(0) + p.name.slice(1).toLowerCase()}/i.test(t) || t.includes(roleId);
      });
      // Better: match by known names
      const nameRe = /${p.name === "KNIGHT" ? "Knight" : p.name === "WARRIOR" ? "Warrior" : p.name === "MACER" ? "Macer" : "Prime"}/i;
      const opt2 = [...document.querySelectorAll('.vscomp-option')].find(el => nameRe.test(el.innerText||'') || el.getAttribute('data-value')===roleId);
      if (opt2) { opt2.click(); method = 'option-click:'+(opt2.innerText||'').slice(0,40); }
      else if (hidden) {
        hidden.value = roleId;
        hidden.dispatchEvent(new Event('input',{bubbles:true}));
        hidden.dispatchEvent(new Event('change',{bubbles:true}));
        method = 'hidden-set';
        // also try VirtualSelect setValue API via element
        try {
          if (typeof VirtualSelect !== 'undefined') {
            // find by ele
          }
          if (ele && typeof ele.setValue === 'function') ele.setValue([roleId]);
          // vscomp global
          const wrap = document.querySelector('#select_pay_discord_roles_379102');
          if (wrap && wrap.virtualSelect) wrap.virtualSelect.setValue([roleId]);
        } catch (e) { method += ' api-err:'+e.message; }
      }
      // Set announce channel to store
      const ch = document.querySelector('select[name="payment_message_channel[379102]"]');
      if (ch) {
        ch.value = storeCh;
        ch.dispatchEvent(new Event('change',{bubbles:true}));
      }
      // Optional thank you msg
      const msg = document.querySelector('textarea[name="payment_message[379102]"], input[name="payment_message[379102]"]');
      if (msg && !msg.value) {
        msg.value = 'Thanks for supporting MerelyMeSMP! Your Discord role is ready.';
        msg.dispatchEvent(new Event('input',{bubbles:true}));
      }
      return {
        method,
        hidden: hidden?.value,
        options: [...document.querySelectorAll('.vscomp-option')].slice(0,20).map(o=>({t:o.innerText.trim().slice(0,50), v:o.getAttribute('data-value')})),
        channel: ch?.value
      };
    })()`,
    returnByValue: true,
  });
  console.log("setRole", JSON.stringify(setRole.result.value, null, 2).slice(0, 2000));

  // Save
  await send("Runtime.evaluate", {
    expression: `(() => { const s=[...document.querySelectorAll('button')].find(b=>/^\\s*Save\\s*$/i.test(b.innerText||'')); s?.click(); return !!s; })()`,
    returnByValue: true,
  });
  await new Promise((r) => setTimeout(r, 3000));
  const after = await send("Runtime.evaluate", {
    expression: `(() => ({
      flash:(document.body.innerText||'').match(/success|saved|error/i)?.[0],
      roleVal: document.querySelector('input[name="payment_role_add[379102]"]')?.value,
      roleLabel: document.querySelector('#select_pay_discord_roles_379102 .vscomp-value')?.innerText
    }))()`,
    returnByValue: true,
  });
  console.log("after", after.result.value);
}
ws.close();
