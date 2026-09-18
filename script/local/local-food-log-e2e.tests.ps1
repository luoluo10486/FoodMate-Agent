$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$scriptPath = Join-Path $repoRoot "script/local/local-food-log-e2e.ps1"
$scriptText = Get-Content -Raw -LiteralPath $scriptPath

if ($scriptText -notmatch "api/auth/register") { throw "local food log E2E must create an isolated account" }
if ($scriptText -notmatch "api/nutrition-foods/search") { throw "local food log E2E must use the nutrition catalog API" }
if ($scriptText -notmatch "api/food-logs") { throw "local food log E2E must use the food log API" }
if ($scriptText -notmatch "api/nutrition-analysis") { throw "local food log E2E must verify nutrition analysis" }
if ($scriptText -notmatch "Idempotency-Key") { throw "local food log E2E must verify the idempotent write path" }
if ($scriptText -notmatch "food_log_deleted") { throw "local food log E2E must retain cleanup evidence" }
if ($scriptText -notmatch "revision") { throw "local food log E2E must use revision-aware cleanup" }
if ($scriptText -match "(?i)ExecutePaid|FOODMATE_E2E_ADMIN|API_KEY|Start-Job|ForEach-Object.*parallel|WarmupSeconds|SteadySeconds|reboot") {
    throw "local food log E2E must remain local and bounded"
}

Write-Output "local_food_log_e2e_contract=passed"
