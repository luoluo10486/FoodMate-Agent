$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$scriptPath = Join-Path $repoRoot "script/local/m1-6-traffic-recovery.ps1"
$scriptText = Get-Content -Raw -LiteralPath $scriptPath

if ($scriptText -notmatch '\[switch\]\$ExecuteTraffic') {
    throw "M1-6 traffic entrypoint must require an explicit ExecuteTraffic switch"
}
if ($scriptText -notmatch 'docker restart foodmate-redis') {
    throw "M1-6 traffic entrypoint must preserve the explicit Redis fault-injection path"
}
if ($scriptText -notmatch '\$executionFailed\s*=\s*\$false') {
    throw "M1-6 failure scenario must distinguish an expected business error from a successful execution"
}
if ($scriptText -notmatch 'if \(-not \$executionFailed\)') {
    throw "M1-6 failure scenario must fail when the invalid request unexpectedly succeeds"
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
if ($scriptText -notmatch '\[Text\.Encoding\]::UTF8\.GetString') {
    throw "M1-6 traffic report must decode HTTP response content as UTF-8 text"
}
foreach ($field in @(
        'audit_before',
        'audit_after',
        'queue_drained',
        'drain_wait_ms',
        'delivery_pending',
        'proposal_inbox_pending',
        'runtime_inbox_pending',
        'sse_replay_retained'
    )) {
    if ($scriptText -notmatch [regex]::Escape($field)) {
        throw "M1-6 traffic report is missing evidence field: $field"
    }
}
if ($scriptText -match 'sse_pending\s*=') {
    throw "M1-6 traffic report must distinguish retained SSE replay facts from drainable queues"
}
if ($scriptText -notmatch 'pending\s*=\s*\$values\[0\]\s*\+\s*\$values\[3\]\s*\+\s*\$values\[4\]') {
    throw "M1-6 drainable pending count must exclude retained SSE replay facts"
}

Write-Output "m1_6_traffic_contract=passed"
