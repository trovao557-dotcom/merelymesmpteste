// Type a Discord slash command in the current channel textbox
const list = await (await fetch("http://127.0.0.1:9222/json/list")).json();
const tab = list.find((t) => t.url && t.url.includes("1544904386587787334"));
if (!tab) throw new Error("create-ticket tab missing");
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
    setTimeout(() => {
      if (pending.has(i)) {
        pending.delete(i);
        reject(Error("timeout " + method));
      }
    }, 20000);
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
await send("Runtime.enable").catch(() => {});
await send("Input.enable").catch(() => {});

// Focus chat box
const focused = await evalE(`(() => {
  const box = document.querySelector('[role="textbox"][data-slate-editor="true"], div[role="textbox"], [class*="slateTextArea"]');
  if (!box) return {ok:false, text:(document.body.innerText||'').slice(0,300)};
  box.focus();
  box.click();
  return {ok:true, tag:box.tagName};
})()`);
console.log("focus", focused);

async function typeText(text) {
  for (const ch of text) {
    await send("Input.dispatchKeyEvent", { type: "keyDown", text: ch });
    await send("Input.dispatchKeyEvent", { type: "keyUp", text: ch });
    await new Promise((r) => setTimeout(r, 40));
  }
}

await typeText("/panel");
await new Promise((r) => setTimeout(r, 1500));
const afterSlash = await evalE(`(() => ({
  text:(document.body.innerText||'').slice(-800),
  options:[...document.querySelectorAll('[role="option"],[class*="option"],[class*="autocomplete"] *')].map(e=>(e.innerText||'').trim()).filter(Boolean).slice(0,20)
}))()`);
console.log("after /panel", JSON.stringify(afterSlash, null, 2));

// Try click Ticket Tool option
const clicked = await evalE(`(() => {
  const els=[...document.querySelectorAll('div,span,li,[role=option]')].filter(e=>/Ticket Tool|panel/i.test(e.innerText||'') && (e.innerText||'').length<80);
  if(!els.length) return {ok:false};
  els[0].click();
  return {ok:true, text:els[0].innerText.slice(0,80)};
})()`);
console.log("click option", clicked);

await new Promise((r) => setTimeout(r, 800));
// Press Enter to send if it's a simple command
await send("Input.dispatchKeyEvent", { type: "keyDown", key: "Enter", code: "Enter", windowsVirtualKeyCode: 13 });
await send("Input.dispatchKeyEvent", { type: "keyUp", key: "Enter", code: "Enter", windowsVirtualKeyCode: 13 });
await new Promise((r) => setTimeout(r, 2000));
console.log(
  await evalE(`JSON.stringify({url:location.href, text:(document.body.innerText||'').slice(-1000)})`)
);
ws.close();
