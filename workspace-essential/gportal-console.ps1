$ErrorActionPreference = "Stop"
$PORT = 9222
$tab = (Invoke-RestMethod "http://127.0.0.1:$PORT/json/list") | Where-Object { $_.type -eq "page" -and $_.url -like "*8699357*" } | Select-Object -First 1
$ws = [System.Net.WebSockets.ClientWebSocket]::new()
$ct = [Threading.CancellationToken]::None
$ws.ConnectAsync([Uri]$tab.webSocketDebuggerUrl, $ct).Wait()
$id = 1
$buf = New-Object byte[] 2097152
$handler = [System.Net.WebSockets.WebSocketMessageType]::Text

function Send-Cdp($method, $params=@{}) {
  $script:msgId = $id++
  $payload = @{ id = $script:msgId; method = $method; params = $params } | ConvertTo-Json -Compress -Depth 8
  $bytes = [Text.Encoding]::UTF8.GetBytes($payload)
  $ws.SendAsync([ArraySegment[byte]]::new($bytes), $handler, $true, $ct).Wait()
  $deadline = [DateTime]::UtcNow.AddSeconds(20)
  $acc = ""
  while ([DateTime]::UtcNow -lt $deadline) {
    $res = $ws.ReceiveAsync([ArraySegment[byte]]::new($buf), $ct).Result
    $acc += [Text.Encoding]::UTF8.GetString($buf, 0, $res.Count)
    if (-not $res.EndOfMessage) { continue }
    if ($acc -notmatch '"id"\s*:') { $acc = ""; continue }
    try { $msg = $acc | ConvertFrom-Json } catch { $acc = ""; continue }
    $acc = ""
    if ($null -eq $msg.id) { continue }
    if ($msg.id -eq $script:msgId) {
      if ($msg.error) { throw ($msg.error | ConvertTo-Json -Compress) }
      return $msg.result
    }
  }
  throw "timeout"
}

Send-Cdp "Page.enable" | Out-Null
$r = Send-Cdp "Runtime.evaluate" @{
  expression = @"
(() => {
  const inputs = [...document.querySelectorAll('input, textarea')].map(el => ({
    tag: el.tagName,
    type: el.type || '',
    ph: el.placeholder || '',
    name: el.name || '',
    id: el.id || '',
    aria: el.getAttribute('aria-label') || ''
  }));
  const cons = [...document.querySelectorAll('*')].filter(el => /console|consola/i.test(el.innerText||'') && (el.innerText||'').length < 40).slice(0,10).map(el => (el.innerText||'').trim());
  return JSON.stringify({inputs: inputs.slice(0,30), cons});
})()
"@
  returnByValue = $true
}
Write-Host $r.result.value
$ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "", $ct).Wait()
