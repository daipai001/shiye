param(
  [Parameter(Mandatory = $true)]
  [string]$BackupFile,
  [string]$DbHost = $(if ($env:DB_HOST) { $env:DB_HOST } else { '127.0.0.1' }),
  [string]$DbPort = $(if ($env:DB_PORT) { $env:DB_PORT } else { '3306' }),
  [string]$DbName = $(if ($env:RESTORE_DB_NAME) { $env:RESTORE_DB_NAME } else { 'shiye_restore_drill' }),
  [string]$DbUser = $(if ($env:RESTORE_DB_USER) { $env:RESTORE_DB_USER } else { $env:DB_USERNAME }),
  [string]$DbPassword = $(if ($env:RESTORE_DB_PASSWORD) { $env:RESTORE_DB_PASSWORD } else { $env:DB_PASSWORD })
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $BackupFile)) {
  throw "backup file not found: $BackupFile"
}
if (-not $DbUser) {
  throw 'RESTORE_DB_USER / DB_USERNAME is required'
}

$mysql = if ($env:MYSQL_BIN) { $env:MYSQL_BIN } else { 'mysql' }
$env:MYSQL_PWD = $DbPassword

$countBefore = & $mysql "-h$DbHost" "-P$DbPort" "-u$DbUser" -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DbName' AND table_type='BASE TABLE';"
if ($LASTEXITCODE -ne 0) {
  throw "cannot read target database '$DbName'"
}
if ([int]$countBefore -gt 0) {
  throw "target database '$DbName' is not empty ($countBefore tables); use a dedicated empty database"
}

# 使用 cmd 重定向，避免 PowerShell 管道按控制台编码破坏 utf8mb4 中文。
$import = "`"$mysql`" -h$DbHost -P$DbPort -u$DbUser --default-character-set=utf8mb4 $DbName < `"$BackupFile`""
& cmd.exe /c $import
if ($LASTEXITCODE -ne 0) {
  throw "mysql restore failed with exit code $LASTEXITCODE"
}

$countAfter = & $mysql "-h$DbHost" "-P$DbPort" "-u$DbUser" -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DbName' AND table_type='BASE TABLE';"
if ([int]$countAfter -le 0) {
  throw 'restore finished but target database has no tables'
}

$users = & $mysql "-h$DbHost" "-P$DbPort" "-u$DbUser" -N -e "SELECT COUNT(*) FROM $DbName.users;"
Write-Host "restore verification passed: tables=$countAfter users=$users database=$DbName"
