$ErrorActionPreference = "Stop"
$PORT = 9222
$tab = (Invoke-RestMethod "http://127.0.0.1:$PORT/json/list") | Where-Object { $_.type -eq "page" -and $_.url -like "*discord.com*" } | Select-Object -First 1
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
  $deadline = [DateTime]::UtcNow.AddSeconds(25)
  $acc = ""
  while ([DateTime]::UtcNow -lt $deadline) {
    $res = $ws.ReceiveAsync([ArraySegment[byte]]::new($buf), $ct).Result
    $acc += [Text.Encoding]::UTF8.GetString($buf, 0, $res.Count)
    if (-not $res.EndOfMessage) { continue }
    if ($acc -notmatch '"id"\s*:') { $acc = ""; continue }
    try { $msg = $acc | ConvertFrom-Json } catch { $acc = ""; continue }
    $acc = ""
    if ($msg.id -eq $script:msgId) {
      if ($msg.error) { throw ($msg.error | ConvertTo-Json -Compress) }
      return $msg.result
    }
  }
  throw "timeout $method"
}
Send-Cdp "Runtime.enable" | Out-Null
Start-Sleep 2
$r = Send-Cdp "Runtime.evaluate" @{
  expression = @"
(() => {
  return {
    url: location.href,
    title: document.title,
    text: (document.body.innerText || '').slice(0, 3000),
    login: !!(document.querySelector('input[name=""email""], button[type=""submit""]')) || /log in|iniciar sess/i.test(document.body.innerText||'')
  };
})()
"@
  returnByValue = $true
}
$r.result.value | ConvertTo-Json -Depth 5
$ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "", $ct).Wait()
