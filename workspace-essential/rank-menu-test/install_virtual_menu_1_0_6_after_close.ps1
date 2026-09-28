$minecraftPid = 15780
$oldJar = 'C:\Users\MerelyMe\AppData\Roaming\ModrinthApp\profiles\Just Minecraft 120.0\mods\MerelyMe-Virtual-Menus-1.0.5.jar'
$newJar = 'C:\Users\MerelyMe\Documents\MerelyMeSMP\rank-menu-test\virtual-menu-helper\MerelyMe-Virtual-Menus-1.0.6.jar'
$installedJar = 'C:\Users\MerelyMe\AppData\Roaming\ModrinthApp\profiles\Just Minecraft 120.0\mods\MerelyMe-Virtual-Menus-1.0.6.jar'
$statusFile = 'C:\Users\MerelyMe\Documents\MerelyMeSMP\rank-menu-test\virtual-menu-install-status.txt'

Wait-Process -Id $minecraftPid -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $oldJar -Force -ErrorAction SilentlyContinue
Copy-Item -LiteralPath $newJar -Destination $installedJar -Force
Set-Content -LiteralPath $statusFile -Value "Installed 1.0.6 at $(Get-Date -Format o)"
