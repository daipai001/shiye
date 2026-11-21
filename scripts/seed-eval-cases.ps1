param(
  [string]$UserApi = 'http://127.0.0.1:18080/api/v1',
  [string]$AdminApi = 'http://127.0.0.1:18081/admin/api/v1',
  [string]$AdminEmail = 'admin@shiye.local',
  [string]$AdminPassword = 'Shiye@2026!',
  [string]$Fixtures = (Join-Path $PSScriptRoot 'eval-fixtures.json')
)

$ErrorActionPreference = 'Stop'
$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$email = "eval-$stamp@shiye.local"
$password = 'EvalTest123'

function Post-Json([string]$Url, $Body, [string]$Token) {
  $headers = @{ 'Content-Type' = 'application/json; charset=utf-8' }
  if ($Token) { $headers.Authorization = "Bearer $Token" }
  $json = $Body | ConvertTo-Json -Depth 8 -Compress
  return Invoke-RestMethod -Uri $Url -Method Post -Headers $headers -Body ([Text.Encoding]::UTF8.GetBytes($json))
}

function Get-Json([string]$Url, [string]$Token) {
  return Invoke-RestMethod -Uri $Url -Headers @{ Authorization = "Bearer $Token" }
}

$fixture = Get-Content -LiteralPath $Fixtures -Raw -Encoding UTF8 | ConvertFrom-Json
$registration = Post-Json "$UserApi/auth/register" @{
  email = $email
  password = $password
  displayName = 'Eval Fixture'
} $null
$userToken = $registration.data.accessToken
$userId = $registration.data.user.id
Write-Host "eval user: $email (id=$userId)"

$articleIds = @{}
foreach ($article in $fixture.articles) {
  $collected = Post-Json "$UserApi/articles/collect" @{
    mode = 'CURRENT_PAGE'
    url = $article.url
    title = $article.title
    content = $article.content
  } $userToken
  $articleIds[$article.key] = [long]$collected.data.articleId
  Write-Host "article $($article.key) -> $($collected.data.articleId)"
}

$adminLogin = Post-Json "$AdminApi/auth/login" @{
  email = $AdminEmail
  password = $AdminPassword
} $null
$adminToken = $adminLogin.data.accessToken

$created = 0
foreach ($case in $fixture.cases) {
  Post-Json "$AdminApi/eval/cases" @{
    userId = [long]$userId
    question = $case.question
    expectedArticleIds = @($articleIds[$case.article])
    expectedChunkIds = @()
    goldAnswer = $case.goldAnswer
    tags = @('regression', $case.article)
    status = 'ACTIVE'
  } $adminToken | Out-Null
  $created += 1
}
Write-Host "eval cases created: $created"

$run = Post-Json "$AdminApi/eval/runs" @{
  userId = [long]$userId
  name = "30-case regression $stamp"
  topK = 5
  rerank = $true
} $adminToken
$runId = $run.data.id
Write-Host "eval run: $runId"

for ($i = 1; $i -le 60; $i++) {
  Start-Sleep -Seconds 10
  $detail = Get-Json "$AdminApi/eval/runs/$runId" $adminToken
  $status = $detail.data.status
  Write-Host "poll ${i}: $status"
  if ($status -eq 'DONE') {
    $detail.data | ConvertTo-Json -Depth 5
    exit 0
  }
}
throw "eval run $runId did not finish in 10 minutes"
