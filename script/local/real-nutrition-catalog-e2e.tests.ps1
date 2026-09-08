$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$scriptPath = Join-Path $repoRoot "script/local/real-nutrition-catalog-e2e.ps1"
$scriptText = Get-Content -Raw -LiteralPath $scriptPath

if ($scriptText -notmatch 'api/nutrition-foods/search') { throw "R1 script must use the real nutrition candidate API" }
if ($scriptText -notmatch 'nutrition_food_id') { throw "R1 script must submit an explicit nutrition catalog id" }
if ($scriptText -notmatch 'api/food-logs|api/nutrition-analysis') { throw "R1 script must verify food log and nutrition analysis APIs" }
if ($scriptText -notmatch 'food log cleanup failed') { throw "R1 script must retain cleanup failure evidence" }
if ($scriptText -match '(?i)Start-Job|ForEach-Object.*parallel|WarmupSeconds|SteadySeconds|reboot|acknowledge[-_ ]?loss') { throw "R1 script must stay a bounded business-path check" }

Write-Output "real_nutrition_catalog_e2e_contract=passed"
