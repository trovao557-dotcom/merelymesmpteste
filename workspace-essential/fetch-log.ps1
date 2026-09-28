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

function Send-Cdp($method, $params) {
  $script:msgId = $id++
  $payload = @{ id = $script:msgId; method = $method; params = $params } | ConvertTo-Json -Compress -Depth 8
  $bytes = [Text.Encoding]::UTF8.GetBytes($payload)
  $segment = [ArraySegment[byte]]::new($bytes)
  $ws.SendAsync($segment, $handler, $true, $ct).Wait()
  $deadline = [DateTime]::UtcNow.AddSeconds(30)
  while ([DateTime]::UtcNow -lt $deadline) {
    $res = $ws.ReceiveAsync([ArraySegment[byte]]::new($buf), $ct).Result
    $text = [Text.Encoding]::UTF8.GetString($buf, 0, $res.Count)
    if ($text -notmatch '"id"\s*:\s*(\d+)') { continue }
    $msg = $text | ConvertFrom-Json
    if ($null -eq $msg.id) { continue }
    if ($msg.id -eq $script:msgId) {
      if ($msg.error) { throw $msg.error.message }
      return $msg.result
    }
  }
  throw "CDP timeout for $method"
}

Send-Cdp "Page.enable" @{} | Out-Null
Send-Cdp "Page.navigate" @{ url = "https://www.g-portal.com/eur/server/minecraft-ram/8699357" } | Out-Null
Start-Sleep -Seconds 10
$r = Send-Cdp "Runtime.evaluate" @{
  expression = "(() => { const a = [...document.querySelectorAll('a')].find(x => (x.innerText||'').includes('ftp://')); return a ? a.href : null; })()"
  returnByValue = $true
}
$base = $r.result.value.TrimEnd('/')
$ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "", $ct).Wait()
$paths = @("/logs/latest.log", "/latest.log")
foreach ($path in $paths) {
  $tmp = "$env:TEMP\latest-log.txt"
  if (Test-Path $tmp) { Remove-Item $tmp -Force }
  $code = curl.exe -s -w "%{http_code}" --ftp-pasv -o $tmp ($base + $path)
  if ((Test-Path $tmp) -and ((Get-Item $tmp).Length -gt 0)) {
    Write-Host "log from $path ($code)"
    Select-String -Path $tmp -Pattern "AhPriceHover|MerelyOrderEnchants|ClassNotFoundException" | Select-Object -Last 20 | ForEach-Object { $_.Line }
    break
  }
  Write-Host "missing $path code=$code"
}
