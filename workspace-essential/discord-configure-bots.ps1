# Configure Discord bots: raise bot roles, Carl autorole Member, Ticket Tool panel setup hints
$ErrorActionPreference = "Stop"
$GUILD = "1534136666984419348"
$MEMBER_ROLE = "1544904296439619614"
$TICKET_STAFF = "1544904309882232842"
$CARL_ROLE = "1544907267994816583"
$TT_ROLE = "1544909536194986048"
$STAT_ROLE = "1544906133536514149"
$BOT_CMDS = "1544904353121308672"
$CREATE_TICKET = "1544904386587787334"
$LOGS = "1544904420536483861"

$PORT = 9222
$tab = (Invoke-RestMethod "http://127.0.0.1:$PORT/json/list") | Where-Object { $_.url -like "*discord.com/channels/$GUILD*" } | Select-Object -First 1
if (-not $tab) { throw "discord guild tab missing" }

$ws = [System.Net.WebSockets.ClientWebSocket]::new()
$ct = [Threading.CancellationToken]::None
$ws.ConnectAsync([Uri]$tab.webSocketDebuggerUrl, $ct).Wait()
$id = 1
$buf = New-Object byte[] 8388608
function Send-Cdp($method, $params = @{}) {
  $script:msgId = $id++
  $payload = @{ id = $script:msgId; method = $method; params = $params } | ConvertTo-Json -Compress -Depth 10
  $bytes = [Text.Encoding]::UTF8.GetBytes($payload)
  $ws.SendAsync([ArraySegment[byte]]::new($bytes), [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $ct).Wait()
  $deadline = [DateTime]::UtcNow.AddSeconds(25)
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
function Eval($expr) {
  (Send-Cdp "Runtime.evaluate" @{ expression = $expr; returnByValue = $true }).result.value
}

$tok = Eval @'
(()=>{const i=document.createElement("iframe");document.body.appendChild(i);let t=i.contentWindow.localStorage.getItem("token");i.remove();return t?t.replace(/^"|"$/g,""):null;})()
'@
if (-not $tok) { throw "no discord token" }
$h = @{ Authorization = $tok; "Content-Type" = "application/json" }

# Raise bot roles above Member so they can assign it
# Fetch roles and find Member position
$roles = Invoke-RestMethod -Uri "https://discord.com/api/v9/guilds/$GUILD/roles" -Headers @{ Authorization = $tok }
$memberPos = ($roles | Where-Object { $_.id -eq $MEMBER_ROLE }).position
$ownerPos = ($roles | Where-Object { $_.name -match "Owner" }).position
Write-Host "Member pos=$memberPos Owner pos=$ownerPos"

# Discord PATCH role position: array of {id, position}
# Put bots just below Owner/Admin/Mod stack - above Member
$targetPos = [Math]::Max($memberPos + 3, 2)
foreach ($rid in @($CARL_ROLE, $TT_ROLE, $STAT_ROLE)) {
  try {
    $body = "[{`"id`":`"$rid`",`"position`":$targetPos}]"
    Invoke-RestMethod -Method Patch -Uri "https://discord.com/api/v9/guilds/$GUILD/roles" -Headers $h -Body $body | Out-Null
    Write-Host "Raised role $rid -> $targetPos"
    $targetPos++
    Start-Sleep -Milliseconds 400
  } catch {
    Write-Host "role raise fail $rid : $($_.Exception.Message)"
  }
}

# Send Carl autorole command in bot-commands
$autoroleMsg = @{
  content = "?autorole add <@&$MEMBER_ROLE>"
} | ConvertTo-Json -Compress
$r1 = Invoke-RestMethod -Method Post -Uri "https://discord.com/api/v9/channels/$BOT_CMDS/messages" -Headers $h -Body $autoroleMsg
Write-Host "autorole cmd msg=$($r1.id)"
Start-Sleep 2

# Also try joinrole alias
$joinMsg = @{
  content = "?joinrole add <@&$MEMBER_ROLE>"
} | ConvertTo-Json -Compress
$r2 = Invoke-RestMethod -Method Post -Uri "https://discord.com/api/v9/channels/$BOT_CMDS/messages" -Headers $h -Body $joinMsg
Write-Host "joinrole cmd msg=$($r2.id)"
Start-Sleep 2

# Logging to logs channel
$logMsg = @{
  content = "?log channel <#${LOGS}>"
} | ConvertTo-Json -Compress
try {
  $r3 = Invoke-RestMethod -Method Post -Uri "https://discord.com/api/v9/channels/$BOT_CMDS/messages" -Headers $h -Body $logMsg
  Write-Host "log cmd msg=$($r3.id)"
} catch { Write-Host "log cmd fail" }

# Update create-ticket channel message / clean placeholder
$ticketMsg = @{
  content = @"
**NEED HELP? Open a ticket.**

Use the **Ticket Tool** panel below (or coming next) for:
• Buy / payment
• Ban / mute appeals
• Bugs & reports
• Staff applications

Staff: <@&$TICKET_STAFF> · <@&$(($roles|?{$_.name -match 'Mod'}).id)>
"@
} | ConvertTo-Json -Compress
$r4 = Invoke-RestMethod -Method Post -Uri "https://discord.com/api/v9/channels/$CREATE_TICKET/messages" -Headers $h -Body $ticketMsg
Write-Host "ticket channel msg=$($r4.id)"

# Verify Statbot member
try {
  $sb = Invoke-RestMethod -Uri "https://discord.com/api/v9/guilds/$GUILD/members/491769129318088714" -Headers @{ Authorization = $tok }
  Write-Host "STATBOT OK: $($sb.user.username)"
} catch {
  Write-Host "STATBOT not in guild yet"
}

# Re-list roles
$roles2 = Invoke-RestMethod -Uri "https://discord.com/api/v9/guilds/$GUILD/roles" -Headers @{ Authorization = $tok }
Write-Host "=== ROLES AFTER ==="
$roles2 | Sort-Object position -Descending | ForEach-Object { Write-Host "$($_.position) $($_.name)" }

$ws.Dispose()
Write-Host "DONE"
