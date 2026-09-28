$ErrorActionPreference = "Stop"
$PORT = 9222
$tab = (Invoke-RestMethod "http://127.0.0.1:$PORT/json/list") | Where-Object { $_.type -eq "page" -and $_.url -like "*console*" } | Select-Object -First 1
if (-not $tab) { $tab = (Invoke-RestMethod "http://127.0.0.1:$PORT/json/list") | Where-Object { $_.type -eq "page" -and $_.url -like "*8699357*" } | Select-Object -First 1 }
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

function Send-Cmd($cmd) {
  $jsonCmd = ($cmd | ConvertTo-Json)
  $expr = @"
(() => {
  const cmd = $jsonCmd;
  const input = document.querySelector('input[name="console-input-message"]') || document.querySelector('input[type="text"]') || document.querySelector('textarea');
  if (!input) return 'no-input';
  const proto = input.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
  const setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
  setter.call(input, cmd);
  input.dispatchEvent(new Event('input', { bubbles: true }));
  input.dispatchEvent(new Event('change', { bubbles: true }));
  const btn = [...document.querySelectorAll('button')].find(b => /enviar|send/i.test((b.innerText||'').trim()));
  if (btn) { btn.click(); return 'sent:' + cmd; }
  input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, bubbles: true }));
  return 'enter:' + cmd;
})()
"@
  $r = Send-Cdp "Runtime.evaluate" @{ expression = $expr; returnByValue = $true; userGesture = $true }
  return $r.result.value
}

Send-Cdp "Runtime.enable" | Out-Null
Start-Sleep -Seconds 3
Write-Host (Send-Cdp "Runtime.evaluate" @{ expression = "location.href + ' | inputs=' + document.querySelectorAll('input,textarea').length"; returnByValue = $true }).result.value
Write-Host (Send-Cmd "tab reload")
Start-Sleep -Seconds 2
Write-Host (Send-Cmd "sk reload fix-rank-chat")
$ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "", $ct).Wait()
