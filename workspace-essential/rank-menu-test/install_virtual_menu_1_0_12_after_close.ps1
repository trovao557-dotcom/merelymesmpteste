$ErrorActionPreference = 'Stop'
$minecraftPid = 6104
$sourceJar = 'C:\Users\MerelyMe\Documents\MerelyMeSMP\rank-menu-test\virtual-menu-helper\MerelyMe-Virtual-Menus-1.0.12.jar'
$profileDir = 'C:\Users\MerelyMe\AppData\Roaming\ModrinthApp\profiles\Just Minecraft 120.0'
$modsDir = Join-Path $profileDir 'mods'
$statusFile = 'C:\Users\MerelyMe\Documents\MerelyMeSMP\rank-menu-test\virtual-menu-1.0.12-install-status.txt'

while (Get-Process -Id $minecraftPid -ErrorAction SilentlyContinue) {
    Start-Sleep -Seconds 2
}

Get-ChildItem -LiteralPath $modsDir -Filter 'MerelyMe-Virtual-Menus-*.jar' |
    Remove-Item -Force
Copy-Item -LiteralPath $sourceJar -Destination (Join-Path $modsDir 'MerelyMe-Virtual-Menus-1.0.12.jar') -Force

$optionsPath = Join-Path $profileDir 'options.txt'
$options = Get-Content -LiteralPath $optionsPath
$options = $options -replace '^textBackgroundOpacity:.*$', 'textBackgroundOpacity:0.0'
Set-Content -LiteralPath $optionsPath -Value $options -Encoding utf8

"Installed 1.0.12 and removed text background at $(Get-Date -Format o)" |
    Set-Content -LiteralPath $statusFile -Encoding utf8


