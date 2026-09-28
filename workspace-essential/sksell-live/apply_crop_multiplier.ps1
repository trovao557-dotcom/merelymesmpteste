$sourcePath = 'C:\Users\MerelyMe\Downloads\prices (1).txt'
$targetPath = Join-Path $PSScriptRoot 'prices.yml'

$lines = Get-Content -LiteralPath $sourcePath
$insideCrops = $false
$insideItems = $false
$changed = 0

for ($i = 0; $i -lt $lines.Count; $i++) {
    $line = $lines[$i]

    if ($line -match '^  crops:\s*$') {
        $insideCrops = $true
        continue
    }

    if ($insideCrops -and $line -match '^  [a-z][a-z0-9_-]*:\s*$' -and $line -notmatch '^  crops:\s*$') {
        $insideCrops = $false
        $insideItems = $false
    }

    if ($insideCrops -and $line -match '^    items:\s*$') {
        $insideItems = $true
        continue
    }

    if ($insideItems -and $line -match '^(      [A-Z0-9_]+:\s*)([0-9]+(?:\.[0-9]+)?)\s*$') {
        $oldPrice = [decimal]$Matches[2]
        $newPrice = $oldPrice * [decimal]2.2
        $formatted = $newPrice.ToString('0.##', [System.Globalization.CultureInfo]::InvariantCulture)
        $lines[$i] = $Matches[1] + $formatted
        $changed++
    }
}

if ($changed -ne 27) {
    throw "Expected 27 crop prices, changed $changed."
}

[System.IO.File]::WriteAllLines($targetPath, $lines, [System.Text.UTF8Encoding]::new($false))
Write-Output "Updated $changed crop prices in $targetPath"
