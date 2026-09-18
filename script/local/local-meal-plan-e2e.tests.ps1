$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$scriptPath = Join-Path $repoRoot "script/local/local-meal-plan-e2e.ps1"
$scriptText = Get-Content -Raw -LiteralPath $scriptPath

if ($scriptText -notmatch "api/auth/register") { throw "local meal plan E2E must create an isolated account" }
if ($scriptText -notmatch "api/meal-plans") { throw "local meal plan E2E must use the meal plan API" }
if ($scriptText -notmatch "\/validate") { throw "local meal plan E2E must verify plan validation" }
if ($scriptText -notmatch "\/save") { throw "local meal plan E2E must verify plan saving" }
if ($scriptText -notmatch "shopping-list") { throw "local meal plan E2E must verify shopping list persistence" }
if ($scriptText -notmatch "shopping-list/items") { throw "local meal plan E2E must verify shopping item updates" }
if ($scriptText -notmatch "\/progress") { throw "local meal plan E2E must verify plan progress" }
if ($scriptText -notmatch "Idempotency-Key") { throw "local meal plan E2E must verify idempotent writes" }
if ($scriptText -notmatch "revision") { throw "local meal plan E2E must use revision-aware writes" }
if ($scriptText -notmatch "deleted_plan_not_found") { throw "local meal plan E2E must verify deleted plan cleanup" }
if ($scriptText -notmatch "New-RevisionUrl") { throw "local meal plan E2E must construct revision URLs without PowerShell variable ambiguity" }
if ($scriptText -match '\$mealPlanId\?revision') { throw "local meal plan E2E must not interpolate a variable immediately before a query marker" }
if ($scriptText -match "(?i)ExecutePaid|FOODMATE_E2E_ADMIN|API_KEY|Start-Job|ForEach-Object.*parallel|WarmupSeconds|SteadySeconds|reboot") {
    throw "local meal plan E2E must remain local and bounded"
}

Write-Output "local_meal_plan_e2e_contract=passed"
