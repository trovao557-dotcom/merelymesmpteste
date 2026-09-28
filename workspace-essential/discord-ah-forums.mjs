const GUILD="1534136666984419348";
const list=await(await fetch("http://127.0.0.1:9222/json/list")).json();
const tab=list.find(t=>t.url&&t.url.includes("discord.com/channels/153413"));
const ws=new WebSocket(tab.webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});
let id=1; const pending=new Map();
ws.onmessage=ev=>{const m=JSON.parse(ev.data); if(m.id==null)return; const p=pending.get(m.id); if(!p)return; pending.delete(m.id); m.error?p.reject(Error(m.error.message)):p.resolve(m.result);};
const send=(method,params={})=>new Promise((resolve,reject)=>{const i=id++; const t=setTimeout(()=>{if(pending.has(i)){pending.delete(i);reject(Error("to"));}},45000); pending.set(i,{resolve:v=>{clearTimeout(t);resolve(v)},reject:e=>{clearTimeout(t);reject(e)}}); ws.send(JSON.stringify({id:i,method,params}));});
const tok=(await send("Runtime.evaluate",{expression:`(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()`,returnByValue:true})).result.value;
const api=async(path,opts={})=>{const res=await fetch("https://discord.com/api/v9"+path,{...opts,headers:{Authorization:tok,"Content-Type":"application/json",...(opts.headers||{})}}); const text=await res.text(); let body; try{body=JSON.parse(text);}catch{body=text;} return {status:res.status,body};};
const channels=(await api(`/guilds/${GUILD}/channels`)).body;
const serverCat=channels.find(c=>c.type===4 && /SERVER/i.test(c.name));
const roles=(await api(`/guilds/${GUILD}/roles`)).body;
const roleId=n=>roles.find(r=>new RegExp(n,"i").test(r.name))?.id;
console.log("serverCat", serverCat?.id, serverCat?.name);

// Create MARKET category or put under SERVER
let marketCat=channels.find(c=>c.type===4 && /MARKET|AH|AUCTION|TRADE/i.test(c.name));
if(!marketCat){
  const c=await api(`/guilds/${GUILD}/channels`,{method:"POST",body:JSON.stringify({name:"💰┃MARKET",type:4,position:(serverCat?.position||0)+1})});
  console.log("create cat", c.status, c.body?.id, c.body?.name||c.body?.message);
  marketCat=c.body;
}
const parent=marketCat?.id;

// Forum for AH listings
const existing=channels.find(c=>/ah-sell|auction|player-ah|ah-listings/i.test(c.name));
let forum=existing;
if(!forum){
  const f=await api(`/guilds/${GUILD}/channels`,{method:"POST",body:JSON.stringify({
    name:"🛒┃ah-sell",
    type:15,
    parent_id:parent,
    topic:"Post items you are selling on /ah. Title = item + price. Use tags.",
    available_tags:[
      {name:"Weapons",moderated:false},
      {name:"Armor",moderated:false},
      {name:"Tools",moderated:false},
      {name:"Blocks",moderated:false},
      {name:"Spawners",moderated:false},
      {name:"Misc",moderated:false},
      {name:"Sold",moderated:true}
    ],
    default_reaction_emoji:{emoji_name:"💰"},
    rate_limit_per_user:10
  })});
  console.log("forum", f.status, f.body?.id||f.body?.message, f.body?.name);
  forum=f.body;
} else console.log("forum exists", forum.id, forum.name);

// Optional tips/trades forum
let trades=channels.find(c=>/player-trade|trades/i.test(c.name));
if(!trades){
  const f=await api(`/guilds/${GUILD}/channels`,{method:"POST",body:JSON.stringify({
    name:"🤝┃player-trades",
    type:15,
    parent_id:parent,
    topic:"Player-to-player trades (not only /ah). Be clear and no scams.",
    available_tags:[
      {name:"WTS",moderated:false},
      {name:"WTB",moderated:false},
      {name:"WTT",moderated:false},
      {name:"Done",moderated:true}
    ],
    default_reaction_emoji:{emoji_name:"🤝"},
    rate_limit_per_user:10
  })});
  console.log("trades", f.status, f.body?.id||f.body?.message);
  trades=f.body;
}

// Post guidelines in ah-sell if forum
if(forum?.id){
  // Forums use starter messages differently - post as channel message may fail; try create post
  const guidelines=[
    "**AH SELL FORUM**",
    "Post items you listed on `/ah`.",
    "",
    "**Title format:** `[PRICE] Item name`",
    "Example: `[50k] Netherite Sword Sharpness 5`",
    "",
    "**In the post include:**",
    "• Minecraft nick",
    "• Price + currency",
    "• Enchantments / details",
    "• Screenshot if possible",
    "",
    "Mark as **Sold** when done. Scams = ban."
  ].join("\n");
  // For forum channels, create a thread/post
  const post=await api(`/channels/${forum.id}/threads`,{method:"POST",body:JSON.stringify({
    name:"📌 READ ME — How to post",
    auto_archive_duration:10080,
    message:{content:guidelines},
    applied_tags: []
  })});
  console.log("guidelines post", post.status, post.body?.id||JSON.stringify(post.body).slice(0,200));
}

// Tip4Serv role IDs for later
console.log("ROLES", Object.fromEntries(["Knight","Warrior","Macer","Prime","Clipper","Member"].map(n=>[n,roleId(n)])));
ws.close();
