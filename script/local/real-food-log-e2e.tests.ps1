$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$scriptPath = Join-Path $repoRoot "script/local/real-food-log-e2e.ps1"
$scriptText = Get-Content -Raw -LiteralPath $scriptPath

if ($scriptText -notmatch '\[switch\]\$ExecutePaid') { throw "R2 script must require an explicit paid execution switch" }
if ($scriptText -notmatch 'FOODMATE_E2E_ADMIN_USERNAME') { throw "R2 script must read admin identity from the process environment" }
if ($scriptText -notmatch 'FOODMATE_E2E_ADMIN_PASSWORD') { throw "R2 script must read admin password from the process environment" }
if ($scriptText -match '(?i)--api-key|\$ApiKey|Write-Output.*(API_KEY|PASSWORD)') { throw "R2 script must not pass or print credentials" }
if ($scriptText -notmatch 'FOODMATE_DOCKER_PAID_MAX_TOTAL_COST_CNY') { throw "R2 script must bound the paid budget" }
if ($scriptText -notmatch 'session.begin_scenario\("food-log"\)') { throw "R2 script must use the food-log paid scenario" }
if ($scriptText -notmatch 'FOODMATE_MODEL_TIER_HIGH|cloud_primary') { throw "R2 script must fail closed unless high Chat is cloud-backed" }
if ($scriptText -notmatch 'api/chat/runs|api/agent-runs/.*/stream') { throw "R2 script must use the real Chat creation and persisted AgentRun SSE APIs" }
if ($scriptText -match 'api/chat/runs/.*/stream') { throw "R2 script must not use the retired Chat SSE path" }
if ($scriptText -notmatch 'run.clarification_requested|food_log_writer') { throw "R2 script must assert the food log confirmation boundary" }
if ($scriptText -notmatch 'api/approvals|/confirm|/execute') { throw "R2 script must execute the real approval API" }
if ($scriptText -notmatch 'api/food-logs|nutrition-matched|nutrition_status|run.completed') { throw "R2 script must assert Java food log persistence and nutrition matching" }
if ($scriptText -notmatch 'food_log_id') { throw "R2 script must bind run completion to the persisted food log" }
if ($scriptText -notmatch 'food log cleanup failed|session cleanup failed') { throw "R2 script must retain cleanup failure evidence" }
if ($scriptText -match '(?i)Start-Job|ForEach-Object.*parallel|WarmupSeconds|SteadySeconds|reboot|acknowledge[-_ ]?loss') { throw "R2 script must stay a bounded business-path check" }

Write-Output "real_food_log_e2e_contract=passed"
