$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$scriptPath = Join-Path $repoRoot "script/local/real-sql-agent-e2e.ps1"
$scriptText = Get-Content -Raw -LiteralPath $scriptPath
foreach ($required in @(
        "FOODMATE_SQL_PLANNER_MODE",
        "sql-agent",
        "time_parser",
        "database_query",
        "sql_query_audits",
        "run.completed",
        "Last-Event-ID",
        "FOODMATE_E2E_ADMIN_USERNAME",
        "FOODMATE_E2E_ADMIN_PASSWORD")) {
    if ($scriptText -notmatch [regex]::Escape($required)) { throw "SQL Agent runner is missing contract: $required" }
}
if ($scriptText -match "SELECT .*sql_text|candidate_sql.*report|answer.*report") { throw "SQL Agent runner must not write SQL text or full answer into its report" }
if ($scriptText -notmatch 'FOODMATE_DOCKER_PAID_MAX_TOTAL_COST_CNY = "5"') { throw "SQL Agent runner must keep the bounded paid budget" }
Write-Output "real_sql_agent_e2e_contract=passed"
