param(
  [string]$OutputDir = (Join-Path $PSScriptRoot '..\dist')
)

$ErrorActionPreference = 'Stop'
$root = Join-Path $PSScriptRoot '..\frontend\user-extension'
$manifestPath = Join-Path $root 'manifest.json'
$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$zip = Join-Path $OutputDir "shiye-extension-$($manifest.version).zip"
if (Test-Path $zip) {
  Remove-Item $zip -Force
}

Push-Location $root
try {
  Compress-Archive -Path @('manifest.json', 'src', 'icons') -DestinationPath $zip -Force
} finally {
  Pop-Location
}

Write-Host "extension package created:"
Write-Host "  $zip"
