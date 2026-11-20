param(
  [string]$BackupDir = (Join-Path $PSScriptRoot '..\backups'),
  [int]$KeepDays = 7
)

$ErrorActionPreference = 'Stop'

$dbHost = if ($env:DB_HOST) { $env:DB_HOST } else { '127.0.0.1' }
$dbPort = if ($env:DB_PORT) { $env:DB_PORT } else { '3306' }
$dbName = if ($env:DB_NAME) { $env:DB_NAME } else { 'shiye' }
$dbUser = if ($env:DB_USERNAME) { $env:DB_USERNAME } else { 'root' }
$redisHost = if ($env:REDIS_HOST) { $env:REDIS_HOST } else { '127.0.0.1' }
$redisPort = if ($env:REDIS_PORT) { $env:REDIS_PORT } else { '6379' }

New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$dbFile = Join-Path $BackupDir "shiye-db-$stamp.sql"
$redisFile = Join-Path $BackupDir "redis-$stamp.rdb"

$env:MYSQL_PWD = $env:DB_PASSWORD
& mysqldump `
  "--host=$dbHost" "--port=$dbPort" "--user=$dbUser" `
  --single-transaction --routines --triggers --no-tablespaces `
  --default-character-set=utf8mb4 `
  "--result-file=$dbFile" $dbName
if ($LASTEXITCODE -ne 0) {
  throw "mysqldump failed with exit code $LASTEXITCODE"
}

$redisArgs = @('-h', $redisHost, '-p', $redisPort, '--rdb', $redisFile)
if ($env:REDIS_PASSWORD) {
  $env:REDISCLI_AUTH = $env:REDIS_PASSWORD
}
& redis-cli @redisArgs
if ($LASTEXITCODE -ne 0) {
  throw "redis-cli --rdb failed with exit code $LASTEXITCODE"
}

Get-ChildItem -LiteralPath $BackupDir -File |
  Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$KeepDays) } |
  Remove-Item -Force

Write-Host "backup completed:"
Write-Host "  $dbFile"
Write-Host "  $redisFile"
