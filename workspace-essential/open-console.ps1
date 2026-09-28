$ErrorActionPreference = "Stop"
$PORT = 9222
$tab = (Invoke-RestMethod "http://127.0.0.1:$PORT/json/list") | Where-Object { $_.type -eq "page" -and $_.url -like "*8699357*" } | Select-Object -First 1
$ws = [System.Net.WebSockets.ClientWebSocket]::new()
$ct = [Threading.CancellationToken]::None
$ws.ConnectAsync([Uri]$tab.webSocketDebuggerUrl, $ct).Wait()
$id = 1
$buf = New-Object byte[] 1048576
$handler = [System.Net.WebSockets.WebSocketMessageType]::Text

function Send-Cdp($method, $params=@{}) {
  $script:msgId = $id++
  $payload = @{ id = $script:msgId; method = $method; params = $params } | ConvertTo-Json -Compress -Depth 8
  $bytes = [Text.Encoding]::UTF8.GetBytes($payload)
  $ws.SendAsync([ArraySegment[byte]]::new($bytes), $handler, $true, $ct).Wait()
  $deadline = [DateTime]::UtcNow.AddSeconds(20)
  while ([DateTime]::UtcNow -lt $deadline) {
    $res = $ws.ReceiveAsync([ArraySegment[byte]]::new($buf), $ct).Result
    $text = [Text.Encoding]::UTF8.GetString($buf, 0, $res.Count)
    if ($text -notmatch '"id"\s*:') { continue }
    try { $msg = $text | ConvertFrom-Json } catch { continue }
    if ($null -eq $msg.id) { continue }
    if ($msg.id -eq $script:msgId) {
      if ($msg.error) { throw $msg.error.message }
      return $msg.result
    }
  }
  throw "timeout $method"
}

function Eval($expr) {
  (Send-Cdp "Runtime.evaluate" @{ expression = $expr; returnByValue = $true }).result.value
}

Send-Cdp "Page.enable" | Out-Null
Write-Host (Eval @"
(() => {
  const a = [...document.querySelectorAll('a')].find(el => (el.innerText||'').includes('Consola') || (el.href||'').includes('console'));
  if (!a) return 'missing';
  const href = a.href;
  a.click();
  return href;
})()
"@)
Start-Sleep -Seconds 6
Write-Host "url:" (Eval "location.href")
Write-Host (Eval @"
(() => {
  const input = document.querySelector('input[name=""console-input-message""]') || document.querySelector('input');
  const btns = [...document.querySelectorAll('button')].map(b => (b.innerText||'').trim()).filter(Boolean).slice(0,10);
  return JSON.stringify({href: location.href, input: input ? {name:input.name, ph:input.placeholder} : null, btns});
})()
"@)

$ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "", $ct).Wait()
