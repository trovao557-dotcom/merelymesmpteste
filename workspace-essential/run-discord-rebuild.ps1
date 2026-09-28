$ErrorActionPreference = "Stop"
$PORT = 9222
$tab = (Invoke-RestMethod "http://127.0.0.1:$PORT/json/list") | Where-Object { $_.id -eq "CC7943C66D0FC7ADAD448D8DDBF58DA5" }
$ws = [System.Net.WebSockets.ClientWebSocket]::new()
$ct = [Threading.CancellationToken]::None
$ws.ConnectAsync([Uri]$tab.webSocketDebuggerUrl, $ct).Wait()
$id = 1
$buf = New-Object byte[] 16777216
function Send-Cdp($method, $params = @{}) {
  $script:msgId = $id++
  $payload = @{ id = $script:msgId; method = $method; params = $params } | ConvertTo-Json -Compress -Depth 12
  $bytes = [Text.Encoding]::UTF8.GetBytes($payload)
  $ws.SendAsync([ArraySegment[byte]]::new($bytes), [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $ct).Wait()
  $deadline = [DateTime]::UtcNow.AddSeconds(180)
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

$js = Get-Content -Raw "C:\Users\MerelyMe\Documents\MerelyMeSMP\discord-rebuild.js"
$r = Send-Cdp "Runtime.evaluate" @{ expression = $js; returnByValue = $true; awaitPromise = $true }
Write-Host ($r.result.value | ConvertTo-Json -Depth 10 -Compress)
$ws.Dispose()
