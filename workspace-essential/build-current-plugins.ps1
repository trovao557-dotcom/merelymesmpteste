$ErrorActionPreference = 'Stop'
$jdk = 'C:\Users\MerelyMe\.jdks\openjdk-26.0.2.1\bin'
$libraries = Get-ChildItem 'merely-tournament\local-test\libraries' -Recurse -Filter '*.jar' | ForEach-Object FullName
$libraries += Get-ChildItem 'merely-tournament\local-test\plugins' -Filter '*.jar' | ForEach-Object FullName
$classpath = [string]::Join(';', $libraries)
$jobs = @(
    @{ Source = 'merely-claims\src'; Output = 'merely-claims\build-v160-new'; Jar = 'MerelyClaims.jar' },
    @{ Source = 'merely-daily\src'; Output = 'merely-daily\build-v130-new'; Jar = 'MerelyDaily.jar' },
    @{ Source = 'merely-tournament\src'; Output = 'merely-tournament\build-v131-new'; Jar = 'MerelyTournament.jar' },
    @{ Source = 'merely-battlepass\src'; Output = 'merely-battlepass\build'; Jar = 'MerelyBattlePass.jar' }
)
foreach ($job in $jobs) {
    $classes = Join-Path $job.Output 'classes'
    if (Test-Path -LiteralPath $classes) {
        Remove-Item -LiteralPath $classes -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $classes | Out-Null
    $sources = Get-ChildItem (Join-Path $job.Source 'main\java') -Recurse -Filter '*.java' | ForEach-Object FullName
    # GPortal currently runs Java 25. Target Java 21 so these plugins also remain
    # compatible with the Paper baseline used by the server.
    & (Join-Path $jdk 'javac.exe') --release 21 -encoding UTF-8 -cp $classpath -d $classes $sources
    if ($LASTEXITCODE -ne 0) { throw "Compilation failed for $($job.Source)" }
    Copy-Item (Join-Path $job.Source 'main\resources\*') $classes -Recurse -Force
    Push-Location $classes
    & (Join-Path $jdk 'jar.exe') --create --file (Join-Path '..' $job.Jar) -C . .
    Pop-Location
    if ($LASTEXITCODE -ne 0) { throw "Packaging failed for $($job.Jar)" }
    Get-Item (Join-Path $job.Output $job.Jar) | Select-Object FullName, Length, LastWriteTime
}
