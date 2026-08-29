$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$scriptPath = Join-Path $repoRoot "script/local/m1-6-traffic-recovery.ps1"
$scriptText = Get-Content -Raw -LiteralPath $scriptPath

if ($scriptText -notmatch '\[switch\]\$ExecuteTraffic') {
    throw "M1-6 traffic entrypoint must require an explicit ExecuteTraffic switch"
}
if ($scriptText -notmatch '80') {
    throw "M1-6 traffic entrypoint must define the 80 percent AgentRun path"
}
if ($scriptText -notmatch '20') {
    throw "M1-6 traffic entrypoint must define the 20 percent Proposal path"
}
foreach ($field in @(
        'p50_ms',
        'p95_ms',
        'p99_ms',
        'queue_peak',
        'duplicate_deliveries',
        'duplicate_side_effects',
        'error_rate_percent'
    )) {
    if ($scriptText -notmatch [regex]::Escape($field)) {
        throw "M1-6 traffic report is missing field: $field"
    }
}
if ($scriptText -match '(?i)FOODMATE_.*API_KEY\s*=\s*[^\r\n#]+') {
    throw "M1-6 traffic entrypoint must not contain a credential value"
}

Write-Output "m1_6_traffic_contract=passed"
