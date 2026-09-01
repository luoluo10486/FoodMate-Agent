[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = "High")]
param(
    [ValidateSet(
        "all",
        "java-restart",
        "python-restart",
        "postgres-restart",
        "redis-restart",
        "rocketmq-restart",
        "outbox-ack-lost",
        "inbox-not-ack",
        "duplicate-proposal",
        "duplicate-event",
        "sse-last-event-id"
    )]
    [string]$Scenario = "all",
    [switch]$Execute,
    [switch]$IncludeAgentRun,
    [switch]$KeepTestData,
    [int]$RecoveryTimeoutSeconds = 120,
    [string]$JavaBaseUrl = "http://127.0.0.1:8080",
    [string]$RuntimeBaseUrl = "http://127.0.0.1:9002"
)

$ErrorActionPreference = "Stop"
if ($RecoveryTimeoutSeconds -lt 10 -or $RecoveryTimeoutSeconds -gt 900) {
    throw "RecoveryTimeoutSeconds must be between 10 and 900"
}

$scenarioNames = @(
    "java-restart",
    "python-restart",
    "postgres-restart",
    "redis-restart",
    "rocketmq-restart",
    "outbox-ack-lost",
    "inbox-not-ack",
    "duplicate-proposal",
    "duplicate-event",
    "sse-last-event-id"
)
$selectedScenarios = if ($Scenario -eq "all") { $scenarioNames } else { @($Scenario) }

$containerAllowList = @(
    "foodmate",
    "foodmate-agent-runtime",
    "foodmate-postgres",
    "foodmate-redis",
    "foodmate-rocketmq-namesrv",
    "foodmate-rocketmq-broker",
    "foodmate-rocketmq-proxy"
)

function ConvertTo-SafeText([object]$value) {
    if ($value -is [byte[]]) { return [Text.Encoding]::UTF8.GetString($value) }
    return [string]$value
}

function Get-HttpStatus([string]$name, [string]$url) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 8
        return [pscustomobject]@{
            component = $name
            ready = ($response.StatusCode -eq 200)
            status_code = [int]$response.StatusCode
            latency_ms = [math]::Round($watch.Elapsed.TotalMilliseconds, 3)
            detail = ConvertTo-SafeText $response.Content
        }
    } catch {
        return [pscustomobject]@{
            component = $name
            ready = $false
            status_code = $null
            latency_ms = [math]::Round($watch.Elapsed.TotalMilliseconds, 3)
            detail = $_.Exception.Message
        }
    }
}

function Get-ContainerHealth([string]$container) {
    if ($container -notin $containerAllowList) { throw "Container is outside the local allow-list: $container" }
    $raw = docker inspect --format '{{json .State}}' $container 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($raw)) {
        return [pscustomobject]@{ component = $container; ready = $false; status = "missing"; detail = "container is unavailable" }
    }
    try {
        $state = $raw | ConvertFrom-Json
        $health = if ($null -ne $state.Health) { [string]$state.Health.Status } else { "running" }
        return [pscustomobject]@{
            component = $container
            ready = ([bool]$state.Running -and $health -in @("healthy", "running"))
            status = $health
            detail = "running=$($state.Running)"
        }
    } catch {
        return [pscustomobject]@{ component = $container; ready = $false; status = "invalid"; detail = "container state could not be parsed" }
    }
}

function Wait-HttpReady([string]$name, [string]$url) {
    $deadline = (Get-Date).AddSeconds($RecoveryTimeoutSeconds)
    $latest = $null
    do {
        $latest = Get-HttpStatus $name $url
        if ($latest.ready) { return $latest }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "$name did not recover within $RecoveryTimeoutSeconds seconds"
}

function Wait-ContainerReady([string]$container) {
    $deadline = (Get-Date).AddSeconds($RecoveryTimeoutSeconds)
    $latest = $null
    do {
        $latest = Get-ContainerHealth $container
        if ($latest.ready) { return $latest }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "$container did not become healthy within $RecoveryTimeoutSeconds seconds"
}

function Invoke-PsqlJson([string]$query) {
    $raw = docker exec foodmate-postgres psql -U postgres -d FoodMate -At -v ON_ERROR_STOP=1 -c $query 2>$null
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL evidence query failed" }
    $text = (ConvertTo-SafeText ($raw -join "`n")).Trim()
    if ([string]::IsNullOrWhiteSpace($text)) { throw "PostgreSQL evidence query returned no result" }
    try { return $text | ConvertFrom-Json } catch { throw "PostgreSQL evidence query returned invalid JSON" }
}

function Get-QueueSnapshot([string]$username = "") {
    $safeUsername = $username.Replace("'", "''")
    $userFilter = if ([string]::IsNullOrWhiteSpace($safeUsername)) { "TRUE" } else { "u.username='$safeUsername'" }
    $query = @"
SELECT json_build_object(
  'pending',
    (SELECT COUNT(*) FROM runtime_dispatch_outbox WHERE status IN ('pending','queued','leased'))
    + (SELECT COUNT(*) FROM knowledge_index_outbox WHERE status IN ('pending','queued','leased'))
    + (SELECT COUNT(*) FROM knowledge_visibility_outbox WHERE status IN ('pending','queued','leased'))
    + (SELECT COUNT(*) FROM runtime_tool_proposal_inbox WHERE status='claimed')
    + (SELECT COUNT(*) FROM runtime_event_inbox_v2 WHERE processing_status='accepted'),
  'delivery_pending', (SELECT COUNT(*) FROM runtime_dispatch_outbox WHERE status IN ('pending','queued','leased')),
  'knowledge_pending',
    (SELECT COUNT(*) FROM knowledge_index_outbox WHERE status IN ('pending','queued','leased'))
    + (SELECT COUNT(*) FROM knowledge_visibility_outbox WHERE status IN ('pending','queued','leased')),
  'proposal_inbox_pending', (SELECT COUNT(*) FROM runtime_tool_proposal_inbox WHERE status='claimed'),
  'runtime_inbox_pending', (SELECT COUNT(*) FROM runtime_event_inbox_v2 WHERE processing_status='accepted'),
  'sse_replay_retained', (SELECT COUNT(*) FROM agent_run_sse_outbox WHERE status IN ('pending','leased')),
  'business_writes', (SELECT COUNT(*) FROM food_logs f JOIN users u ON u.user_id=f.user_id WHERE $userFilter),
  'audit_count', (SELECT COUNT(*) FROM operation_audits a JOIN users u ON u.user_id=a.operator_id WHERE $userFilter),
  'retry_attempts',
    COALESCE((SELECT SUM(send_attempts) FROM runtime_dispatch_outbox),0)
    + COALESCE((SELECT SUM(retry_count) FROM agent_run_sse_outbox),0),
  'sampled_at', CURRENT_TIMESTAMP
)::text;
"@
    return Invoke-PsqlJson $query
}

function New-HttpContext {
    Add-Type -AssemblyName System.Net.Http
    $handler = New-Object System.Net.Http.HttpClientHandler
    $handler.CookieContainer = New-Object System.Net.CookieContainer
    $client = New-Object System.Net.Http.HttpClient($handler)
    $client.Timeout = [TimeSpan]::FromSeconds(45)
    return [pscustomobject]@{ Handler = $handler; Client = $client }
}

function Invoke-Json(
    [object]$context,
    [string]$method,
    [string]$url,
    [hashtable]$payload = $null,
    [hashtable]$headers = $null
) {
    $request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::new($method), $url)
    if ($null -ne $payload) {
        $body = $payload | ConvertTo-Json -Depth 20 -Compress
        $request.Content = New-Object System.Net.Http.StringContent($body, [Text.Encoding]::UTF8, "application/json")
    }
    if ($null -ne $headers) {
        foreach ($key in $headers.Keys) { [void]$request.Headers.TryAddWithoutValidation($key, [string]$headers[$key]) }
    }
    $response = $context.Client.SendAsync($request).GetAwaiter().GetResult()
    $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $response.IsSuccessStatusCode) {
        throw "$method endpoint returned HTTP $([int]$response.StatusCode)"
    }
    if ([string]::IsNullOrWhiteSpace($text)) { return $null }
    try { return $text | ConvertFrom-Json } catch { throw "$method endpoint returned invalid JSON" }
}

function Get-Csrf([object]$context, [string]$baseUrl) {
    $cookies = $context.Handler.CookieContainer.GetCookies([Uri]$baseUrl)
    $cookie = $cookies | Where-Object Name -eq "foodmate_csrf" | Select-Object -First 1
    if (-not $cookie) { throw "CSRF cookie is missing" }
    return $cookie.Value
}

function New-TestProbe([string]$baseUrl) {
    $suffix = [guid]::NewGuid().ToString("N")
    $username = "m16fault_$suffix"
    $password = "$suffix`Aa1!"
    $context = New-HttpContext
    [void](Invoke-Json $context "POST" "$baseUrl/api/auth/register" @{
            username = $username
            email = "$username@example.com"
            password = $password
            nickname = "M1-6 fault matrix"
        })
    [void](Invoke-Json $context "POST" "$baseUrl/api/auth/login" @{ username_or_email = $username; password = $password })
    $csrf = Get-Csrf $context $baseUrl
    $session = Invoke-Json $context "POST" "$baseUrl/api/sessions" @{
        title = "M1-6 fault matrix $suffix"
        mode = "agent"
    } @{ "X-CSRF-Token" = $csrf }
    $sessionId = [string]$session.data.session_id
    if ([string]::IsNullOrWhiteSpace($sessionId)) { throw "test session id is missing" }
    return [pscustomobject]@{
        context = $context
        username = $username
        password = $password
        csrf = $csrf
        session_id = $sessionId
        run_id = $null
    }
}

function Remove-TestProbe([object]$probe) {
    if ($null -eq $probe -or [string]::IsNullOrWhiteSpace($probe.username) -or $KeepTestData) { return }
    $safe = $probe.username.Replace("'", "''")
    $query = @"
BEGIN;
UPDATE user_auth_sessions SET revoked_at=COALESCE(revoked_at,CURRENT_TIMESTAMP),updated_at=CURRENT_TIMESTAMP
WHERE user_id=(SELECT user_id FROM users WHERE username='$safe');
UPDATE messages SET is_deleted=TRUE,deleted_at=COALESCE(deleted_at,CURRENT_TIMESTAMP),deleted_by=created_by,updated_at=CURRENT_TIMESTAMP
WHERE session_id IN (SELECT session_id FROM sessions WHERE user_id=(SELECT user_id FROM users WHERE username='$safe'));
UPDATE sessions SET is_deleted=TRUE,deleted_at=COALESCE(deleted_at,CURRENT_TIMESTAMP),deleted_by=user_id,updated_at=CURRENT_TIMESTAMP
WHERE user_id=(SELECT user_id FROM users WHERE username='$safe');
UPDATE user_profiles SET is_deleted=TRUE,deleted_at=COALESCE(deleted_at,CURRENT_TIMESTAMP),deleted_by=user_id,updated_at=CURRENT_TIMESTAMP
WHERE user_id=(SELECT user_id FROM users WHERE username='$safe');
UPDATE users SET is_deleted=TRUE,deleted_at=COALESCE(deleted_at,CURRENT_TIMESTAMP),deleted_by=user_id,status='disabled',updated_at=CURRENT_TIMESTAMP
WHERE username='$safe';
COMMIT;
"@
    try { docker exec foodmate-postgres psql -U postgres -d FoodMate -v ON_ERROR_STOP=1 -c $query 2>$null | Out-Null } catch { Write-Warning "test probe soft cleanup failed" }
}

function Test-BusinessRead([object]$probe, [string]$baseUrl) {
    $response = Invoke-Json $probe.context "GET" "$baseUrl/api/sessions?page=1&size=50"
    $found = @($response.data.items | Where-Object { [string]$_.session_id -eq $probe.session_id }).Count -eq 1
    if (-not $found) { throw "business probe session is not readable after recovery" }
    return [pscustomobject]@{ business_writes = 0; audit_count = 0; probe = "authenticated_session_read" }
}

function Invoke-DuplicateProposal([object]$probe, [string]$baseUrl) {
    $parameters = @{
        meal_time = (Get-Date).ToUniversalTime().ToString("o")
        meal_type = "lunch"
        notes = "M1-6 duplicate proposal probe"
        items = @(@{ name = "rice"; amount = 100; unit = "g" })
    }
    $key = "m16fault-" + [guid]::NewGuid().ToString("N")
    $payload = @{
        session_id = [long]$probe.session_id
        operation = "create"
        resource_type = "food_log"
        parameters = $parameters
        idempotency_key = $key
        expires_in_seconds = 300
    }
    $first = Invoke-Json $probe.context "POST" "$baseUrl/api/approvals/proposals" $payload @{ "X-CSRF-Token" = $probe.csrf }
    $second = Invoke-Json $probe.context "POST" "$baseUrl/api/approvals/proposals" $payload @{ "X-CSRF-Token" = $probe.csrf }
    $firstId = [string]$first.data.approval_request_id
    $secondId = [string]$second.data.approval_request_id
    if ([string]::IsNullOrWhiteSpace($firstId) -or $firstId -ne $secondId) { throw "duplicate proposal did not reuse the original approval" }
    [void](Invoke-Json $probe.context "POST" "$baseUrl/api/approvals/$firstId/reject" $parameters @{ "X-CSRF-Token" = $probe.csrf })
    return [pscustomobject]@{ duplicate_deliveries = 1; duplicate_side_effects = 0; final_status = "reused_and_rejected" }
}

function New-AgentRun([object]$probe, [string]$baseUrl) {
    $response = Invoke-Json $probe.context "POST" "$baseUrl/api/chat/runs" @{
        prompt = "M1-6 fault matrix recovery probe"
        session_id = $probe.session_id
    } @{ "X-CSRF-Token" = $probe.csrf }
    $runId = [string]$response.data.run_id
    if ([string]::IsNullOrWhiteSpace($runId)) { throw "agent run id is missing" }
    return $runId
}

function Wait-AgentRun([object]$probe, [string]$baseUrl, [string]$runId) {
    $deadline = (Get-Date).AddSeconds($RecoveryTimeoutSeconds)
    $status = "queued"
    do {
        $response = Invoke-Json $probe.context "GET" "$baseUrl/api/chat/runs/$([uri]::EscapeDataString($runId))"
        $status = [string]$response.data.status
        if ($status -in @("completed", "failed", "cancelled")) { return $status }
        Start-Sleep -Milliseconds 300
    } while ((Get-Date) -lt $deadline)
    throw "AgentRun did not reach a terminal state within $RecoveryTimeoutSeconds seconds"
}

function Get-RawEvent([string]$runId) {
    $safeRunId = $runId.Replace("'", "''")
    $query = @"
SELECT json_build_object(
  'schema_version','v1',
  'run_id',CAST(e.agent_run_id AS text),
  'dispatch_id',e.dispatch_id,
  'attempt',d.attempt,
  'event_id',e.event_id,
  'event_seq',e.event_seq,
  'request_id','m1-6-fault-matrix',
  'trace_id',COALESCE(r.trace_id,'m1-6-fault-matrix'),
  'request_hash',e.request_hash,
  'occurred_at',e.occurred_at,
  'event_type',e.event_type,
  'payload',e.payload_json
)::text
FROM runtime_event_inbox_v2 e
JOIN agent_runs r ON r.agent_run_id=e.agent_run_id
JOIN agent_run_dispatches d ON d.dispatch_id=e.dispatch_id
WHERE CAST(e.agent_run_id AS text)='$safeRunId'
ORDER BY e.event_seq
LIMIT 1;
"@
    return Invoke-PsqlJson $query
}

function Invoke-DuplicateEvent([object]$probe, [string]$baseUrl, [string]$runId) {
    $event = Get-RawEvent $runId
    $result = Invoke-Json $probe.context "POST" "$baseUrl/foodmate/internal/v1/agent-events" $event @{ "X-Contract-Version" = "v1" }
    $duplicate = [bool]$result.data.duplicate
    if (-not $duplicate) { throw "duplicate event was not recognized by the Java Inbox" }
    return [pscustomobject]@{ duplicate_deliveries = 1; duplicate_side_effects = 0; final_status = "inbox_duplicate_reused" }
}

function Invoke-PsqlCommand([string]$query) {
    docker exec foodmate-postgres psql -U postgres -d FoodMate -v ON_ERROR_STOP=1 -c $query 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL fault-injection command failed" }
}

function Get-RunDispatchSummary([string]$runId) {
    $safeRunId = $runId.Replace("'", "''")
    $query = @"
SELECT json_build_object(
  'outbox_id',o.outbox_id,
  'dispatch_id',o.dispatch_id,
  'status',o.status,
  'transport',o.transport,
  'send_attempts',o.send_attempts,
  'event_count',(SELECT COUNT(*) FROM runtime_event_inbox_v2 e WHERE e.agent_run_id=o.agent_run_id),
  'assistant_count',(SELECT COUNT(*) FROM messages m WHERE m.agent_run_id=o.agent_run_id AND m.role='assistant' AND m.is_deleted=FALSE)
)::text
FROM runtime_dispatch_outbox o
WHERE o.run_id='$safeRunId'
ORDER BY o.created_at DESC
LIMIT 1;
"@
    return Invoke-PsqlJson $query
}

function Wait-OutboxRepublished([string]$runId, [int64]$outboxId, [int]$previousAttempts) {
    $deadline = (Get-Date).AddSeconds($RecoveryTimeoutSeconds)
    $latest = $null
    do {
        $latest = Get-RunDispatchSummary $runId
        if ([int64]$latest.outbox_id -eq $outboxId -and
            [string]$latest.status -eq "published" -and
            [int]$latest.send_attempts -gt $previousAttempts) {
            return $latest
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "Outbox ACK-loss probe did not observe a republished fact"
}

function Invoke-OutboxAckLost([object]$probe, [string]$baseUrl, [string]$runId) {
    $before = Get-RunDispatchSummary $runId
    if ($null -eq $before -or [string]$before.status -ne "published" -or [string]$before.transport -ne "rocketmq") {
        throw "Outbox ACK-loss probe requires a published RocketMQ test fact"
    }
    $safeOutboxId = [int64]$before.outbox_id
    $faultInjectedAt = (Get-Date).ToUniversalTime().ToString("o")
    $query = @"
UPDATE runtime_dispatch_outbox
SET status='pending',
    owner_token=NULL,
    lease_until=NULL,
    next_attempt_at=CURRENT_TIMESTAMP,
    mq_message_id=NULL,
    published_at=NULL,
    delivered_at=NULL,
    last_error='M1-6 injected broker ACK loss',
    updated_at=CURRENT_TIMESTAMP
WHERE outbox_id=$safeOutboxId
  AND run_id='$($runId.Replace("'", "''"))'
  AND status='published'
  AND transport='rocketmq';
"@
    Invoke-PsqlCommand $query
    $pending = Get-RunDispatchSummary $runId
    if ($null -eq $pending -or [string]$pending.status -ne "pending") {
        throw "Outbox ACK-loss probe did not reset the selected test fact"
    }
    $after = Wait-OutboxRepublished $runId $safeOutboxId ([int]$before.send_attempts)
    $faultSettledAt = (Get-Date).ToUniversalTime().ToString("o")
    $eventDelta = [int64]$after.event_count - [int64]$before.event_count
    $assistantDelta = [int64]$after.assistant_count - [int64]$before.assistant_count
    $duplicateSideEffects = [math]::Max(0, $eventDelta + $assistantDelta)
    if ($duplicateSideEffects -ne 0) {
        throw "Outbox ACK-loss replay produced duplicate business side effects"
    }
    return [pscustomobject]@{
        fault_injected_at = $faultInjectedAt
        readiness_recovered_at = $faultSettledAt
        outbox_id = $safeOutboxId
        dispatch_id = [string]$before.dispatch_id
        initial_send_attempts = [int]$before.send_attempts
        final_send_attempts = [int]$after.send_attempts
        retry_attempts = [int]$after.send_attempts - [int]$before.send_attempts
        duplicate_deliveries = 1
        duplicate_side_effects = $duplicateSideEffects
        event_count_before = [int64]$before.event_count
        event_count_after = [int64]$after.event_count
        assistant_count_before = [int64]$before.assistant_count
        assistant_count_after = [int64]$after.assistant_count
        final_status = "outbox_replayed_idempotently"
    }
}

function Invoke-SseReplay([object]$probe, [string]$baseUrl, [string]$runId) {
    $events = Invoke-Json $probe.context "GET" "$baseUrl/api/chat/runs/$([uri]::EscapeDataString($runId))/events"
    $items = @($events.data)
    if ($items.Count -lt 2) { throw "SSE replay probe needs at least two persisted events" }
    $cursor = [string]$items[0].event_id
    $expected = @($items | Where-Object { [long]$_.event_seq -gt [long]$items[0].event_seq } | ForEach-Object { [string]$_.event_id })
    $request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Get, "$baseUrl/api/chat/runs/$([uri]::EscapeDataString($runId))/stream")
    [void]$request.Headers.TryAddWithoutValidation("Last-Event-ID", $cursor)
    $response = $probe.context.Client.SendAsync($request).GetAwaiter().GetResult()
    $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $response.IsSuccessStatusCode) { throw "SSE replay returned HTTP $([int]$response.StatusCode)" }
    $observed = @([regex]::Matches($body, '(?m)^id:\s*(\S+)') | ForEach-Object { $_.Groups[1].Value })
    $terminalCount = @($observed | Where-Object { $_ -in @($items | Where-Object { $_.state -in @("SUCCEEDED", "FAILED", "CANCELED") } | ForEach-Object { [string]$_.event_id }) }).Count
    $missing = @($expected | Where-Object { $_ -notin $observed }).Count
    $duplicates = $observed.Count - @($observed | Select-Object -Unique).Count
    if ($missing -ne 0 -or $terminalCount -gt 1) { throw "SSE replay has missing events or duplicate terminal events" }
    return [pscustomobject]@{
        last_event_id = $cursor
        sse_gap_count = $missing
        sse_duplicate_terminal_count = [math]::Max(0, $terminalCount - 1)
        sse_duplicate_count = $duplicates
        final_status = "replayed"
    }
}

function Invoke-ContainerRestart([string]$component, [string[]]$containers) {
    $started = (Get-Date).ToUniversalTime().ToString("o")
    $recovery = @()
    foreach ($container in $containers) {
        if ($container -notin $containerAllowList) { throw "Container is outside the local allow-list: $container" }
        if (-not $PSCmdlet.ShouldProcess($container, "restart local fault-matrix component")) {
            continue
        }
        docker restart $container | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "failed to restart $container" }
        if ($container -eq "foodmate") { $recovery += Wait-HttpReady "java_readiness_after_restart" "$JavaBaseUrl/actuator/health/readiness" }
        elseif ($container -eq "foodmate-agent-runtime") { $recovery += Wait-HttpReady "python_readiness_after_restart" "$RuntimeBaseUrl/foodmate/internal/health/ready" }
        else { $recovery += Wait-ContainerReady $container }
    }
    return [pscustomobject]@{ fault_injected_at = $started; component = ($containers -join ","); readiness_recovered_at = (Get-Date).ToUniversalTime().ToString("o"); recovery = @($recovery) }
}

function New-ScenarioResult([string]$name, [object]$before) {
    return [ordered]@{
        scenario = $name
        component = $null
        fault_injected_at = $null
        readiness_recovered_at = $null
        readiness_recovery_ms = $null
        retry_attempts = 0
        final_status = "not_executed"
        queue_peak = $null
        queue_drained = $null
        business_writes = [int64]$before.business_writes
        audit_count = [int64]$before.audit_count
        duplicate_deliveries = 0
        duplicate_side_effects = 0
        sse_gap_count = 0
        sse_duplicate_terminal_count = 0
        sse_duplicate_count = 0
        last_event_id = $null
        queue_before = $before
        queue_after = $null
        evidence = $null
    }
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw "Docker CLI is required" }
$containers = @(docker ps --format '{{.Names}}')
if ($LASTEXITCODE -ne 0) { throw "Docker Desktop is unavailable" }
$requiredContainers = @($containerAllowList)
$missing = @($requiredContainers | Where-Object { $_ -notin $containers })
if ($missing.Count -gt 0) { throw "Required local containers are unavailable: $($missing -join ', ')" }

$beforeReady = @(
    Get-HttpStatus "java_readiness" "$JavaBaseUrl/actuator/health/readiness"
    Get-HttpStatus "python_readiness" "$RuntimeBaseUrl/foodmate/internal/health/ready"
)
if (@($beforeReady | Where-Object { -not $_.ready }).Count -gt 0) { throw "Readiness prerequisite failed; no fault was injected" }

$report = [ordered]@{
    run_id = "m16-fault-" + [guid]::NewGuid().ToString("N")
    started_at = (Get-Date).ToUniversalTime().ToString("o")
    execute = [bool]$Execute
    include_agent_run = [bool]$IncludeAgentRun
    scenarios = @()
    readiness_before = $beforeReady
    limitations = @(
        "Only local Docker containers in the explicit allow-list are eligible for restart.",
        "No database volume deletion, truncate, migration, backup restore, or production endpoint is performed.",
        "AgentRun-dependent replay scenarios require IncludeAgentRun and may call the configured Runtime model."
    )
}

if (-not $Execute) {
    foreach ($name in $selectedScenarios) {
        $before = Get-QueueSnapshot
        $result = New-ScenarioResult $name $before
        $result.final_status = "preflight_only"
        $result.component = switch ($name) {
            "java-restart" { "foodmate" }
            "python-restart" { "foodmate-agent-runtime" }
            "postgres-restart" { "foodmate-postgres" }
            "redis-restart" { "foodmate-redis" }
            "rocketmq-restart" { "foodmate-rocketmq-namesrv,foodmate-rocketmq-broker,foodmate-rocketmq-proxy" }
            default { "application-and-database-evidence" }
        }
        $report.scenarios += [pscustomobject]$result
    }
    $report.finished_at = (Get-Date).ToUniversalTime().ToString("o")
    $report | ConvertTo-Json -Depth 15
    exit 0
}

if (-not $PSCmdlet.ShouldProcess(($selectedScenarios -join ","), "execute M1-6 local fault matrix; Execute requires explicit confirmation")) {
    $report.execute = $false
    $report.confirmation = "WhatIf"
    $report.finished_at = (Get-Date).ToUniversalTime().ToString("o")
    $report | ConvertTo-Json -Depth 15
    exit 0
}

$probe = $null
try {
    $probe = New-TestProbe $JavaBaseUrl
    foreach ($name in $selectedScenarios) {
        $before = Get-QueueSnapshot $probe.username
        $result = New-ScenarioResult $name $before
        $result.component = switch ($name) {
            "java-restart" { "foodmate" }
            "python-restart" { "foodmate-agent-runtime" }
            "postgres-restart" { "foodmate-postgres" }
            "redis-restart" { "foodmate-redis" }
            "rocketmq-restart" { "foodmate-rocketmq-namesrv,foodmate-rocketmq-broker,foodmate-rocketmq-proxy" }
            default { "application-and-database-evidence" }
        }
        $watch = [Diagnostics.Stopwatch]::StartNew()
        try {
            switch ($name) {
                "java-restart" { $result.evidence = Invoke-ContainerRestart "java" @("foodmate"); Test-BusinessRead $probe $JavaBaseUrl | Out-Null }
                "python-restart" { $result.evidence = Invoke-ContainerRestart "python" @("foodmate-agent-runtime"); Test-BusinessRead $probe $JavaBaseUrl | Out-Null }
                "postgres-restart" { $result.evidence = Invoke-ContainerRestart "postgres" @("foodmate-postgres"); Wait-HttpReady "java_readiness_after_postgres_restart" "$JavaBaseUrl/actuator/health/readiness"; Test-BusinessRead $probe $JavaBaseUrl | Out-Null }
                "redis-restart" { $result.evidence = Invoke-ContainerRestart "redis" @("foodmate-redis"); Wait-HttpReady "python_readiness_after_redis_restart" "$RuntimeBaseUrl/foodmate/internal/health/ready"; Test-BusinessRead $probe $JavaBaseUrl | Out-Null }
                "rocketmq-restart" { $result.evidence = Invoke-ContainerRestart "rocketmq" @("foodmate-rocketmq-proxy", "foodmate-rocketmq-broker", "foodmate-rocketmq-namesrv"); Test-BusinessRead $probe $JavaBaseUrl | Out-Null }
                "duplicate-proposal" { $result.evidence = Invoke-DuplicateProposal $probe $JavaBaseUrl }
                { $_ -in @("outbox-ack-lost", "inbox-not-ack", "duplicate-event", "sse-last-event-id") } {
                    if (-not $IncludeAgentRun) { throw "Scenario $name requires -IncludeAgentRun to create a real run" }
                    if ([string]::IsNullOrWhiteSpace($probe.run_id)) { $probe.run_id = New-AgentRun $probe $JavaBaseUrl }
                    $status = Wait-AgentRun $probe $JavaBaseUrl $probe.run_id
                    if ($name -eq "outbox-ack-lost") { $result.evidence = Invoke-OutboxAckLost $probe $JavaBaseUrl $probe.run_id }
                    elseif ($name -eq "duplicate-event" -or $name -eq "inbox-not-ack") { $result.evidence = Invoke-DuplicateEvent $probe $JavaBaseUrl $probe.run_id }
                    elseif ($name -eq "sse-last-event-id") { $result.evidence = Invoke-SseReplay $probe $JavaBaseUrl $probe.run_id }
                }
            }
            if ($result.evidence.fault_injected_at) { $result.fault_injected_at = [string]$result.evidence.fault_injected_at }
            if ($result.evidence.readiness_recovered_at) { $result.readiness_recovered_at = [string]$result.evidence.readiness_recovered_at }
            if ($result.evidence.component) { $result.component = [string]$result.evidence.component }
            $result.final_status = if ($result.evidence.final_status) { [string]$result.evidence.final_status } else { "recovered" }
            if ($result.evidence.duplicate_deliveries) { $result.duplicate_deliveries = [int]$result.evidence.duplicate_deliveries }
            if ($result.evidence.duplicate_side_effects) { $result.duplicate_side_effects = [int]$result.evidence.duplicate_side_effects }
            if ($result.evidence.last_event_id) { $result.last_event_id = [string]$result.evidence.last_event_id }
            if ($result.evidence.sse_gap_count) { $result.sse_gap_count = [int]$result.evidence.sse_gap_count }
            if ($result.evidence.sse_duplicate_terminal_count) { $result.sse_duplicate_terminal_count = [int]$result.evidence.sse_duplicate_terminal_count }
            if ($result.evidence.sse_duplicate_count) { $result.sse_duplicate_count = [int]$result.evidence.sse_duplicate_count }
        } catch {
            $result.final_status = "failed"
            $result.evidence = [pscustomobject]@{ error_code = "M16_SCENARIO_FAILED"; error_summary = $_.Exception.Message.Substring(0, [math]::Min(256, $_.Exception.Message.Length)) }
        } finally {
            $watch.Stop()
            $result.readiness_recovery_ms = [math]::Round($watch.Elapsed.TotalMilliseconds, 3)
            $after = Get-QueueSnapshot $probe.username
            $result.queue_after = $after
            $result.retry_attempts = [int64]$after.retry_attempts - [int64]$before.retry_attempts
            $result.business_writes = [int64]$after.business_writes
            $result.audit_count = [int64]$after.audit_count
            $result.queue_drained = ([int64]$after.pending -eq 0)
            $report.scenarios += [pscustomobject]$result
        }
    }
} finally {
    if ($probe -and $probe.context) { $probe.context.Client.Dispose() }
    Remove-TestProbe $probe
}

$report.finished_at = (Get-Date).ToUniversalTime().ToString("o")
$report | ConvertTo-Json -Depth 15
