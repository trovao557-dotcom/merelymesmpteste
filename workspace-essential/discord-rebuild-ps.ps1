$ErrorActionPreference = "Stop"
$PORT = 9222
$cfgPath = "C:\Users\MerelyMe\Documents\MerelyMeSMP\discord-structure.json"
$cfg = [System.IO.File]::ReadAllText($cfgPath, [Text.Encoding]::UTF8) | ConvertFrom-Json
$GUILD = [string]$cfg.guildId

$tab = (Invoke-RestMethod "http://127.0.0.1:$PORT/json/list") | Where-Object { $_.url -like "*discord.com*" } | Select-Object -First 1
$ws = [System.Net.WebSockets.ClientWebSocket]::new()
$ct = [Threading.CancellationToken]::None
$ws.ConnectAsync([Uri]$tab.webSocketDebuggerUrl, $ct).Wait()
$id = 1
$buf = New-Object byte[] 4194304
function Send-Cdp($method, $params = @{}) {
  $script:msgId = $id++
  $payload = @{ id = $script:msgId; method = $method; params = $params } | ConvertTo-Json -Compress -Depth 8
  $bytes = [Text.Encoding]::UTF8.GetBytes($payload)
  $ws.SendAsync([ArraySegment[byte]]::new($bytes), [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $ct).Wait()
  $deadline = [DateTime]::UtcNow.AddSeconds(30)
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
  throw "timeout"
}
$tokRes = Send-Cdp "Runtime.evaluate" @{
  expression = '(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()'
  returnByValue = $true
}
$token = $tokRes.result.value
$ws.Dispose()
if (-not $token) { throw "no token" }
Write-Host "auth ok"

function Invoke-DApi($Method, $Path, $BodyObj = $null) {
  $uri = "https://discord.com/api/v9$Path"
  for ($a = 0; $a -lt 8; $a++) {
    try {
      $headers = @{ Authorization = $token }
      if ($null -eq $BodyObj) {
        return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers
      }
      $headers["Content-Type"] = "application/json; charset=utf-8"
      $bytes = [Text.Encoding]::UTF8.GetBytes(($BodyObj | ConvertTo-Json -Depth 20 -Compress))
      return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers -Body $bytes
    } catch {
      $code = 0
      try { $code = [int]$_.Exception.Response.StatusCode } catch {}
      if ($code -eq 429) { Start-Sleep -Seconds 3; continue }
      Write-Host ("API fail " + $Method + " " + $Path + " :: " + $_.Exception.Message)
      Start-Sleep -Milliseconds 700
      if ($a -eq 7) { return $null }
    }
  }
}

$channels = Invoke-DApi GET "/guilds/$GUILD/channels"
$roles = Invoke-DApi GET "/guilds/$GUILD/roles"
Write-Host ("current ch=" + $channels.Count + " roles=" + $roles.Count)

Write-Host "delete channels"
foreach ($c in $channels) {
  try {
    Invoke-RestMethod -Method DELETE -Uri ("https://discord.com/api/v9/channels/" + $c.id) -Headers @{ Authorization = $token } | Out-Null
    Write-Host ("del " + $c.id)
  } catch { Write-Host ("del-fail " + $c.id) }
  Start-Sleep -Milliseconds 500
}

Write-Host "delete roles"
foreach ($r in ($roles | Sort-Object position -Descending)) {
  if ($r.id -eq $GUILD -or $r.name -eq "@everyone") { continue }
  if ($r.name -match "Jockie|Carl|Ticket|Statbot|Wick|Dyno|MEE6") { Write-Host ("keep " + $r.name); continue }
  try {
    Invoke-RestMethod -Method DELETE -Uri ("https://discord.com/api/v9/guilds/$GUILD/roles/" + $r.id) -Headers @{ Authorization = $token } | Out-Null
    Write-Host ("del-role " + $r.id)
  } catch { Write-Host ("role-fail " + $r.id) }
  Start-Sleep -Milliseconds 500
}

Write-Host "create roles"
$createdRoles = @{}
foreach ($rd in $cfg.roles) {
  $r = Invoke-DApi POST "/guilds/$GUILD/roles" @{
    name = [string]$rd.name
    color = [int]$rd.color
    hoist = [bool]$rd.hoist
    mentionable = [bool]$rd.mentionable
    permissions = "0"
  }
  if ($r) { $createdRoles[[string]$rd.key] = [string]$r.id; Write-Host ("role " + $rd.key) }
  else { Write-Host ("role-fail " + $rd.key) }
  Start-Sleep -Milliseconds 550
}

$denyView = 1024
$allowView = 1024
$allowSend = 2048
$denySend = 2048
$staffIds = @($createdRoles["owner"], $createdRoles["admin"], $createdRoles["mod"], $createdRoles["helper"], $createdRoles["ticket"]) | Where-Object { $_ }

$staffOW = New-Object System.Collections.ArrayList
[void]$staffOW.Add(@{ id = $GUILD; type = 0; allow = "0"; deny = "$denyView" })
foreach ($sid in $staffIds) { [void]$staffOW.Add(@{ id = $sid; type = 0; allow = "$allowView"; deny = "0" }) }

$announceOW = New-Object System.Collections.ArrayList
[void]$announceOW.Add(@{ id = $GUILD; type = 0; allow = "$allowView"; deny = "$denySend" })
foreach ($sid in @($createdRoles["owner"], $createdRoles["admin"], $createdRoles["mod"])) {
  if ($sid) { [void]$announceOW.Add(@{ id = $sid; type = 0; allow = "$($allowView -bor $allowSend)"; deny = "0" }) }
}

$ownerOW = New-Object System.Collections.ArrayList
[void]$ownerOW.Add(@{ id = $GUILD; type = 0; allow = "0"; deny = "$denyView" })
foreach ($sid in @($createdRoles["owner"], $createdRoles["admin"])) {
  if ($sid) { [void]$ownerOW.Add(@{ id = $sid; type = 0; allow = "$($allowView -bor $allowSend)"; deny = "0" }) }
}

Write-Host "create channels"
$createdCh = @{}
$pos = 0
foreach ($block in $cfg.categories) {
  $catBody = @{ name = [string]$block.name; type = 4; position = $pos }
  $pos++
  if ($block.staffOnly) { $catBody.permission_overwrites = @($staffOW) }
  $cat = Invoke-DApi POST "/guilds/$GUILD/channels" $catBody
  if (-not $cat) { Write-Host "cat-fail"; continue }
  Write-Host "cat-ok"
  Start-Sleep -Milliseconds 600
  foreach ($ch in $block.channels) {
    $body = @{
      name = [string]$ch.name
      type = [int]$ch.type
      parent_id = [string]$cat.id
      position = $pos
    }
    $pos++
    if ($ch.topic) { $body.topic = [string]$ch.topic }
    if ($ch.ownerOnly) { $body.permission_overwrites = @($ownerOW) }
    elseif ($ch.announce) { $body.permission_overwrites = @($announceOW) }
    elseif ($block.staffOnly) { $body.permission_overwrites = @($staffOW) }
    $created = Invoke-DApi POST "/guilds/$GUILD/channels" $body
    if ($created) {
      $createdCh[[string]$ch.key] = [string]$created.id
      Write-Host ("ch " + $ch.key)
    } else {
      Write-Host ("ch-fail " + $ch.key)
    }
    Start-Sleep -Milliseconds 600
  }
}

Write-Host "seed messages"
$msgs = $cfg.messages
$seed = @(
  @{ key = "announcements"; text = [string]$msgs.announcements },
  @{ key = "rules"; text = [string]$msgs.rules },
  @{ key = "iplinks"; text = [string]$msgs.iplinks },
  @{ key = "ticket"; text = [string]$msgs.ticket },
  @{ key = "chat"; text = ([string]$msgs.chat) + " <#" + $createdCh["rules"] + ">" }
)
foreach ($s in $seed) {
  $cid = $createdCh[$s.key]
  if (-not $cid) { continue }
  Invoke-DApi POST "/channels/$cid/messages" @{ content = $s.text } | Out-Null
  Write-Host ("msg " + $s.key)
  Start-Sleep -Milliseconds 450
}

Invoke-DApi PATCH "/guilds/$GUILD" @{ description = "MerelyMeSMP | First Season EU | Survival Economy | Lock in." } | Out-Null
Write-Host ("DONE roles=" + $createdRoles.Count + " channels=" + $createdCh.Count)
