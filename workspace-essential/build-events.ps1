$ErrorActionPreference = 'Stop'
$jdk = 'C:\Users\MerelyMe\.jdks\openjdk-26.0.2.1\bin'
$libraries = Get-ChildItem 'merely-tournament\local-test\libraries' -Recurse -Filter '*.jar' | ForEach-Object FullName
$libraries += Get-ChildItem 'merely-tournament\local-test\plugins' -Filter '*.jar' | ForEach-Object FullName
$classpath = [string]::Join(';', $libraries)
$output = 'merely-events\build-v130'
$classes = Join-Path $output 'classes'
if (Test-Path -LiteralPath $classes) {
    Remove-Item -LiteralPath $classes -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& (Join-Path $jdk 'javac.exe') --release 21 -encoding UTF-8 -cp $classpath -d $classes 'merely-events\src\me\merelyme\events\MerelyEventsPlugin.java'
if ($LASTEXITCODE -ne 0) { throw 'MerelyEvents compilation failed' }
Copy-Item 'merely-events\plugin.yml' $classes -Force
Push-Location $classes
& (Join-Path $jdk 'jar.exe') --create --file '..\MerelyEvents.jar' -C . .
Pop-Location
if ($LASTEXITCODE -ne 0) { throw 'MerelyEvents packaging failed' }
Get-Item (Join-Path $output 'MerelyEvents.jar') | Select-Object FullName, Length, LastWriteTime
