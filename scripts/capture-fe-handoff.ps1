<#
.SYNOPSIS
  Captures the FE handoff artifacts from a RUNNING server:
    - docs/frontend/openapi.json            (full API spec from /v3/api-docs)
    - docs/frontend/fixtures/*.json         (real wire responses, saved byte-for-byte)

.NOTES
  Run AFTER seeding data with the docs/api-testing Postman collection (fresh or reused
  account) so the fixtures actually contain insights/suggestions/notifications.
  Defaults match docs/api-testing/adaptive-engine.postman_environment.json:
  scenario month = May 2026, hence -AsOf 2026-05-15 and budgets month=5&year=2026.

.EXAMPLE
  .\scripts\capture-fe-handoff.ps1
  .\scripts\capture-fe-handoff.ps1 -Email you@example.com -Password secret
#>
param(
    [string]$BaseUrl  = "http://localhost:8080",
    [string]$Email    = "vtvuong22@clc.fitus.edu.vn",
    [string]$Password = "test@123",
    [string]$AsOf     = "2026-05-15",
    [int]$Month       = 5,
    [int]$Year        = 2026
)

$ErrorActionPreference = "Stop"
$repoRoot   = Split-Path -Parent $PSScriptRoot
$outDir     = Join-Path $repoRoot "docs\frontend"
$fixtureDir = Join-Path $outDir "fixtures"
New-Item -ItemType Directory -Force $fixtureDir | Out-Null

# Save the exact wire bytes (no re-serialization) so fixtures are the literal contract.
function Save-Raw([string]$Url, [string]$Path, [hashtable]$Headers) {
    $resp = Invoke-WebRequest -Uri $Url -Headers $Headers -UseBasicParsing
    [System.IO.File]::WriteAllText($Path, $resp.Content, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "  wrote $Path"
}

Write-Host "1/3 OpenAPI spec..."
Save-Raw "$BaseUrl/v3/api-docs" (Join-Path $outDir "openapi.json") @{}

Write-Host "2/3 Login as $Email..."
$loginBody = @{ email = $Email; password = $Password } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/auth/login" `
    -ContentType "application/json" -Body $loginBody
if (-not $login.success) { throw "Login failed: $($login.message)" }
$headers = @{ Authorization = "Bearer $($login.data.accessToken)" }

Write-Host "3/3 Fixtures (scenario month $Year-$Month, asOf=$AsOf)..."
Save-Raw "$BaseUrl/api/v1/auth/me/financial-setup" (Join-Path $fixtureDir "financial-setup.json") $headers
Save-Raw "$BaseUrl/api/v1/insights?asOf=$AsOf"     (Join-Path $fixtureDir "insights.json")        $headers
foreach ($status in @("PENDING", "ACCEPTED", "DISMISSED", "EXPIRED")) {
    Save-Raw "$BaseUrl/api/v1/suggestions?status=$status" `
        (Join-Path $fixtureDir "suggestions-$($status.ToLower()).json") $headers
}
Save-Raw "$BaseUrl/api/v1/notifications"                      (Join-Path $fixtureDir "notifications.json") $headers
Save-Raw "$BaseUrl/api/v1/budgets?month=$Month&year=$Year"    (Join-Path $fixtureDir "budgets.json")       $headers
Save-Raw "$BaseUrl/api/v1/projects"                           (Join-Path $fixtureDir "projects.json")      $headers

Write-Host "`nDone. Artifacts in docs/frontend/ - review fixtures for anything you don't want to hand off (tokens are NOT captured; user ids are)."
