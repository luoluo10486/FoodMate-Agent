$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$scriptPath = Join-Path $repoRoot "script/local/real-meal-plan-e2e.ps1"
$scriptText = Get-Content -Raw -LiteralPath $scriptPath

if ($scriptText -notmatch '\[switch\]\$ExecutePaid') { throw "R3 script must require an explicit paid execution switch" }
if ($scriptText -notmatch 'FOODMATE_E2E_ADMIN_USERNAME') { throw "R3 script must read admin identity from the process environment" }
if ($scriptText -notmatch 'FOODMATE_E2E_ADMIN_PASSWORD') { throw "R3 script must read admin password from the process environment" }
if ($scriptText -match '(?i)--api-key|\$ApiKey|Write-Output.*(API_KEY|PASSWORD)') { throw "R3 script must not pass or print credentials" }
if ($scriptText -notmatch 'FOODMATE_DOCKER_PAID_MAX_TOTAL_COST_CNY') { throw "R3 script must bound the paid budget" }
if ($scriptText -notmatch 'session.begin_scenario\("meal-plan"\)') { throw "R3 script must use the meal-plan paid scenario" }
if ($scriptText -notmatch 'FOODMATE_MODEL_TIER_HIGH|cloud_primary') { throw "R3 script must fail closed unless high Chat is cloud-backed" }
if ($scriptText -notmatch 'api/chat/runs|api/agent-runs/.*/stream') { throw "R3 script must use the real Chat creation and persisted AgentRun SSE APIs" }
if ($scriptText -match 'api/chat/runs/.*/stream') { throw "R3 script must not use the retired Chat SSE path" }
if ($scriptText -notmatch 'run.clarification_requested|meal_plan.save_plan') { throw "R3 script must assert the meal plan confirmation boundary" }
if ($scriptText -notmatch 'confirmParameters|confirm.*execute|\$confirmParameters') { throw "R3 script must reuse identical plan parameters for confirmation and execution" }
if ($scriptText -notmatch 'api/approvals|/confirm|/execute') { throw "R3 script must execute the real approval API" }
if ($scriptText -notmatch 'api/meal-plans|shopping-list|saved') { throw "R3 script must assert Java meal plan and shopping list persistence" }
if ($scriptText -notmatch 'run.completed') { throw "R3 script must assert the terminal run.completed event" }
if ($scriptText -notmatch 'meal_plan_id') { throw "R3 script must bind run completion to the persisted meal plan" }
if ($scriptText -notmatch 'meal plan cleanup failed|session cleanup failed') { throw "R3 script must retain cleanup failure evidence" }
if ($scriptText -match '(?i)Start-Job|ForEach-Object.*parallel|WarmupSeconds|SteadySeconds|reboot|acknowledge[-_ ]?loss') { throw "R3 script must stay a bounded business-path check" }

Write-Output "real_meal_plan_e2e_contract=passed"
