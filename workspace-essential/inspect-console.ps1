$ErrorActionPreference = "Stop"
$PORT = 9222
$tab = (Invoke-RestMethod "http://127.0.0.1:$PORT/json/list") | Where-Object { $_.type -eq "page" -and $_.url -like "*console*" } | Select-Object -First 1
$ws = [System.Net.WebSockets.ClientWebSocket]::new()
$ct = [Threading.CancellationToken]::None
$ws.ConnectAsync([Uri]$tab.webSocketDebuggerUrl, $ct).Wait()
$id = 1
$buf = New-Object byte[] 2097152
function Send-Cdp($method, $params=@{}) {
  $script:msgId = $id++
  $payload = @{ id = $script:msgId; method = $method; params = $params } | ConvertTo-Json -Compress -Depth 8
  $bytes = [Text.Encoding]::UTF8.GetBytes($payload)
  $ws.SendAsync([ArraySegment[byte]]::new($bytes), [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $ct).Wait()
  $deadline = [DateTime]::UtcNow.AddSeconds(20)
  $acc = ""
  while ([DateTime]::UtcNow -lt $deadline) {
    $res = $ws.ReceiveAsync([ArraySegment[byte]]::new($buf), $ct).Result
    $acc += [Text.Encoding]::UTF8.GetString($buf, 0, $res.Count)
    if (-not $res.EndOfMessage) { continue }
    if ($acc -notmatch '"id"\s*:') { $acc = ""; continue }
    try { $msg = $acc | ConvertFrom-Json } catch { $acc = ""; continue }
    $acc = ""
    if ($msg.id -eq $script:msgId) { if ($msg.error) { throw $msg.error.message }; return $msg.result }
  }
  throw "timeout"
}
Send-Cdp "Page.navigate" @{ url = "https://www.g-portal.com/eur/server/minecraft-ram/8699357/console" } | Out-Null
Start-Sleep 6
$r = Send-Cdp "Runtime.evaluate" @{
  expression = @"
(() => {
  const inputs = [...document.querySelectorAll('input,textarea')].map(i => ({tag:i.tagName,name:i.name,ph:i.placeholder,type:i.type,cls:(i.className||'').toString().slice(0,60)}));
  const btns = [...document.querySelectorAll('button')].map(b => (b.innerText||b.getAttribute('aria-label')||'').trim()).filter(Boolean);
  return JSON.stringify({url:location.href, inputs, btns});
})()
"@
  returnByValue = $true
}
Write-Host $r.result.value
$ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "", $ct).Wait()
