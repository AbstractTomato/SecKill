param(
    [int]$Count = 500,
    [long]$StartPhone = 18800000000,
    [string]$Password = "123456",
    [string]$MysqlExe = "E:\MySQL\bin\mysql.exe",
    [string]$MysqlUser = "root",
    [string]$MysqlPassword = "123456",
    [string]$Database = "seckill",
    [string]$RedisHost = "127.0.0.1",
    [int]$RedisPort = 6379,
    [string]$RedisPassword = "123456",
    [int]$TokenTtlMinutes = 120,
    [string]$OutFile = "target/loadtest-tokens.txt"
)

$ErrorActionPreference = "Stop"

function New-Md5 {
    param([string]$Text)

    $md5 = [System.Security.Cryptography.MD5]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
        $hash = $md5.ComputeHash($bytes)
        return ([System.BitConverter]::ToString($hash) -replace "-", "").ToLowerInvariant()
    } finally {
        $md5.Dispose()
    }
}

function Escape-Sql {
    param([string]$Text)
    return $Text.Replace("\", "\\").Replace("'", "''")
}

function Add-Bytes {
    param(
        [System.Collections.Generic.List[byte]]$Buffer,
        [byte[]]$Bytes
    )
    foreach ($b in $Bytes) {
        $Buffer.Add($b)
    }
}

function New-RedisCommandBytes {
    param([string[]]$CommandArgs)

    $buffer = [System.Collections.Generic.List[byte]]::new()
    Add-Bytes $buffer ([System.Text.Encoding]::ASCII.GetBytes("*$($CommandArgs.Count)`r`n"))
    foreach ($arg in $CommandArgs) {
        $argBytes = [System.Text.Encoding]::UTF8.GetBytes($arg)
        Add-Bytes $buffer ([System.Text.Encoding]::ASCII.GetBytes("`$$($argBytes.Length)`r`n"))
        Add-Bytes $buffer $argBytes
        Add-Bytes $buffer ([System.Text.Encoding]::ASCII.GetBytes("`r`n"))
    }
    return $buffer.ToArray()
}

function Read-RedisLine {
    param([System.Net.Sockets.NetworkStream]$Stream)

    $bytes = [System.Collections.Generic.List[byte]]::new()
    while ($true) {
        $value = $Stream.ReadByte()
        if ($value -lt 0) {
            throw "Redis connection closed unexpectedly."
        }
        if ($value -eq 13) {
            $lineFeed = $Stream.ReadByte()
            if ($lineFeed -ne 10) {
                throw "Invalid Redis response."
            }
            break
        }
        $bytes.Add([byte]$value)
    }
    return [System.Text.Encoding]::UTF8.GetString($bytes.ToArray())
}

function Invoke-RedisCommand {
    param(
        [System.Net.Sockets.NetworkStream]$Stream,
        [string[]]$CommandArgs
    )

    $bytes = New-RedisCommandBytes -CommandArgs $CommandArgs
    $Stream.Write($bytes, 0, $bytes.Length)
    $response = Read-RedisLine $Stream
    if ($response.StartsWith("-")) {
        throw "Redis command failed: $response"
    }
    return $response
}

if ($Count -le 0) {
    throw "Count must be greater than 0."
}

if (-not (Test-Path $MysqlExe)) {
    throw "mysql.exe not found: $MysqlExe"
}

$outDir = Split-Path -Parent $OutFile
if ($outDir) {
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
}
$outPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutFile)

$formPassword = New-Md5 $Password
$values = New-Object System.Collections.Generic.List[string]
$phones = New-Object System.Collections.Generic.List[string]

for ($i = 0; $i -lt $Count; $i++) {
    $phone = [string]($StartPhone + $i)
    if ($phone.Length -ne 11) {
        throw "Generated phone is not 11 digits: $phone"
    }

    $indexText = ($i + 1).ToString("000000")
    $nickname = "loadtest$indexText"
    $salt = "lt" + ($i + 1).ToString("00000000")
    $dbPassword = New-Md5 ($formPassword + $salt)

    $values.Add("('$phone','$(Escape-Sql $nickname)','$dbPassword','$salt',NOW())")
    $phones.Add($phone)
}

$insertSql = @"
SET NAMES utf8mb4;
INSERT INTO ``user`` (phone, nickname, password, salt, register_date)
VALUES
$($values -join ",`n")
ON DUPLICATE KEY UPDATE
    nickname = VALUES(nickname),
    password = VALUES(password),
    salt = VALUES(salt);
"@

Write-Host "Upserting $Count load-test users into MySQL..."
$insertSql | & $MysqlExe "-u$MysqlUser" "-p$MysqlPassword" "--default-character-set=utf8mb4" $Database | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Failed to upsert users into MySQL."
}

$endPhone = [string]($StartPhone + $Count - 1)
$selectSql = @"
SET NAMES utf8mb4;
SELECT id, phone, nickname
FROM ``user``
WHERE phone >= '$StartPhone' AND phone <= '$endPhone'
ORDER BY phone;
"@

Write-Host "Reading generated users from MySQL..."
$rows = $selectSql | & $MysqlExe "-u$MysqlUser" "-p$MysqlPassword" "--default-character-set=utf8mb4" "--batch" "--skip-column-names" $Database
if ($LASTEXITCODE -ne 0) {
    throw "Failed to query generated users from MySQL."
}

$users = @()
foreach ($row in $rows) {
    if ([string]::IsNullOrWhiteSpace($row)) {
        continue
    }

    $parts = $row -split "`t"
    if ($parts.Count -lt 3) {
        throw "Unexpected MySQL row: $row"
    }

    $users += [pscustomobject]@{
        Id = [long]$parts[0]
        Phone = $parts[1]
        Nickname = $parts[2]
    }
}

if ($users.Count -ne $Count) {
    throw "Expected $Count users, but queried $($users.Count)."
}

Write-Host "Writing Redis token sessions..."
$client = [System.Net.Sockets.TcpClient]::new()
$client.ReceiveTimeout = 5000
$client.SendTimeout = 5000
$client.Connect($RedisHost, $RedisPort)

try {
    $stream = $client.GetStream()
    $stream.ReadTimeout = 5000
    $stream.WriteTimeout = 5000
    if (-not [string]::IsNullOrWhiteSpace($RedisPassword)) {
        Invoke-RedisCommand -Stream $stream -CommandArgs @("AUTH", $RedisPassword) | Out-Null
    }

    $ttlSeconds = $TokenTtlMinutes * 60
    $tokens = New-Object System.Collections.Generic.List[string]
    foreach ($user in $users) {
        $token = [guid]::NewGuid().ToString("N")
        $json = "{`"id`":$($user.Id),`"phone`":`"$($user.Phone)`",`"nickname`":`"$($user.Nickname)`"}"
        Invoke-RedisCommand -Stream $stream -CommandArgs @("SET", "token:$token", $json, "EX", [string]$ttlSeconds) | Out-Null
        $tokens.Add($token)
    }
} finally {
    $client.Close()
}

[System.IO.File]::WriteAllLines($outPath, $tokens, [System.Text.UTF8Encoding]::new($false))

Write-Host ""
Write-Host "Done."
Write-Host "Users: $Count"
Write-Host "Password: $Password"
Write-Host "Token TTL: $TokenTtlMinutes minutes"
Write-Host "Token file: $outPath"
Write-Host ""
Write-Host "Paste the token file content into the test console token textarea."
