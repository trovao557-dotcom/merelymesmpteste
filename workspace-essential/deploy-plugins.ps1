$ErrorActionPreference = "Stop"
$PORT = 9222
$list = Invoke-RestMethod "http://127.0.0.1:$PORT/json/list"
$tab = $list | Where-Object { $_.type -eq "page" -and $_.url -like "*8699357*" } | Select-Object -First 1
if (-not $tab) { $tab = $list | Where-Object { $_.type -eq "page" } | Select-Object -First 1 }

$ws = [System.Net.WebSockets.ClientWebSocket]::new()
$ct = [Threading.CancellationToken]::None
$ws.ConnectAsync([Uri]$tab.webSocketDebuggerUrl, $ct).Wait()

$id = 1
$results = @{}
$script:pending = @{}

$handler = [System.Net.WebSockets.WebSocketMessageType]::Text
$buf = New-Object byte[] 65536

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
    $msg = $text | ConvertFrom-Json
    if ($msg.id -eq $script:msgId) {
      if ($msg.error) { throw $msg.error.message }
      return $msg.result
    }
  }
  throw "CDP timeout for $method"
}

Send-Cdp "Page.enable" @{} | Out-Null
if ($tab.url -notlike "*8699357*") {
  Send-Cdp "Page.navigate" @{ url = "https://www.g-portal.com/eur/server/minecraft-ram/8699357" } | Out-Null
  Start-Sleep -Seconds 8
}
Send-Cdp "Runtime.evaluate" @{ expression = "window.scrollTo(0, document.body.scrollHeight)"; returnByValue = $true } | Out-Null
Start-Sleep -Seconds 2
$r = Send-Cdp "Runtime.evaluate" @{
  expression = "(() => { const a = [...document.querySelectorAll('a')].find(x => (x.innerText||'').includes('ftp://')); return a ? a.href : null; })()"
  returnByValue = $true
}
$base = $r.result.value
if (-not $base) { throw "FTP link not found" }
$base = $base.TrimEnd('/')

$uploads = @(
  @("C:\Users\MerelyMe\Documents\MerelyMeSMP\ah-price-hover\AhPriceHover.jar", "/plugins/AhPriceHover.jar"),
  @("C:\Users\MerelyMe\Documents\MerelyMeSMP\merely-order-enchants\MerelyOrderEnchants.jar", "/plugins/MerelyOrderEnchants.jar"),
  @("C:\Users\MerelyMe\Documents\EconomySMP\plugins\SKAuction\menu-browse.yml", "/plugins/SKAuction/menu-browse.yml")
)

foreach ($pair in $uploads) {
  $local = $pair[0]
  $remote = $pair[1]
  if (-not (Test-Path $local)) { Write-Host "skip missing $local"; continue }
  $size = (Get-Item $local).Length
  curl.exe -s --ftp-pasv -T $local ($base + $remote)
  Write-Host "uploaded $remote ($size bytes)"
}

$ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "", $ct).Wait()
Write-Host "done"
