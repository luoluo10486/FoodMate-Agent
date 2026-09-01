[CmdletBinding()]
param(
    [int]$WarmupSeconds = 30,
    [int]$SteadySeconds = 120,
    [int]$Workers = 16,
    [int]$DrainTimeoutSeconds = 90,
    [string]$JavaBaseUrl = "http://127.0.0.1:8080",
    [string]$RuntimeBaseUrl = "http://127.0.0.1:9002",
    [switch]$ExecuteTraffic,
    [switch]$EnableFaultInjection
)

$ErrorActionPreference = "Stop"

function Require-Positive([int]$value, [string]$name) {
    if ($value -lt 1) { throw "$name must be positive" }
}

function ConvertTo-ResponseText([object]$content) {
    if ($content -is [byte[]]) {
        return [Text.Encoding]::UTF8.GetString($content)
    }
    return [string]$content
}

function Get-Status([string]$name, [string]$url) {
    $stopwatch = [Diagnostics.Stopwatch]::StartNew()
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 5
        [pscustomobject]@{
            component = $name
            ready = $response.StatusCode -eq 200
            latency_ms = [math]::Round($stopwatch.Elapsed.TotalMilliseconds, 3)
            detail = ConvertTo-ResponseText $response.Content
        }
    } catch {
        [pscustomobject]@{
            component = $name
            ready = $false
            latency_ms = [math]::Round($stopwatch.Elapsed.TotalMilliseconds, 3)
            detail = $_.Exception.Message
        }
    }
}

function Wait-Ready([string]$name, [string]$url) {
    $deadline = (Get-Date).AddSeconds(90)
    do {
        $status = Get-Status $name $url
        if ($status.ready) { return $status }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "$name did not recover within 90 seconds: $($status.detail)"
}

function Get-QueueSnapshot {
    $query = @"
SELECT CONCAT_WS('|',
  (SELECT COUNT(*) FROM runtime_dispatch_outbox WHERE status IN ('pending','leased'))
  + (SELECT COUNT(*) FROM knowledge_index_outbox WHERE status IN ('pending','leased'))
  + (SELECT COUNT(*) FROM knowledge_visibility_outbox WHERE status IN ('pending','leased')),
  (SELECT COUNT(*) FROM runtime_dispatch_outbox WHERE status IN ('pending','leased')),
  (SELECT COUNT(*) FROM knowledge_index_outbox WHERE status IN ('pending','leased'))
  + (SELECT COUNT(*) FROM knowledge_visibility_outbox WHERE status IN ('pending','leased')),
  (SELECT COUNT(*) FROM runtime_tool_proposal_inbox WHERE status='claimed'),
  (SELECT COUNT(*) FROM runtime_event_inbox_v2 WHERE processing_status='accepted'),
  (SELECT COUNT(*) FROM agent_run_sse_outbox WHERE status IN ('pending','leased'))
)
"@
    try {
        $raw = docker exec foodmate-postgres psql -U postgres -d FoodMate -At -F '|' -v ON_ERROR_STOP=1 -c $query 2>$null
        if ($LASTEXITCODE -ne 0) { return $null }
        return ConvertTo-QueueSnapshot $raw
    } catch { }
    return $null
}

function ConvertTo-QueueSnapshot([string]$raw) {
    $parts = @($raw.Trim().Split('|'))
    if ($parts.Count -lt 6) { return $null }
    $values = @()
    foreach ($part in $parts[0..5]) {
        $value = 0L
        if (-not [long]::TryParse($part, [ref]$value)) { return $null }
        $values += $value
    }
    return [pscustomobject]@{
        pending = $values[0] + $values[3] + $values[4]
        delivery_pending = $values[0]
        dispatch_pending = $values[1]
        knowledge_pending = $values[2]
        proposal_inbox_pending = $values[3]
        runtime_inbox_pending = $values[4]
        sse_replay_retained = $values[5]
        sampled_at = (Get-Date).ToUniversalTime().ToString("o")
    }
}

function Start-QueueSampler([datetime]$until) {
    return Start-Job -ScriptBlock {
        param($deadline)
        $ErrorActionPreference = "SilentlyContinue"
        $query = @"
SELECT CONCAT_WS('|',
  (SELECT COUNT(*) FROM runtime_dispatch_outbox WHERE status IN ('pending','leased'))
  + (SELECT COUNT(*) FROM knowledge_index_outbox WHERE status IN ('pending','leased'))
  + (SELECT COUNT(*) FROM knowledge_visibility_outbox WHERE status IN ('pending','leased')),
  (SELECT COUNT(*) FROM runtime_dispatch_outbox WHERE status IN ('pending','leased')),
  (SELECT COUNT(*) FROM knowledge_index_outbox WHERE status IN ('pending','leased'))
  + (SELECT COUNT(*) FROM knowledge_visibility_outbox WHERE status IN ('pending','leased')),
  (SELECT COUNT(*) FROM runtime_tool_proposal_inbox WHERE status='claimed'),
  (SELECT COUNT(*) FROM runtime_event_inbox_v2 WHERE processing_status='accepted'),
  (SELECT COUNT(*) FROM agent_run_sse_outbox WHERE status IN ('pending','leased'))
)
"@
        while ((Get-Date) -lt $deadline) {
            $raw = docker exec foodmate-postgres psql -U postgres -d FoodMate -At -F '|' -c $query 2>$null
            if ($LASTEXITCODE -eq 0) {
                $parts = @($raw.Trim().Split('|'))
                if ($parts.Count -ge 6) {
                    $values = @()
                    $valid = $true
                    foreach ($part in $parts[0..5]) {
                        $value = 0L
                        if (-not [long]::TryParse($part, [ref]$value)) { $valid = $false; break }
                        $values += $value
                    }
                    if ($valid) {
                        [pscustomobject]@{
                            pending = $values[0] + $values[3] + $values[4]
                            delivery_pending = $values[0]
                            dispatch_pending = $values[1]
                            knowledge_pending = $values[2]
                            proposal_inbox_pending = $values[3]
                            runtime_inbox_pending = $values[4]
                            sse_replay_retained = $values[5]
                            sampled_at = (Get-Date).ToUniversalTime().ToString("o")
                        }
                    }
                }
            }
            Start-Sleep -Seconds 1
        }
    } -ArgumentList $until
}

function Get-AuditSnapshot([string]$username) {
    if ($username -notmatch '^m16_[a-f0-9]{32}$') { return $null }
    $query = @"
SELECT CONCAT_WS('|',
  COUNT(*),
  COUNT(*) FILTER (WHERE result='success'),
  COUNT(*) FILTER (WHERE result='failed'),
  COUNT(*) FILTER (WHERE result='rejected'),
  COUNT(*) FILTER (WHERE result='pending')
)
FROM operation_audits
WHERE operator_id=(SELECT user_id FROM users WHERE username=:'scan_username')
"@
    try {
        $raw = docker exec foodmate-postgres psql -U postgres -d FoodMate -At -F '|' -v ON_ERROR_STOP=1 -v "scan_username=$username" -c $query 2>$null
        if ($LASTEXITCODE -ne 0) { return $null }
        $parts = @($raw.Trim().Split('|'))
        if ($parts.Count -lt 5) { return $null }
        $values = @()
        foreach ($part in $parts[0..4]) {
            $value = 0L
            if (-not [long]::TryParse($part, [ref]$value)) { return $null }
            $values += $value
        }
        return [pscustomobject]@{
            total = $values[0]
            success = $values[1]
            failed = $values[2]
            rejected = $values[3]
            pending = $values[4]
            sampled_at = (Get-Date).ToUniversalTime().ToString("o")
        }
    } catch { }
    return $null
}

function Test-QueueAtBaseline([object]$snapshot, [object]$baseline) {
    if ($null -eq $snapshot) { return $false }
    if ($null -eq $baseline) { return $snapshot.pending -eq 0 }
    return (
        $snapshot.delivery_pending -le $baseline.delivery_pending -and
        $snapshot.dispatch_pending -le $baseline.dispatch_pending -and
        $snapshot.knowledge_pending -le $baseline.knowledge_pending -and
        $snapshot.proposal_inbox_pending -le $baseline.proposal_inbox_pending -and
        $snapshot.runtime_inbox_pending -le $baseline.runtime_inbox_pending
    )
}

function Get-QueueDelta([object]$before, [object]$after) {
    if ($null -eq $before -or $null -eq $after) { return $null }
    return [pscustomobject]@{
        pending = $after.pending - $before.pending
        delivery_pending = $after.delivery_pending - $before.delivery_pending
        dispatch_pending = $after.dispatch_pending - $before.dispatch_pending
        knowledge_pending = $after.knowledge_pending - $before.knowledge_pending
        proposal_inbox_pending = $after.proposal_inbox_pending - $before.proposal_inbox_pending
        runtime_inbox_pending = $after.runtime_inbox_pending - $before.runtime_inbox_pending
        sse_replay_retained = $after.sse_replay_retained - $before.sse_replay_retained
    }
}

function Wait-QueueDrained([int]$timeoutSeconds = 90, [object]$baseline = $null) {
    $started = [Diagnostics.Stopwatch]::StartNew()
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    $latest = Get-QueueSnapshot
    do {
        if (Test-QueueAtBaseline $latest $baseline) {
            $started.Stop()
            return [pscustomobject]@{
                drained = $true
                wait_ms = [math]::Round($started.Elapsed.TotalMilliseconds, 3)
                snapshot = $latest
            }
        }
        Start-Sleep -Seconds 1
        $latest = Get-QueueSnapshot
    } while ((Get-Date) -lt $deadline)
    $started.Stop()
    return [pscustomobject]@{
        drained = $false
        wait_ms = [math]::Round($started.Elapsed.TotalMilliseconds, 3)
        snapshot = $latest
    }
}

function Get-Percentile([object[]]$values, [double]$percent) {
    $sorted = @($values | ForEach-Object { [double]$_ } | Sort-Object)
    if ($sorted.Count -eq 0) { return $null }
    $rank = ($percent / 100) * ($sorted.Count - 1)
    $lower = [math]::Floor($rank)
    $upper = [math]::Ceiling($rank)
    if ($lower -eq $upper) { return [double]$sorted[$lower] }
    return [double]$sorted[$lower] + ($rank - $lower) * ([double]$sorted[$upper] - [double]$sorted[$lower])
}

function Remove-TestUser([string]$username) {
    if ([string]::IsNullOrWhiteSpace($username)) { return }
    $sql = @"
BEGIN;
UPDATE user_auth_sessions SET revoked_at=COALESCE(revoked_at,CURRENT_TIMESTAMP),updated_at=CURRENT_TIMESTAMP
WHERE user_id=(SELECT user_id FROM users WHERE username='$username');
UPDATE messages SET is_deleted=TRUE,deleted_at=COALESCE(deleted_at,CURRENT_TIMESTAMP),deleted_by=created_by,updated_at=CURRENT_TIMESTAMP
WHERE session_id IN (SELECT session_id FROM sessions WHERE user_id=(SELECT user_id FROM users WHERE username='$username'));
UPDATE sessions SET is_deleted=TRUE,deleted_at=COALESCE(deleted_at,CURRENT_TIMESTAMP),deleted_by=user_id,updated_at=CURRENT_TIMESTAMP
WHERE user_id=(SELECT user_id FROM users WHERE username='$username');
UPDATE user_profiles SET is_deleted=TRUE,deleted_at=COALESCE(deleted_at,CURRENT_TIMESTAMP),deleted_by=user_id,updated_at=CURRENT_TIMESTAMP
WHERE user_id=(SELECT user_id FROM users WHERE username='$username');
UPDATE users SET is_deleted=TRUE,deleted_at=COALESCE(deleted_at,CURRENT_TIMESTAMP),deleted_by=user_id,status='disabled',updated_at=CURRENT_TIMESTAMP
WHERE username='$username';
COMMIT;
"@
    try {
        docker exec foodmate-postgres psql -U postgres -d FoodMate -v ON_ERROR_STOP=1 -c $sql | Out-Null
    } catch {
        Write-Warning "Test user cleanup failed: $($_.Exception.Message)"
    }
}

Require-Positive $WarmupSeconds "WarmupSeconds"
Require-Positive $SteadySeconds "SteadySeconds"
Require-Positive $Workers "Workers"
Require-Positive $DrainTimeoutSeconds "DrainTimeoutSeconds"
if ($DrainTimeoutSeconds -gt 900) { throw "DrainTimeoutSeconds must not exceed 900" }
if ($Workers -gt 64) { throw "Workers must not exceed 64 for a bounded local run" }
if ($EnableFaultInjection -and -not $ExecuteTraffic) {
    throw "EnableFaultInjection requires explicit ExecuteTraffic"
}

$javaReady = "$JavaBaseUrl/actuator/health/readiness"
$javaMetrics = "$JavaBaseUrl/actuator/metrics"
$runtimeReady = "$RuntimeBaseUrl/foodmate/internal/health/ready"
$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) { throw "Docker CLI is required for PostgreSQL/Redis/RocketMQ checks" }

$before = @(
    Get-Status "java_readiness" $javaReady
    Get-Status "java_metrics" $javaMetrics
    Get-Status "python_readiness" $runtimeReady
)
$previousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$containers = docker ps --format '{{.Names}}' 2>$null
$dockerExitCode = $LASTEXITCODE
$ErrorActionPreference = $previousErrorActionPreference
if ($dockerExitCode -ne 0) { throw "Docker Desktop is unavailable; no traffic or fault injection was started" }
$required = @("foodmate-postgres", "foodmate-redis", "foodmate-rocketmq-namesrv", "foodmate-rocketmq-broker", "foodmate-rocketmq-proxy")
$missing = $required | Where-Object { $_ -notin @($containers) }
if ($missing) { throw "Required local dependencies are unavailable: $($missing -join ', ')" }
if (@($before | Where-Object { -not $_.ready }).Count -gt 0) { throw "Readiness prerequisite failed; no traffic or fault injection was started" }

$queueBefore = Get-QueueSnapshot
$report = [ordered]@{
    run_id = "m16-" + [guid]::NewGuid().ToString("N")
    started_at = (Get-Date).ToUniversalTime().ToString("o")
    traffic = [ordered]@{
        warmup_seconds = $WarmupSeconds
        steady_seconds = $SteadySeconds
        workers = $Workers
        mode = "deterministic-local-runtime"
        execution = if ($ExecuteTraffic) { "requested" } else { "preflight_only" }
        channel_mix = [ordered]@{ agent_run_percent = 80; proposal_percent = 20 }
    }
    readiness_before = $before
    queue_before = $queueBefore
    audit_before = $null
    audit_after = $null
    queue_drained = $null
    drain_wait_ms = $null
    fault_injection = "not_requested"
    limitations = @(
        "This is a bounded local business-path baseline, not a production capacity result.",
        "Queue peak is sampled from PostgreSQL outboxes and excludes broker-internal metrics.",
        "Expected business rejection/failure is reported separately from unexpected errors."
    )
}

if (-not $ExecuteTraffic) {
    $report.finished_at = (Get-Date).ToUniversalTime().ToString("o")
    $report | ConvertTo-Json -Depth 10
    exit 0
}

$username = "m16_" + [guid]::NewGuid().ToString("N")
$email = "$username@example.com"
$password = [guid]::NewGuid().ToString("N") + "Aa1!"
$sampler = $null
$jobs = @()
$faultRecovery = $null

try {
    Add-Type -AssemblyName System.Net.Http
    $setupHandler = New-Object System.Net.Http.HttpClientHandler
    $setupHandler.CookieContainer = New-Object System.Net.CookieContainer
    $setupClient = New-Object System.Net.Http.HttpClient($setupHandler)
    $setupClient.Timeout = [TimeSpan]::FromSeconds(20)
    try {
        $registerBody = @{ username = $username; email = $email; password = $password; nickname = "M1-6" } | ConvertTo-Json -Compress
        $registerContent = New-Object System.Net.Http.StringContent($registerBody, [Text.Encoding]::UTF8, "application/json")
        $registerResponse = $setupClient.PostAsync("$JavaBaseUrl/api/auth/register", $registerContent).GetAwaiter().GetResult()
        if (-not $registerResponse.IsSuccessStatusCode) { throw "test user registration failed: HTTP $($registerResponse.StatusCode)" }

        $loginBody = @{ username_or_email = $username; password = $password } | ConvertTo-Json -Compress
        $loginContent = New-Object System.Net.Http.StringContent($loginBody, [Text.Encoding]::UTF8, "application/json")
        $loginResponse = $setupClient.PostAsync("$JavaBaseUrl/api/auth/login", $loginContent).GetAwaiter().GetResult()
        if (-not $loginResponse.IsSuccessStatusCode) { throw "test user login failed: HTTP $($loginResponse.StatusCode)" }
        $cookies = $setupHandler.CookieContainer.GetCookies([Uri]$JavaBaseUrl)
        $csrfCookie = $cookies | Where-Object Name -eq "foodmate_csrf" | Select-Object -First 1
        if (-not $csrfCookie) { throw "foodmate_csrf cookie is missing after login" }

        $report.audit_before = Get-AuditSnapshot $username
    } finally {
        $setupClient.Dispose()
    }

    if ($EnableFaultInjection) {
        $report.fault_injection = "redis_restart"
        docker restart foodmate-redis | Out-Null
        $faultRecovery = Wait-Ready "python_readiness_after_redis_restart" $runtimeReady
        $report.readiness_after_fault = $faultRecovery
    }

    $sampler = Start-QueueSampler ((Get-Date).AddSeconds($WarmupSeconds + $SteadySeconds + 45))
    $workerScript = {
        param($workerId, $warmup, $steady, $baseUrl, $user, $secret)
        $ErrorActionPreference = "Stop"
        Add-Type -AssemblyName System.Net.Http

        function New-Context {
            $handler = New-Object System.Net.Http.HttpClientHandler
            $handler.CookieContainer = New-Object System.Net.CookieContainer
            $client = New-Object System.Net.Http.HttpClient($handler)
            $client.Timeout = [TimeSpan]::FromSeconds(30)
            return [pscustomobject]@{ Handler = $handler; Client = $client }
        }

        function Send-Json($context, [string]$method, [string]$url, [hashtable]$payload, [string]$csrf = $null) {
            $httpMethod = [System.Net.Http.HttpMethod]::new($method)
            $request = New-Object System.Net.Http.HttpRequestMessage($httpMethod, $url)
            if ($null -ne $payload) {
                $body = $payload | ConvertTo-Json -Depth 12 -Compress
                $request.Content = New-Object System.Net.Http.StringContent($body, [Text.Encoding]::UTF8, "application/json")
            }
            if (-not [string]::IsNullOrWhiteSpace($csrf)) { [void]$request.Headers.Add("X-CSRF-Token", $csrf) }
            $response = $context.Client.SendAsync($request).GetAwaiter().GetResult()
            $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            if (-not $response.IsSuccessStatusCode) { throw "$method $url returned HTTP $([int]$response.StatusCode)" }
            if ([string]::IsNullOrWhiteSpace($text)) { return $null }
            return $text | ConvertFrom-Json
        }

        function Get-Csrf($context) {
            $cookies = $context.Handler.CookieContainer.GetCookies([Uri]$baseUrl)
            $cookie = $cookies | Where-Object Name -eq "foodmate_csrf" | Select-Object -First 1
            if (-not $cookie) { throw "worker csrf cookie is missing" }
            return $cookie.Value
        }

        function Login($context) {
            [void](Send-Json $context "POST" "$baseUrl/api/auth/login" @{ username_or_email = $user; password = $secret })
            return Get-Csrf $context
        }

        function New-WorkerSession($context, [string]$csrf) {
            $suffix = [guid]::NewGuid().ToString("N")
            $response = Send-Json $context "POST" "$baseUrl/api/sessions" @{
                title = "M1-6 traffic worker $workerId-$suffix"
                mode = "agent"
            } $csrf
            $workerSessionId = [string]$response.data.session_id
            if ([string]::IsNullOrWhiteSpace($workerSessionId)) {
                throw "worker session id is missing"
            }
            return $workerSessionId
        }

        function Wait-Run($context, [string]$runId) {
            $deadline = (Get-Date).AddSeconds(60)
            do {
                $statusResponse = Send-Json $context "GET" "$baseUrl/api/chat/runs/$([uri]::EscapeDataString($runId))"
                $status = [string]$statusResponse.data.status
                if ($status -in @("completed", "failed", "cancelled")) { return $status }
                Start-Sleep -Milliseconds 100
            } while ((Get-Date) -lt $deadline)
            throw "AgentRun did not reach a terminal state within 60 seconds"
        }

        function Invoke-Agent($context, [string]$csrf, [int]$index) {
            $started = [Diagnostics.Stopwatch]::StartNew()
            $token = [guid]::NewGuid().ToString("N")
            $response = Send-Json $context "POST" "$baseUrl/api/chat/runs" @{ prompt = "M1-6 deterministic agent query $token"; session_id = $workerSessionId } $csrf
            $runId = [string]$response.data.run_id
            if ([string]::IsNullOrWhiteSpace($runId)) { throw "AgentRun id is missing" }
            $status = Wait-Run $context $runId
            $started.Stop()
            return [pscustomobject]@{
                channel = "agent_run"
                outcome = if ($status -eq "completed") { "success" } else { "business_failed" }
                unexpected = $false
                duration_ms = [math]::Round($started.Elapsed.TotalMilliseconds, 3)
                duplicate_deliveries = 0
                duplicate_side_effects = 0
            }
        }

        function New-FoodInput([string]$notes, [bool]$validItems) {
            $items = @()
            if ($validItems) { $items = @(@{ name = "rice"; amount = 100; unit = "g" }) }
            return @{
                meal_time = (Get-Date).ToUniversalTime().AddSeconds(-30).ToString("o")
                meal_type = "lunch"
                notes = $notes
                items = $items
            }
        }

        function New-Approval($context, [string]$csrf, [string]$operation, [Nullable[long]]$resourceId, [hashtable]$parameters, [string]$key) {
            $payload = @{
                session_id = [long]$workerSessionId
                operation = $operation
                resource_type = "food_log"
                parameters = $parameters
                idempotency_key = $key
                expires_in_seconds = 300
            }
            if ($null -ne $resourceId) { $payload.resource_id = $resourceId.Value }
            $response = Send-Json $context "POST" "$baseUrl/api/approvals/proposals" $payload $csrf
            $id = [string]$response.data.approval_request_id
            if ([string]::IsNullOrWhiteSpace($id)) { throw "approval request id is missing" }
            return $response.data
        }

        function Invoke-Proposal($context, [string]$csrf, [int]$index) {
            $started = [Diagnostics.Stopwatch]::StartNew()
            $scenario = $index % 5
            $key = "m16-$workerId-$index-" + [guid]::NewGuid().ToString("N")
            $duplicates = 0
            $duplicateEffects = 0
            $outcome = "success"
            if ($scenario -eq 0) {
                $parameters = New-FoodInput "M1-6 proposal success" $true
                $proposal = New-Approval $context $csrf "create" $null $parameters $key
                [void](Send-Json $context "POST" "$baseUrl/api/approvals/$($proposal.approval_request_id)/confirm" $parameters $csrf)
                $first = Send-Json $context "POST" "$baseUrl/api/approvals/$($proposal.approval_request_id)/execute" $parameters $csrf
                $second = Send-Json $context "POST" "$baseUrl/api/approvals/$($proposal.approval_request_id)/execute" $parameters $csrf
                if ([string]$first.data.status -ne "executed" -or [string]$second.data.status -ne "executed") { throw "idempotent proposal execution did not converge" }
                if ([string]$first.data.resource_id -ne [string]$second.data.resource_id) { throw "duplicate proposal execution changed the resource" }
                $duplicates = 1
            } elseif ($scenario -eq 1) {
                $parameters = New-FoodInput "M1-6 proposal rejected" $true
                $proposal = New-Approval $context $csrf "create" $null $parameters $key
                $rejected = Send-Json $context "POST" "$baseUrl/api/approvals/$($proposal.approval_request_id)/reject" $parameters $csrf
                if ([string]$rejected.data.status -ne "rejected") { throw "proposal rejection did not converge" }
                $outcome = "business_rejected"
            } elseif ($scenario -eq 2) {
                $parameters = New-FoodInput "M1-6 proposal failure" $false
                $proposal = New-Approval $context $csrf "create" $null $parameters $key
                [void](Send-Json $context "POST" "$baseUrl/api/approvals/$($proposal.approval_request_id)/confirm" $parameters $csrf)
                $executionFailed = $false
                try {
                    [void](Send-Json $context "POST" "$baseUrl/api/approvals/$($proposal.approval_request_id)/execute" $parameters $csrf)
                } catch { $executionFailed = $true }
                if (-not $executionFailed) { throw "invalid food log unexpectedly executed" }
                $outcome = "business_failed"
            } elseif ($scenario -eq 3) {
                $resourceId = [long](900000000 + ($workerId * 10000) + $index)
                $parameters = @{ revision = 1 }
                $first = New-Approval $context $csrf "update" $resourceId $parameters "$key-first"
                $second = New-Approval $context $csrf "update" $resourceId $parameters "$key-second"
                if ([string]$first.status -ne "superseded") { throw "first update proposal was not superseded" }
                if ([string]$second.status -ne "pending") { throw "replacement proposal is not pending" }
                [void](Send-Json $context "POST" "$baseUrl/api/approvals/$($second.approval_request_id)/reject" $parameters $csrf)
                $outcome = "business_superseded"
            } else {
                $parameters = New-FoodInput "M1-6 proposal duplicate" $true
                $proposal = New-Approval $context $csrf "create" $null $parameters $key
                $replayed = New-Approval $context $csrf "create" $null $parameters $key
                if ([string]$proposal.approval_request_id -ne [string]$replayed.approval_request_id) { throw "duplicate proposal created a second approval" }
                [void](Send-Json $context "POST" "$baseUrl/api/approvals/$($proposal.approval_request_id)/reject" $parameters $csrf)
                $duplicates = 1
                $outcome = "business_duplicate"
            }
            $started.Stop()
            return [pscustomobject]@{
                channel = "proposal"
                outcome = $outcome
                unexpected = $false
                duration_ms = [math]::Round($started.Elapsed.TotalMilliseconds, 3)
                duplicate_deliveries = $duplicates
                duplicate_side_effects = $duplicateEffects
            }
        }

        $context = New-Context
        # Windows PowerShell Job 返回值必须使用可序列化的数组，不能跨进程返回泛型 List。
        $operations = @()
        $workerError = $null
        try {
            $csrf = Login $context
            $workerSessionId = New-WorkerSession $context $csrf
            $warmupEnd = (Get-Date).AddSeconds($warmup)
            $counter = 0
            while ((Get-Date) -lt $warmupEnd) {
                try {
                    if ((Get-Random -Minimum 0 -Maximum 100) -lt 80) { [void](Invoke-Agent $context $csrf $counter) }
                    else { [void](Invoke-Proposal $context $csrf $counter) }
                } catch { }
                $counter++
            }
            $steadyEnd = (Get-Date).AddSeconds($steady)
            while ((Get-Date) -lt $steadyEnd) {
                $started = [Diagnostics.Stopwatch]::StartNew()
                try {
                    if ((Get-Random -Minimum 0 -Maximum 100) -lt 80) {
                        $operation = Invoke-Agent $context $csrf $counter
                    } else {
                        $operation = Invoke-Proposal $context $csrf $counter
                    }
                    $operations += $operation
                } catch {
                    $started.Stop()
                    $operations += [pscustomobject]@{
                        channel = "unknown"
                        outcome = "unexpected_error"
                        unexpected = $true
                        duration_ms = [math]::Round($started.Elapsed.TotalMilliseconds, 3)
                        duplicate_deliveries = 0
                        duplicate_side_effects = 0
                    }
                }
                $counter++
            }
        } catch {
            $message = [string]$_.Exception.Message
            if (-not [string]::IsNullOrWhiteSpace($secret)) {
                $message = $message.Replace($secret, "[redacted]")
            }
            if (-not [string]::IsNullOrWhiteSpace($user)) {
                $message = $message.Replace($user, "[redacted]")
            }
            $workerError = "worker setup failed: $($_.Exception.GetType().Name): " + $message.Substring(0, [math]::Min(256, $message.Length))
        }
        finally { $context.Client.Dispose() }
        [pscustomobject]@{
            worker_id = $workerId
            session_id = $workerSessionId
            operations = @($operations)
            worker_error = $workerError
        }
    }

    for ($worker = 0; $worker -lt $Workers; $worker++) {
        $jobs += Start-Job -ScriptBlock $workerScript -ArgumentList $worker, $WarmupSeconds, $SteadySeconds, $JavaBaseUrl, $username, $password
    }
    Wait-Job -Job $jobs | Out-Null
    $workerResultsList = [System.Collections.Generic.List[object]]::new()
    foreach ($job in @($jobs)) {
        $jobOutput = @(Receive-Job -Id ([int]$job.Id) -ErrorAction SilentlyContinue)
        $workerResults = @(
            $jobOutput |
                Where-Object {
                    $null -ne $_ -and $null -ne $_.PSObject.Properties['operations']
                }
        )
        foreach ($workerResult in $workerResults) {
            [void]$workerResultsList.Add($workerResult)
        }
        $jobErrors = @(
            $job.ChildJobs |
                ForEach-Object { $_.Error } |
                Where-Object { $null -ne $_ }
        )
        if ($workerResults.Count -eq 0) {
            [void]$workerResultsList.Add(
                [pscustomobject]@{
                    worker_id = $job.Id
                    session_id = $null
                    operations = @()
                    worker_error = if ($jobErrors.Count -gt 0) {
                        "worker job failed: $($jobErrors.Count) error record(s)"
                    } else {
                        "worker job returned no result"
                    }
                })
        }
        elseif ($jobErrors.Count -gt 0) {
            foreach ($workerResult in $workerResults) {
                $workerResult.worker_error = "worker job emitted $($jobErrors.Count) error record(s)"
            }
        }
    }
    $workerResults = @($workerResultsList)
    $allOperations = @($workerResults | ForEach-Object { @($_.operations) })
    $workerErrors = @($workerResults | Where-Object { -not [string]::IsNullOrWhiteSpace($_.worker_error) }).Count
    $report.traffic.worker_errors = @(
        $workerResults |
            ForEach-Object { [string]$_.worker_error } |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
            Select-Object -First 16
    )
    $workerSessions = @($workerResults | Where-Object { -not [string]::IsNullOrWhiteSpace($_.session_id) } | Select-Object -ExpandProperty session_id -Unique)

    $latencies = @($allOperations | ForEach-Object { $_.duration_ms })
    $total = $allOperations.Count
    $unexpectedErrors = @($allOperations | Where-Object { $_.unexpected }).Count
    $agentRuns = @($allOperations | Where-Object { $_.channel -eq "agent_run" }).Count
    $proposals = @($allOperations | Where-Object { $_.channel -eq "proposal" }).Count
    $successful = @($allOperations | Where-Object { $_.outcome -eq "success" }).Count
    $businessRejected = @($allOperations | Where-Object { $_.outcome -eq "business_rejected" }).Count
    $businessFailed = @($allOperations | Where-Object { $_.outcome -eq "business_failed" }).Count
    $businessSuperseded = @($allOperations | Where-Object { $_.outcome -eq "business_superseded" }).Count
    $duplicateDeliveries = [int](($allOperations | Measure-Object -Property duplicate_deliveries -Sum).Sum)
    $duplicateSideEffects = [int](($allOperations | Measure-Object -Property duplicate_side_effects -Sum).Sum)
    $queueSamples = @()
    if ($sampler) {
        Wait-Job -Job $sampler -Timeout 5 | Out-Null
        $queueSamples = @(Receive-Job -Job $sampler -ErrorAction SilentlyContinue)
    }
    $queuePeak = $null
    if ($queueSamples.Count -gt 0) { $queuePeak = [long](($queueSamples | Measure-Object -Property pending -Maximum).Maximum) }
    $drain = Wait-QueueDrained $DrainTimeoutSeconds $report.queue_before
    $report.queue_after = $drain.snapshot
    $report.queue_drained = $drain.drained
    $report.drain_wait_ms = $drain.wait_ms
    $report.queue_delta = Get-QueueDelta $report.queue_before $report.queue_after
    $report.queue_peak_over_baseline = if ($null -eq $queuePeak -or $null -eq $report.queue_before) {
        $null
    } else {
        [math]::Max(0, $queuePeak - $report.queue_before.pending)
    }
    $report.audit_after = Get-AuditSnapshot $username

    $report.traffic.total_operations = $total
    $report.traffic.agent_run_operations = $agentRuns
    $report.traffic.proposal_operations = $proposals
    $report.traffic.worker_sessions = $workerSessions.Count
    $report.traffic.successful_operations = $successful
    $report.traffic.business_rejected = $businessRejected
    $report.traffic.business_failed = $businessFailed
    $report.traffic.business_superseded = $businessSuperseded
    $report.traffic.unexpected_errors = $unexpectedErrors + $workerErrors
    $report.traffic.error_rate_percent = if ($total -eq 0) { 0 } else { [math]::Round((($unexpectedErrors + $workerErrors) / $total) * 100, 3) }
    $report.traffic.throughput_operations_per_second = [math]::Round($total / [math]::Max($SteadySeconds, 1), 3)
    $report.traffic.p50_ms = Get-Percentile $latencies 50
    $report.traffic.p95_ms = Get-Percentile $latencies 95
    $report.traffic.p99_ms = Get-Percentile $latencies 99
    $report.traffic.duplicate_deliveries = $duplicateDeliveries
    $report.traffic.duplicate_side_effects = $duplicateSideEffects
    $report.queue_peak = $queuePeak
    $report.queue_samples = $queueSamples
} finally {
    if ($jobs.Count -gt 0) { Remove-Job -Job $jobs -Force -ErrorAction SilentlyContinue }
    if ($sampler) { Remove-Job -Job $sampler -Force -ErrorAction SilentlyContinue }
    Remove-TestUser $username
    if ($report.traffic.execution -eq "requested") {
        if ($null -eq $report.queue_after) { $report.queue_after = Get-QueueSnapshot }
        if ($null -eq $report.queue_delta) { $report.queue_delta = Get-QueueDelta $report.queue_before $report.queue_after }
        $report.finished_at = (Get-Date).ToUniversalTime().ToString("o")
        $report | ConvertTo-Json -Depth 12
    }
}
