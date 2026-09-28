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

function Click-Label($want) {
  $expr = @"
(() => {
  const want = '$want';
  const nodes = [...document.querySelectorAll('button, a, [role=button], span')];
  const hit = nodes.find(el => {
    const t = (el.innerText||el.textContent||'').trim();
    return t.toUpperCase() === want;
  });
  if (!hit) return 'missing:' + want;
  hit.click();
  return 'clicked:' + want;
})()
"@
  $r = Send-Cdp "Runtime.evaluate" @{ expression = $expr; returnByValue = $true }
  return $r.result.value
}

Send-Cdp "Page.enable" | Out-Null
Send-Cdp "Page.navigate" @{ url = "https://www.g-portal.com/eur/server/minecraft-ram/8699357" } | Out-Null
Start-Sleep -Seconds 10

Write-Host (Click-Label "PARAR")
Start-Sleep -Seconds 2
Write-Host (Click-Label "PARAR")
Start-Sleep -Seconds 2
Write-Host (Click-Label "SIM")
Start-Sleep -Seconds 2
Write-Host (Click-Label "OK")

# get ftp
Send-Cdp "Runtime.evaluate" @{ expression = "window.scrollTo(0, document.body.scrollHeight)"; returnByValue = $true } | Out-Null
Start-Sleep -Seconds 2
$r = Send-Cdp "Runtime.evaluate" @{
  expression = "(() => { const a = [...document.querySelectorAll('a')].find(x => (x.innerText||'').includes('ftp://')); return a ? a.href : null; })()"
  returnByValue = $true
}
$base = $r.result.value
if (-not $base) { throw "no ftp" }
$base = $base.TrimEnd('/')
$ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "", $ct).Wait()
Write-Host "ftp ok"

Write-Host "waiting for server stop..."
Start-Sleep -Seconds 25

$uploads = @(
  @("C:\Users\MerelyMe\Documents\MerelyMeSMP\skauction-patch\SKAuction-patched.jar", "/plugins/SKAuction.jar"),
  @("C:\Users\MerelyMe\Documents\MerelyMeSMP\ah-price-hover\AhPriceHover.jar", "/plugins/AhPriceHover.jar"),
  @("C:\Users\MerelyMe\Documents\MerelyMeSMP\merely-order-enchants\MerelyOrderEnchants.jar", "/plugins/MerelyOrderEnchants.jar"),
  @("C:\Users\MerelyMe\Documents\EconomySMP\plugins\SKAuction\settings.yml", "/plugins/SKAuction/settings.yml")
)
foreach ($pair in $uploads) {
  curl.exe -s --ftp-pasv -T $pair[0] ($base + $pair[1])
  Write-Host "ok" $pair[1] (Get-Item $pair[0]).Length
}

# verify size on ftp
$out = "C:\Users\MerelyMe\Documents\MerelyMeSMP\_verify"
New-Item -ItemType Directory -Force -Path $out | Out-Null
curl.exe -s --ftp-pasv "$base/plugins/" -o "$out\plugins-list.txt"
Select-String -Path "$out\plugins-list.txt" -Pattern "AhPriceHover|SKAuction.jar" | ForEach-Object { $_.Line }

Write-Host "STARTING"
# reopen cdp to click start
$tab2 = (Invoke-RestMethod "http://127.0.0.1:$PORT/json/list") | Where-Object { $_.type -eq "page" -and $_.url -like "*8699357*" } | Select-Object -First 1
$ws = [System.Net.WebSockets.ClientWebSocket]::new()
$ws.ConnectAsync([Uri]$tab2.webSocketDebuggerUrl, $ct).Wait()
$id = 1
Send-Cdp "Page.enable" | Out-Null
Write-Host (Click-Label "INICIAR")
Start-Sleep -Seconds 2
Write-Host (Click-Label "INICIAR")
Start-Sleep -Seconds 1
Write-Host (Click-Label "SIM")
$ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "", $ct).Wait()
Write-Host "DONE"
