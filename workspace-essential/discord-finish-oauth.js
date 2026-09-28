// Finish Discord OAuth: scroll permissions then Continuar/Autorizar
(async () => {
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  for (let i = 0; i < 50; i++) {
    const scrollBtn = [...document.querySelectorAll("button")].find((x) =>
      /Continue Rolando|Continuar a rolar/i.test(x.innerText || "")
    );
    if (scrollBtn) {
      const scrollers = [...document.querySelectorAll("div")].filter(
        (d) => d.scrollHeight > d.clientHeight + 20
      );
      scrollers.forEach((d) => {
        d.scrollTop = d.scrollHeight;
      });
      scrollBtn.click();
      await sleep(200);
      continue;
    }
    const next = [...document.querySelectorAll("button")].find((x) =>
      /^(Continuar|Continue)$/i.test((x.innerText || "").trim())
    );
    if (next) {
      next.click();
      await sleep(1500);
      continue;
    }
    const auth = [...document.querySelectorAll("button")].find((x) =>
      /^(Autorizar|Authorize)$/i.test((x.innerText || "").trim())
    );
    if (auth) {
      auth.click();
      await sleep(2500);
      return {
        done: "AUTH",
        url: location.href,
        text: (document.body.innerText || "").slice(0, 400),
      };
    }
    break;
  }
  return {
    done: "stuck",
    url: location.href,
    buttons: [...document.querySelectorAll("button")]
      .map((b) => b.innerText.trim())
      .filter(Boolean),
    text: (document.body.innerText || "").slice(0, 500),
  };
})();
