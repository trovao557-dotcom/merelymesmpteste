$ErrorActionPreference = 'Stop'
$minecraftPid = 14040
$sourceJar = 'C:\Users\MerelyMe\Documents\MerelyMeSMP\rank-menu-test\virtual-menu-helper\MerelyMe-Virtual-Menus-1.0.8.jar'
$modsDir = 'C:\Users\MerelyMe\AppData\Roaming\ModrinthApp\profiles\Just Minecraft 120.0\mods'
$statusFile = 'C:\Users\MerelyMe\Documents\MerelyMeSMP\rank-menu-test\virtual-menu-1.0.8-install-status.txt'

while (Get-Process -Id $minecraftPid -ErrorAction SilentlyContinue) {
    Start-Sleep -Seconds 2
}

Get-ChildItem -LiteralPath $modsDir -Filter 'MerelyMe-Virtual-Menus-*.jar' |
    Remove-Item -Force
Copy-Item -LiteralPath $sourceJar -Destination (Join-Path $modsDir 'MerelyMe-Virtual-Menus-1.0.8.jar') -Force
"Installed 1.0.8 at $(Get-Date -Format o)" | Set-Content -LiteralPath $statusFile -Encoding utf8
