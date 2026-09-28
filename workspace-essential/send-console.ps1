param(
    [Parameter(Mandatory = $true)][string]$Command
)

$ErrorActionPreference = "Stop"
$PORT = 9222
$list = Invoke-RestMethod "http://127.0.0.1:$PORT/json/list"
$tab = $list | Where-Object { $_.type -eq "page" -and $_.url -like "*8699357/console*" } | Select-Object -First 1
if (-not $tab) {
    $tab = $list | Where-Object { $_.type -eq "page" -and $_.url -like "*8699357*" } | Select-Object -First 1
}
if (-not $tab) { throw "no tab" }

$ws = New-Object System.Net.WebSockets.ClientWebSocket
$ws.ConnectAsync([Uri]$tab.webSocketDebuggerUrl, [Threading.CancellationToken]::None).Wait()

function Send-Cdp($method, $paramsObj) {
    $script:id = if ($script:id) { $script:id + 1 } else { 1 }
    $payload = @{ id = $script:id; method = $method; params = $paramsObj } | ConvertTo-Json -Compress -Depth 8
    $bytes = [Text.Encoding]::UTF8.GetBytes($payload)
    $seg = [ArraySegment[byte]]::new($bytes)
    $ws.SendAsync($seg, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, [Threading.CancellationToken]::None).Wait()
    $buf = New-Object byte[] 65536
    do {
        $res = $ws.ReceiveAsync([ArraySegment[byte]]::new($buf), [Threading.CancellationToken]::None).Result
        $json = [Text.Encoding]::UTF8.GetString($buf, 0, $res.Count)
    } while (-not $res.EndOfMessage)
    $msg = $json | ConvertFrom-Json
    if ($msg.error) { throw $msg.error.message }
    return $msg.result
}

Send-Cdp "Runtime.enable" @{} | Out-Null
if ($tab.url -notlike "*console*") {
    Send-Cdp "Page.navigate" @{ url = "https://www.g-portal.com/eur/server/minecraft-ram/8699357/console" } | Out-Null
    Start-Sleep -Seconds 5
}

$cmdJson = ($Command | ConvertTo-Json -Compress)
$expr = @"
(() => {
  const inputs = [...document.querySelectorAll('input')];
  const input = inputs.find(i => i.name === 'console-input-message')
    || inputs.find(i => /command|comando|console/i.test((i.placeholder||'') + (i.name||'')))
    || inputs.find(i => i.type === 'text' && !i.hidden);
  if (!input) return { ok: false, err: 'no input' };
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
  setter.call(input, $cmdJson);
  input.dispatchEvent(new Event('input', { bubbles: true }));
  input.dispatchEvent(new Event('change', { bubbles: true }));
  const btn = [...document.querySelectorAll('button')].find(b => /ENVIAR|SEND|Send/i.test((b.innerText||'').trim()));
  if (!btn) return { ok: false, err: 'no send btn' };
  btn.click();
  return { ok: true, cmd: input.value, btn: btn.innerText.trim() };
})()
"@

$r = Send-Cdp "Runtime.evaluate" @{ expression = $expr; returnByValue = $true; userGesture = $true }
$ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "", [Threading.CancellationToken]::None).Wait()
$r.result.value | ConvertTo-Json -Compress
