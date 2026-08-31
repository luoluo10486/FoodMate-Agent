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
if ($scriptText -notmatch '\$workerSessionId') {
    throw "M1-6 traffic workers must use a worker-local session"
}
if ($scriptText -match '\$operations\s*=\s*(?:New-Object\s+)?System\.Collections\.Generic\.List\[object\]') {
    throw "M1-6 PowerShell workers must not return generic List values across a background Job boundary"
}
if ($scriptText -notmatch '\$operations\s*=\s*@\(\)') {
    throw "M1-6 traffic workers must collect operations in a Job-serializable array"
}
if ($scriptText -notmatch 'api/sessions') {
    throw "M1-6 traffic workers must create sessions through the real API"
}
if ($scriptText -match '\$sharedSessionId') {
    throw "M1-6 traffic workers must not share one session across workers"
}

if ($scriptText -notmatch 'foreach\s*\(\$job\s+in\s+@\(\$jobs\)\)') {
    throw "M1-6 traffic results must be received one Job at a time for Windows PowerShell compatibility"
}
if ($scriptText -match 'Receive-Job\s+-Job\s+\$jobs\b') {
    throw "M1-6 traffic must not pass the heterogeneous jobs array directly to Receive-Job"
}
if ($scriptText -notmatch 'Receive-Job\s+-Id\s+\(\[int\]\$job\.Id\)') {
    throw "M1-6 traffic must receive each worker Job by its stable numeric id"
}
if ($scriptText -notmatch 'ChildJobs.*Error|worker_error') {
    throw "M1-6 traffic must retain worker Job errors in the report"
}
if ($scriptText -notmatch 'worker setup failed:\s*\$\(') {
    throw "M1-6 traffic must expose a bounded worker setup error summary for diagnosis"
}
if ($scriptText -notmatch 'worker_errors\s*=') {
    throw "M1-6 traffic report must expose bounded worker error summaries"
}
if ($scriptText -notmatch '\[int\]\$DrainTimeoutSeconds\s*=\s*90') {
    throw "M1-6 traffic must allow a bounded queue drain timeout for diagnostics"
}
if ($scriptText -notmatch 'Wait-QueueDrained\s+\$DrainTimeoutSeconds') {
    throw "M1-6 traffic must use the configured queue drain timeout"
}

Write-Output "m1_6_traffic_contract=passed"
