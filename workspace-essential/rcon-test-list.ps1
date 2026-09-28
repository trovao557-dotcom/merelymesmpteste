$props = Get-Content 'current-check\_server.properties'
$hostName = '144.91.119.134'
$port = [int](($props | Where-Object { $_ -like 'rcon.port=*' }) -replace '^rcon\.port=', '')
$password = (($props | Where-Object { $_ -like 'rcon.password=*' }) -replace '^rcon\.password=', '')

$client = [Net.Sockets.TcpClient]::new()
$client.Connect($hostName, $port)
$stream = $client.GetStream()

function Read-Exact([IO.Stream]$streamIn, [int]$count) {
    $buffer = New-Object byte[] $count
    $offset = 0
    while ($offset -lt $count) {
        $read = $streamIn.Read($buffer, $offset, $count - $offset)
        if ($read -le 0) { throw 'RCON connection closed.' }
        [void]($offset += $read)
    }
    return ,$buffer
}

function Send-Rcon([int]$id, [int]$type, [string]$body) {
    $payload = [Text.Encoding]::UTF8.GetBytes($body + [char]0 + [char]0)
    $length = 4 + 4 + $payload.Length
    $packet = [IO.MemoryStream]::new()
    $writer = [IO.BinaryWriter]::new($packet)
    $writer.Write([int]$length)
    $writer.Write([int]$id)
    $writer.Write([int]$type)
    $writer.Write($payload)
    $writer.Flush()
    $bytes = $packet.ToArray()
    $stream.Write($bytes, 0, $bytes.Length)
    $stream.Flush()
    $responseLength = [BitConverter]::ToInt32((Read-Exact $stream 4), 0)
    $response = Read-Exact $stream $responseLength
    [PSCustomObject]@{
        Id = [BitConverter]::ToInt32($response, 0)
        Type = [BitConverter]::ToInt32($response, 4)
        Body = [Text.Encoding]::UTF8.GetString($response, 8, $response.Length - 10)
    }
}

$auth = Send-Rcon 100 3 $password
if ($auth.Id -eq -1) { throw 'RCON authentication failed.' }

foreach ($command in @(
    'execute in minecraft:overworld run gamerule showDeathMessages true',
    'execute in minecraft:the_nether run gamerule showDeathMessages true',
    'execute in minecraft:the_end run gamerule showDeathMessages true'
)) {
    [void](Send-Rcon (Get-Random -Minimum 200 -Maximum 2000000000) 2 $command)
}

$checks = foreach ($world in @('overworld', 'the_nether', 'the_end')) {
    $reply = Send-Rcon (Get-Random -Minimum 200 -Maximum 2000000000) 2 "execute in minecraft:$world run gamerule showDeathMessages"
    "$world = $($reply.Body.Trim())"
}

$client.Close()
Write-Output ($checks -join '; ')

