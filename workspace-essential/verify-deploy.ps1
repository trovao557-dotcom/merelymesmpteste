$ErrorActionPreference = "Stop"
$PORT = 9222
$list = Invoke-RestMethod "http://127.0.0.1:$PORT/json/list"
$tab = $list | Where-Object { $_.type -eq "page" -and $_.url -like "*8699357*" -and $_.url -notlike "*console*" } | Select-Object -First 1
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
Send-Cdp "Runtime.evaluate" @{ expression = "window.scrollTo(0, document.body.scrollHeight)"; returnByValue = $true } | Out-Null
Start-Sleep -Seconds 2
$r = Send-Cdp "Runtime.evaluate" @{
  expression = "(() => { const a = [...document.querySelectorAll('a')].find(x => (x.innerText||'').includes('ftp://')); return a ? a.href : null; })()"
  returnByValue = $true
}
$base = $r.result.value.TrimEnd('/')
$ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "", $ct).Wait()

$out = "C:\Users\MerelyMe\Documents\MerelyMeSMP\_verify"
New-Item -ItemType Directory -Force -Path $out | Out-Null

# list plugins dir
curl.exe -s --ftp-pasv "$base/plugins/" -o "$out\plugins-list.txt"
Write-Host "=== plugins list (first lines) ==="
Get-Content "$out\plugins-list.txt" -ErrorAction SilentlyContinue | Select-String "AhPrice|SKAuction" | ForEach-Object { $_.Line }

# download jars and check sizes
curl.exe -s --ftp-pasv -o "$out\AhPriceHover.jar" "$base/plugins/AhPriceHover.jar"
curl.exe -s --ftp-pasv -o "$out\SKAuction.jar" "$base/plugins/SKAuction.jar"
Write-Host "live AhPriceHover:" (Get-Item "$out\AhPriceHover.jar").Length
Write-Host "live SKAuction:" (Get-Item "$out\SKAuction.jar").Length

# try logs
foreach ($p in @("/logs/latest.log","/latest.log","/logs/server.log")) {
  $dest = "$out\log.txt"
  Remove-Item $dest -Force -ErrorAction SilentlyContinue
  curl.exe -s --ftp-pasv -o $dest ($base + $p)
  if ((Test-Path $dest) -and ((Get-Item $dest).Length -gt 100)) {
    Write-Host "got log from $p size" (Get-Item $dest).Length
    Write-Host "=== AhPriceHover / plugin.yml / SKAuction ==="
    Select-String -Path $dest -Pattern "AhPriceHover|plugin.yml|MerelyOrder|Failed to patch|AH lore" | Select-Object -Last 40 | ForEach-Object { $_.Line }
    Write-Host "=== Done enabling plugins ==="
    Select-String -Path $dest -Pattern "Done \(|Enabling SKAuction|Enabling AhPrice" | Select-Object -Last 15 | ForEach-Object { $_.Line }
    break
  }
}
