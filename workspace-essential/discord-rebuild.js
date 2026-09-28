(async () => {
  const GUILD = "1534136666984419348";
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  const iframe = document.createElement("iframe");
  document.body.appendChild(iframe);
  let token = iframe.contentWindow.localStorage.getItem("token");
  iframe.remove();
  if (!token) return { ok: false, err: "no token" };
  token = token.replace(/^"|"$/g, "");
  const headers = { Authorization: token, "Content-Type": "application/json" };
  const api = async (method, path, body) => {
    const res = await fetch("https://discord.com/api/v9" + path, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
    });
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch { data = { raw: text }; }
    if (!res.ok) {
      if (res.status === 429) {
        const wait = ((data && data.retry_after) || 2) * 1000 + 200;
        await sleep(wait);
        return api(method, path, body);
      }
      return { __error: true, status: res.status, data };
    }
    return data;
  };

  const log = [];
  const channels = await api("GET", `/guilds/${GUILD}/channels`);
  if (channels.__error) return { ok: false, step: "list channels", channels };
  const roles = await api("GET", `/guilds/${GUILD}/roles`);
  if (roles.__error) return { ok: false, step: "list roles", roles };

  // 1) Delete all channels (wipes old chat history with them)
  for (const c of channels) {
    const del = await api("DELETE", `/channels/${c.id}`);
    if (del && del.__error) log.push("del-ch-fail:" + c.name + ":" + del.status);
    else log.push("del-ch:" + c.name);
    await sleep(450);
  }

  // 2) Delete old custom roles (keep @everyone + known bot roles)
  const keepRole = (n) =>
    n === "@everyone" ||
    /jockie|bot|carl|ticket|stat|wick|dyno|mee6|groovy|rythm/i.test(n);
  const sortedRoles = [...roles].sort((a, b) => b.position - a.position);
  for (const r of sortedRoles) {
    if (keepRole(r.name) || r.id === GUILD) continue;
    const del = await api("DELETE", `/guilds/${GUILD}/roles/${r.id}`);
    if (del && del.__error) log.push("del-role-fail:" + r.name + ":" + del.status);
    else log.push("del-role:" + r.name);
    await sleep(450);
  }

  // 3) Create roles (bottom to top so final order is nice)
  const roleDefs = [
    { name: "🎮┃Member", color: 0xd4d4d4, hoist: false, mentionable: false },
    { name: "⭐┃Prime", color: 0xffd400, hoist: true, mentionable: false },
    { name: "💜┃Media", color: 0xe14aff, hoist: true, mentionable: true },
    { name: "🧡┃Helper", color: 0xff9a1f, hoist: true, mentionable: true },
    { name: "🎫┃Ticket Staff", color: 0x57f287, hoist: false, mentionable: true },
    { name: "🛠️┃Mod", color: 0x3d9bff, hoist: true, mentionable: true },
    { name: "⚡┃Admin", color: 0xa855ff, hoist: true, mentionable: true },
    { name: "👑┃Owner", color: 0xff2a2a, hoist: true, mentionable: true },
  ];
  const createdRoles = {};
  for (const rd of roleDefs) {
    const r = await api("POST", `/guilds/${GUILD}/roles`, {
      name: rd.name,
      color: rd.color,
      hoist: rd.hoist,
      mentionable: rd.mentionable,
      permissions: "0",
    });
    if (r.__error) log.push("role-fail:" + rd.name);
    else {
      createdRoles[rd.name] = r.id;
      log.push("role:" + rd.name);
    }
    await sleep(500);
  }

  // Helper overwrites
  const denyView = 1024; // VIEW_CHANNEL
  const allowView = 1024;
  const allowSend = 2048;
  const denySend = 2048;

  const staffRoleIds = [
    createdRoles["👑┃Owner"],
    createdRoles["⚡┃Admin"],
    createdRoles["🛠️┃Mod"],
    createdRoles["🧡┃Helper"],
    createdRoles["🎫┃Ticket Staff"],
  ].filter(Boolean);

  const staffOverwrites = [
    { id: GUILD, type: 0, allow: "0", deny: String(denyView) },
    ...staffRoleIds.map((id) => ({ id, type: 0, allow: String(allowView), deny: "0" })),
  ];

  const announceOverwrites = [
    { id: GUILD, type: 0, allow: String(allowView), deny: String(denySend) },
    ...[createdRoles["👑┃Owner"], createdRoles["⚡┃Admin"], createdRoles["🛠️┃Mod"]]
      .filter(Boolean)
      .map((id) => ({ id, type: 0, allow: String(allowView | allowSend), deny: "0" })),
  ];

  // 4) Create categories + channels
  const structure = [
    {
      cat: "📢┃INFORMATION",
      channels: [
        { name: "📣┃announcements", type: 0, topic: "Official MerelyMeSMP news only.", overwrites: announceOverwrites },
        { name: "📜┃rules", type: 0, topic: "Server rules — read before playing.", overwrites: announceOverwrites },
        { name: "🌐┃ip-links", type: 0, topic: "IP, store, socials, vote links.", overwrites: announceOverwrites },
        { name: "🎉┃updates", type: 0, topic: "Patch notes & season updates.", overwrites: announceOverwrites },
      ],
    },
    {
      cat: "💬┃COMMUNITY",
      channels: [
        { name: "💬┃chat-geral", type: 0, topic: "Main community chat. Be respectful." },
        { name: "📸┃media", type: 0, topic: "Clips, screenshots, builds." },
        { name: "🤖┃bot-commands", type: 0, topic: "Use bot commands here." },
        { name: "💡┃sugestoes", type: 0, topic: "Ideas for the SMP — one suggestion per message." },
        { name: "🤝┃looking-for-team", type: 0, topic: "Find teammates / trade buddies." },
      ],
    },
    {
      cat: "🎮┃SERVER",
      channels: [
        { name: "🛒┃store", type: 0, topic: "Ranks, crates & support the server." },
        { name: "🗳️┃vote", type: 0, topic: "Vote links & rewards." },
        { name: "🏆┃leaderboard", type: 0, topic: "Top players & events." },
        { name: "⏱️┃playtime", type: 0, topic: "Voice/playtime leaderboards (Statbot)." },
      ],
    },
    {
      cat: "🎫┃SUPPORT",
      channels: [
        { name: "🎫┃create-ticket", type: 0, topic: "Open a ticket for help, appeals, buys." },
      ],
    },
    {
      cat: "🔊┃VOICE",
      channels: [
        { name: "🔊┃geral", type: 2 },
        { name: "🎮┃gaming", type: 2 },
        { name: "🎵┃music", type: 2 },
        { name: "💤┃afk", type: 2 },
      ],
    },
    {
      cat: "🔒┃STAFF",
      overwrites: staffOverwrites,
      channels: [
        { name: "👑┃owner", type: 0, topic: "Owner only / high-level decisions.", overwrites: [
          { id: GUILD, type: 0, allow: "0", deny: String(denyView) },
          ...(createdRoles["👑┃Owner"] ? [{ id: createdRoles["👑┃Owner"], type: 0, allow: String(allowView | allowSend), deny: "0" }] : []),
          ...(createdRoles["⚡┃Admin"] ? [{ id: createdRoles["⚡┃Admin"], type: 0, allow: String(allowView | allowSend), deny: "0" }] : []),
        ]},
        { name: "🛡️┃staff-chat", type: 0, topic: "Staff coordination.", overwrites: staffOverwrites },
        { name: "📝┃logs", type: 0, topic: "Moderation / bot logs.", overwrites: staffOverwrites },
        { name: "🚨┃reports", type: 0, topic: "Player reports mirror.", overwrites: staffOverwrites },
      ],
    },
  ];

  const createdChannels = {};
  let pos = 0;
  for (const block of structure) {
    const catBody = {
      name: block.cat,
      type: 4,
      position: pos++,
    };
    if (block.overwrites) catBody.permission_overwrites = block.overwrites;
    const cat = await api("POST", `/guilds/${GUILD}/channels`, catBody);
    if (cat.__error) {
      log.push("cat-fail:" + block.cat + ":" + cat.status);
      continue;
    }
    createdChannels[block.cat] = cat.id;
    log.push("cat:" + block.cat);
    await sleep(550);

    for (const ch of block.channels) {
      const body = {
        name: ch.name,
        type: ch.type,
        parent_id: cat.id,
        position: pos++,
      };
      if (ch.topic) body.topic = ch.topic;
      if (ch.overwrites) body.permission_overwrites = ch.overwrites;
      else if (block.overwrites) body.permission_overwrites = block.overwrites;
      const created = await api("POST", `/guilds/${GUILD}/channels`, body);
      if (created.__error) log.push("ch-fail:" + ch.name + ":" + created.status);
      else {
        createdChannels[ch.name] = created.id;
        log.push("ch:" + ch.name);
      }
      await sleep(550);
    }
  }

  // 5) Seed professional messages in key channels
  const posts = [
    ["📣┃announcements", "**MerelyMeSMP** is live.\nSeason 1 · EU · Survival Economy\nStay tuned for events, updates & giveaways."],
    ["📜┃rules", [
      "**RULES — MerelyMeSMP**",
      "",
      "1. Be respectful — no toxicity, hate, or harassment.",
      "2. No cheating / hacked clients / freecam / xray.",
      "3. No scam, dupe, or real-money trades outside store.",
      "4. No spam, NSFW, or advertising other SMPs.",
      "5. English / Portuguese OK — keep chat clean.",
      "6. Staff decisions are final — appeal via ticket.",
      "",
      "IP: `merelymesmp.g-portal.game`",
    ].join("\n")],
    ["🌐┃ip-links", [
      "**CONNECT**",
      "IP: `merelymesmp.g-portal.game`",
      "",
      "**USEFUL**",
      "• Store / ranks — (add link)",
      "• Vote — (add link)",
      "• Twitch — twitch.tv/merelyme",
    ].join("\n")],
    ["🎫┃create-ticket", [
      "**NEED HELP?**",
      "Open a ticket for:",
      "• Buy / payment issues",
      "• Ban / mute appeals",
      "• Bugs & reports",
      "• Staff applications",
      "",
      "_Ticket bot will be added next — use this channel as the panel._",
    ].join("\n")],
    ["💬┃chat-geral", "Welcome to **MerelyMeSMP**. Lock in. 🔒\nRead <#rules> and join the grind."],
  ];

  for (const [name, content] of posts) {
    const id = createdChannels[name];
    if (!id) continue;
    // fix rules mention if we have id
    let msg = content;
    if (createdChannels["📜┃rules"]) {
      msg = msg.replace("<#rules>", `<#${createdChannels["📜┃rules"]}>`);
    }
    const p = await api("POST", `/channels/${id}/messages`, { content: msg });
    if (p.__error) log.push("msg-fail:" + name);
    else log.push("msg:" + name);
    await sleep(400);
  }

  // Rename guild if needed stays MerelyMe SMP
  await api("PATCH", `/guilds/${GUILD}`, {
    description: "MerelyMeSMP · First Season EU · Survival Economy · Lock in.",
  });

  return {
    ok: true,
    roles: Object.keys(createdRoles).length,
    channels: Object.keys(createdChannels).length,
    log: log.slice(-80),
  };
})()
