$ErrorActionPreference = "Stop"
$PORT = 9222
$tab = (Invoke-RestMethod "http://127.0.0.1:$PORT/json/list") | Where-Object { $_.type -eq "page" -and $_.url -like "*console*" } | Select-Object -First 1
if (-not $tab) {
  $tab = (Invoke-RestMethod "http://127.0.0.1:$PORT/json/list") | Where-Object { $_.type -eq "page" -and $_.url -like "*8699357*" } | Select-Object -First 1
}
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
  const input = document.querySelector('input[name="console-input-message"]');
  if (!input) return 'no-input';
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
  setter.call(input, cmd);
  input.dispatchEvent(new Event('input', { bubbles: true }));
  const btn = [...document.querySelectorAll('button')].find(b => (b.innerText||'').trim() === 'ENVIAR');
  if (!btn) return 'no-btn';
  btn.click();
  return 'ok';
})()
"@
  $r = Send-Cdp "Runtime.evaluate" @{ expression = $expr; returnByValue = $true; userGesture = $true }
  return $r.result.value
}

Send-Cdp "Page.enable" | Out-Null
if ($tab.url -notlike "*console*") {
  Send-Cdp "Page.navigate" @{ url = "https://www.g-portal.com/eur/server/minecraft-ram/8699357/console" } | Out-Null
  Start-Sleep -Seconds 6
}

$cmds = @(
  'tab reload',
  'lp group default meta setprefix 5 "&#E8E8E8&lPLAYER &f"',
  'lp group member meta setprefix 10 "&#D4D4D4&lMEMBER &f"',
  'lp group knight meta setprefix 20 "&#4CFF3A&lKNIGHT &f"',
  'lp group warrior meta setprefix 30 "&#FF3B3B&lWARRIOR &f"',
  'lp group macer meta setprefix 40 "&#2ECBFF&lMACER &f"',
  'lp group prime meta setprefix 50 "&#FFD400&lPRIME &f"',
  'lp group clipper meta setprefix 55 "&#00F0FF&lCLIPPER &f"',
  'lp group media meta setprefix 60 "&#E14AFF&lMEDIA &f"',
  'lp group helper meta setprefix 80 "&#FF9A1F&lHELPER &f"',
  'lp group mod meta setprefix 90 "&#3D9BFF&lMOD &f"',
  'lp group admin meta setprefix 95 "&#A855FF&lADMIN &f"',
  'lp group owner meta setprefix 100 "&#FF2A2A&lOWNER &f"',
  'sk reload fix-rank-chat'
)

foreach ($c in $cmds) {
  $res = Send-Cmd $c
  Write-Host "$res | $c"
  Start-Sleep -Milliseconds 700
}
$ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "", $ct).Wait()
Write-Host "DONE"
