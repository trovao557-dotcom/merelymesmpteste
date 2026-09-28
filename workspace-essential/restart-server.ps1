$ErrorActionPreference = "Stop"
$PORT = 9222
$list = Invoke-RestMethod "http://127.0.0.1:$PORT/json/list"
$tab = $list | Where-Object { $_.type -eq "page" -and $_.url -like "*8699357*" } | Select-Object -First 1
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
  $deadline = [DateTime]::UtcNow.AddSeconds(25)
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

Send-Cdp "Page.enable" | Out-Null
Send-Cdp "Page.navigate" @{ url = "https://www.g-portal.com/eur/server/minecraft-ram/8699357" } | Out-Null
Start-Sleep -Seconds 10

$r = Send-Cdp "Runtime.evaluate" @{
  expression = @"
(() => {
  const texts = [...document.querySelectorAll('button, a, [role=button], span, div')]
    .map(el => ({el, t: (el.innerText||el.textContent||'').trim()}))
    .filter(x => x.t.length > 0 && x.t.length < 40);
  const restart = texts.find(x => /^restart$/i.test(x.t) || /^reiniciar$/i.test(x.t));
  if (restart) { restart.el.click(); return 'restart:' + restart.t; }
  const stop = texts.find(x => /^stop$/i.test(x.t) || /^parar$/i.test(x.t));
  if (stop) { stop.el.click(); return 'stop:' + stop.t; }
  return 'buttons:' + texts.slice(0,20).map(x=>x.t).join('|');
})()
"@
  returnByValue = $true
}
Write-Host "action1:" $r.result.value
Start-Sleep -Seconds 2

$r2 = Send-Cdp "Runtime.evaluate" @{
  expression = @"
(() => {
  const texts = [...document.querySelectorAll('button, a, [role=button]')]
    .map(el => ({el, t: (el.innerText||el.textContent||'').trim()}));
  const ok = texts.find(x => /^(yes|sim|ok|confirm|restart|reiniciar)$/i.test(x.t));
  if (ok) { ok.el.click(); return 'confirm:' + ok.t; }
  return 'no-confirm';
})()
"@
  returnByValue = $true
}
Write-Host "action2:" $r2.result.value
$ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "", $ct).Wait()
