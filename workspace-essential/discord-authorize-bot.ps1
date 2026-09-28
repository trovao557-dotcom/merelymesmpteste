param(
  [Parameter(Mandatory=$true)][string]$TabId,
  [string]$GuildId = "1534136666984419348",
  [string]$GuildName = "MerelyMe SMP"
)
$ErrorActionPreference = "Stop"

$PORT = 9222
$tab = (Invoke-RestMethod "http://127.0.0.1:$PORT/json/list") | Where-Object { $_.id -eq $TabId } | Select-Object -First 1
if (-not $tab) { throw "tab not found $TabId" }
$ws = [System.Net.WebSockets.ClientWebSocket]::new()
$ct = [Threading.CancellationToken]::None
$ws.ConnectAsync([Uri]$tab.webSocketDebuggerUrl, $ct).Wait()
$id = 1
$buf = New-Object byte[] 8388608
function Send-Cdp($method, $params = @{}) {
  $script:msgId = $id++
  $payload = @{ id = $script:msgId; method = $method; params = $params } | ConvertTo-Json -Compress -Depth 10
  $bytes = [Text.Encoding]::UTF8.GetBytes($payload)
  $ws.SendAsync([ArraySegment[byte]]::new($bytes), [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $ct).Wait()
  $deadline = [DateTime]::UtcNow.AddSeconds(30)
  $acc = ""
  while ([DateTime]::UtcNow -lt $deadline) {
    $res = $ws.ReceiveAsync([ArraySegment[byte]]::new($buf), $ct).Result
    $acc += [Text.Encoding]::UTF8.GetString($buf, 0, $res.Count)
    if (-not $res.EndOfMessage) { continue }
    if ($acc -notmatch '"id"') { $acc = ""; continue }
    try { $msg = $acc | ConvertFrom-Json } catch { $acc = ""; continue }
    $acc = ""
    if ($msg.id -eq $script:msgId) {
      if ($msg.error) { throw ($msg.error | ConvertTo-Json -Compress) }
      return $msg.result
    }
  }
  throw "timeout $method"
}

function Eval($expr) {
  (Send-Cdp "Runtime.evaluate" @{ expression = $expr; returnByValue = $true }).result.value
}

Start-Sleep 2
# Continue in browser if shown
$c1 = Eval @'
(() => {
  const a = [...document.querySelectorAll("a,button")].find(x => /Continuar para o Discord|Continue to Discord|navegador|browser/i.test(x.innerText||""));
  if (a) { a.click(); return "continue:" + a.innerText; }
  return "no-continue";
})()
'@
Write-Host $c1
Start-Sleep 4

# Select guild in dropdown if present
$c2 = Eval @"
(() => {
  const text = document.body.innerText || '';
  // try select element
  const sel = document.querySelector('select');
  if (sel) {
    const opt = [...sel.options].find(o => (o.text||'').includes('$GuildName') || o.value === '$GuildId');
    if (opt) { sel.value = opt.value; sel.dispatchEvent(new Event('change', {bubbles:true})); return 'select:' + opt.text; }
  }
  // click guild picker buttons
  const btn = [...document.querySelectorAll('button,div[role=button],div[class*=option]')].find(el => (el.innerText||'').includes('$GuildName'));
  if (btn) { btn.click(); return 'click-guild:' + btn.innerText.slice(0,40); }
  return 'no-guild-picker:' + text.slice(0,400);
})()
"@
Write-Host $c2
Start-Sleep 2

# Authorize
$c3 = Eval @'
(() => {
  const b = [...document.querySelectorAll("button")].find(x => /^(Autorizar|Authorize|Continuar|Continue)$/i.test((x.innerText||"").trim()) || /Autorizar|Authorize/.test(x.innerText||""));
  if (b) { b.click(); return "auth:" + b.innerText.trim(); }
  return "buttons:" + [...document.querySelectorAll("button")].map(x => (x.innerText||"").trim()).filter(Boolean).slice(0,15).join("|");
})()
'@
Write-Host $c3
Start-Sleep 3
$final = Eval 'JSON.stringify({url:location.href, text:(document.body.innerText||"").slice(0,800)})'
Write-Host $final
$ws.Dispose()
