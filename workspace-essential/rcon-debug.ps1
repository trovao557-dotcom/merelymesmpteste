$props = Get-Content 'current-check\_server.properties'
$port = [int](($props | Where-Object { $_ -like 'rcon.port=*' }) -replace '^rcon\.port=', '')
$password = (($props | Where-Object { $_ -like 'rcon.password=*' }) -replace '^rcon\.password=', '')
$client = [Net.Sockets.TcpClient]::new(); $client.Connect('144.91.119.134', $port); $stream = $client.GetStream()
function Read-Exact([IO.Stream]$streamIn, [int]$count) { $buffer=New-Object byte[] $count; $offset=0; while($offset -lt $count){$read=$streamIn.Read($buffer,$offset,$count-$offset); if($read -le 0){throw 'closed'}; [void]($offset+=$read)}; return ,$buffer }
function Send-Rcon([int]$id,[int]$type,[string]$body){$payload=[Text.Encoding]::UTF8.GetBytes($body+[char]0+[char]0);$ms=[IO.MemoryStream]::new();$bw=[IO.BinaryWriter]::new($ms);$bw.Write([int](4+4+$payload.Length));$bw.Write([int]$id);$bw.Write([int]$type);$bw.Write($payload);$bw.Flush();$bytes=$ms.ToArray();$stream.Write($bytes,0,$bytes.Length);$stream.Flush();$l=[BitConverter]::ToInt32((Read-Exact $stream 4),0);$r=Read-Exact $stream $l;[PSCustomObject]@{Id=[BitConverter]::ToInt32($r,0);Type=[BitConverter]::ToInt32($r,4);Body=[Text.Encoding]::UTF8.GetString($r,8,$r.Length-10)}}
$auth=Send-Rcon 100 3 $password; Write-Output ("auth id=$($auth.Id) type=$($auth.Type) body=<$($auth.Body)>")
$resp=Send-Rcon 200 2 'minecraft:gamerule showDeathMessages'; Write-Output ("gamerule id=$($resp.Id) type=$($resp.Type) body=<$($resp.Body)>")
$client.Close()
