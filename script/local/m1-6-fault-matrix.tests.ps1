$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$scriptPath = Join-Path $repoRoot "script/local/m1-6-fault-matrix.ps1"
if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
    throw "M1-6 fault matrix entrypoint is missing"
}
$scriptText = Get-Content -Raw -LiteralPath $scriptPath

if ($scriptText -notmatch '\[CmdletBinding\([^)]*SupportsShouldProcess') {
    throw "Fault matrix must support WhatIf/ShouldProcess before restarting a service"
}
if ($scriptText -notmatch '\[switch\]\$Execute') {
    throw "Fault matrix must require an explicit Execute switch"
}
if ($scriptText -notmatch 'Execute requires explicit confirmation') {
    throw "Fault matrix must reject accidental execution without explicit confirmation"
}

foreach ($scenario in @(
        'java-restart',
        'python-restart',
        'postgres-restart',
        'redis-restart',
        'rocketmq-restart',
        'outbox-ack-lost',
        'inbox-not-ack',
        'duplicate-proposal',
        'duplicate-event',
        'sse-last-event-id',
        'all'
    )) {
    if ($scriptText -notmatch [regex]::Escape($scenario)) {
        throw "Fault matrix is missing scenario: $scenario"
    }
}

foreach ($container in @(
        'foodmate',
        'foodmate-agent-runtime',
        'foodmate-postgres',
        'foodmate-redis',
        'foodmate-rocketmq-namesrv',
        'foodmate-rocketmq-broker',
        'foodmate-rocketmq-proxy'
    )) {
    if ($scriptText -notmatch [regex]::Escape($container)) {
        throw "Fault matrix is missing local container allow-list entry: $container"
    }
}

foreach ($field in @(
        'fault_injected_at',
        'component',
        'readiness_recovered_at',
        'readiness_recovery_ms',
        'retry_attempts',
        'final_status',
        'queue_peak',
        'queue_drained',
        'business_writes',
        'audit_count',
        'duplicate_deliveries',
        'duplicate_side_effects',
        'sse_gap_count',
        'sse_duplicate_terminal_count',
        'last_event_id'
    )) {
    if ($scriptText -notmatch [regex]::Escape($field)) {
        throw "Fault matrix report is missing evidence field: $field"
    }
}

foreach ($assignment in @(
        '$result.component =',
        '$result.fault_injected_at =',
        '$result.readiness_recovered_at ='
    )) {
    if ($scriptText -notmatch [regex]::Escape($assignment)) {
        throw "Fault matrix must promote restart evidence into the scenario result: $assignment"
    }
}

if ($scriptText -match '(?i)FOODMATE_.*API_KEY\s*=\s*[^\r\n#]+') {
    throw "Fault matrix must not contain a credential value"
}
if ($scriptText -match 'docker\s+(rm|system\s+prune|volume\s+rm|compose\s+down\s+-v)') {
    throw "Fault matrix must not destroy local data volumes"
}
if ($scriptText -notmatch 'Last-Event-ID') {
    throw "Fault matrix must verify the Last-Event-ID replay contract"
}
if ($scriptText -notmatch 'function Get-PersistedSseEvents') {
    throw "SSE replay probe must read authoritative persisted SSE event ids"
}
if ($scriptText -notmatch 'sse_event_id') {
    throw "SSE replay probe must compare the stream sse_event_id values"
}
if ($scriptText -notmatch 'runtime_dispatch_outbox') {
    throw "Fault matrix must inspect Outbox state"
}
if ($scriptText -notmatch 'runtime_event_inbox_v2') {
    throw "Fault matrix must inspect Inbox state"
}
if ($scriptText -notmatch 'function Invoke-OutboxAckLost') {
    throw "Fault matrix must execute a real Outbox ACK-loss replay probe"
}
if ($scriptText -notmatch 'function Invoke-InboxNotAck') {
    throw "Fault matrix must execute a distinct Inbox no-ACK replay probe"
}
if ($scriptText -notmatch 'ack_lost_after_commit') {
    throw "Inbox no-ACK probe must identify the committed-transaction/ack-loss fault model"
}
if ($scriptText -notmatch 'processing_status') {
    throw "Inbox no-ACK probe must verify the persisted Inbox completion fact"
}
if ($scriptText -match '(?s)elseif\s*\(\$name\s*-eq\s*\"inbox-not-ack\"\s*\)\s*\{[^}]*Invoke-DuplicateEvent') {
    throw "Inbox no-ACK must not reuse the generic duplicate-event probe"
}
if ($scriptText -notmatch 'UPDATE runtime_dispatch_outbox') {
    throw "Outbox ACK-loss probe must reset one published test fact for controlled replay"
}
if ($scriptText -notmatch 'send_attempts') {
    throw "Outbox ACK-loss probe must verify that the relay published a retry"
}
if ($scriptText -match '\[hashtable\]\$payload') {
    throw "Fault matrix JSON helper must accept deserialized event payloads"
}
if ($scriptText -notmatch 'status -notin @\("pending", "published"\)') {
    throw "Outbox ACK-loss probe must tolerate a relay that republishes before the next observation"
}
if ($scriptText -match 'outbox-replay_requires_orchestrator_fixture') {
    throw "Outbox ACK-loss scenario must not remain an orchestrator-only placeholder"
}

$restartFunction = [regex]::Match(
    $scriptText,
    '(?s)function Invoke-ContainerRestart.*?\n}\s*\n\s*function New-ScenarioResult'
).Value
if ([string]::IsNullOrWhiteSpace($restartFunction)) {
    throw "Fault matrix restart helper is missing"
}
if ($restartFunction -match '\$PSCmdlet\.ShouldProcess') {
    throw "Nested restart helper must not access the top-level PSCmdlet scope"
}

Write-Output "m1_6_fault_matrix_contract=passed"
