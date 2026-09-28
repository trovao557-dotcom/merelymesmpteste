param(
    [Parameter(Mandatory = $true)][string]$LocalFile,
    [Parameter(Mandatory = $true)][string]$RemotePath
)

$ErrorActionPreference = "Stop"
$PORT = 9222
$list = Invoke-RestMethod "http://127.0.0.1:$PORT/json/list"
$tab = $list | Where-Object { $_.type -eq "page" -and $_.url -like "*8699357*" } | Select-Object -First 1
if (-not $tab) { throw "no g-portal tab" }

$uri = [Uri]$tab.webSocketDebuggerUrl
$ws = New-Object System.Net.WebSockets.ClientWebSocket
$ws.ConnectAsync($uri, [Threading.CancellationToken]::None).Wait()

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

Send-Cdp "Page.enable" @{} | Out-Null
Send-Cdp "Runtime.enable" @{} | Out-Null
Send-Cdp "Page.navigate" @{ url = "https://www.g-portal.com/eur/server/minecraft-ram/8699357" } | Out-Null
Start-Sleep -Seconds 6
Send-Cdp "Runtime.evaluate" @{ expression = "window.scrollTo(0, document.body.scrollHeight)"; returnByValue = $true } | Out-Null
Start-Sleep -Seconds 2
$r = Send-Cdp "Runtime.evaluate" @{
    expression = "(() => { const a = [...document.querySelectorAll('a')].find(x => (x.innerText||'').includes('ftp://')); return a ? a.href : null; })()"
    returnByValue = $true
}
$ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "", [Threading.CancellationToken]::None).Wait()

$ftpBase = $r.result.value
if (-not $ftpBase) { throw "FTP link not found on G-Portal" }
$url = ($ftpBase.TrimEnd("/")) + $RemotePath
& curl.exe -s --ftp-pasv -T $LocalFile $url
$size = (Get-Item $LocalFile).Length
Write-Host "uploaded $RemotePath bytes $size"
