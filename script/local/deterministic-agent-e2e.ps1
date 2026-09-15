[CmdletBinding()]
param(
    [string]$JavaBaseUrl = "http://127.0.0.1:8080",
    [int]$RunTimeoutSeconds = 180,
    [switch]$KeepData
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http -ErrorAction Stop
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$composeFile = Join-Path $repoRoot "docker/compose.yml"
$envFile = Join-Path $repoRoot ".env"
$composeArgs = @("--env-file", $envFile, "-f", $composeFile)
$context = $null
$runtimeEnvironmentChanged = $false
$previousRuntimeEnvironment = @{}
$runtimeEnvironmentNames = @(
    "FOODMATE_DOCKER_MODEL_TIER_HIGH",
    "FOODMATE_DOCKER_MODEL_TIER_STANDARD",
    "FOODMATE_DOCKER_MODEL_TIER_ECONOMY",
    "FOODMATE_DOCKER_MODEL_TIER_EVAL",
    "FOODMATE_DOCKER_MODEL_FALLBACK_ENABLED",
    "FOODMATE_DOCKER_PAID_EXECUTION_ENABLED",
    "FOODMATE_DOCKER_PAID_REQUIRE_CLOUD"
)

$report = [ordered]@{
    status = "running"
    execution = "deterministic_local"
    started_at = (Get-Date).ToUniversalTime().ToString("o")
    provider = "deterministic"
    model = "local"
    run_id = $null
    session_id = $null
    sse = $null
    api = $null
    cleanup = [ordered]@{
        requested = (-not $KeepData)
        session_soft_deleted = $false
        errors = @()
    }
    error_code = $null
    error_summary = $null
}

function Get-Field([object]$Object, [string[]]$Names) {
    if ($null -eq $Object) { return $null }
    foreach ($name in $Names) {
        $property = $Object.PSObject.Properties[$name]
        if ($null -ne $property) { return $property.Value }
    }
    return $null
}

function Get-SafeSummary([object]$ErrorRecord) {
    $exception = if ($null -ne $ErrorRecord.Exception) { $ErrorRecord.Exception } else { $ErrorRecord }
    $message = if ($null -ne $exception) { [string]$exception.Message } else { "unknown error" }
    $message = [regex]::Replace($message, '(?i)(api[_ -]?key|authorization|bearer|password|token)s*[:=]\s*\S+', '$1=[redacted]')
    $message = [regex]::Replace($message, '(?i)https?://\S+', "[url]")
    $message = [regex]::Replace($message, '\s+', " ").Trim()
    if ([string]::IsNullOrWhiteSpace($message)) { $message = "unknown error" }
    if ($message.Length -gt 256) { $message = $message.Substring(0, 256) }
    return $message
}

function Get-ErrorCode([object]$ErrorRecord) {
    $exception = if ($null -ne $ErrorRecord.Exception) { $ErrorRecord.Exception } else { $ErrorRecord }
    if ($null -ne $exception -and $null -ne $exception.Data -and $exception.Data.Contains("foodmate_error_code")) {
        return [string]$exception.Data["foodmate_error_code"]
    }
    return "DETERMINISTIC_AGENT_E2E_FAILED"
}

function Add-CleanupError([string]$Message) {
    $report.cleanup.errors = @($report.cleanup.errors) + $Message
}

function New-HttpFailure([string]$Method, [int]$StatusCode, [string]$Body) {
    $code = "HTTP_$StatusCode"
    try {
        $json = $Body | ConvertFrom-Json
        $errorNode = Get-Field $json @("error", "data")
        $candidate = Get-Field $errorNode @("code", "error_code")
        if (-not [string]::IsNullOrWhiteSpace([string]$candidate)) { $code = [string]$candidate }
    } catch { }
    $exception = [System.Exception]::new("$Method returned HTTP $StatusCode")
    [void]$exception.Data.Add("foodmate_error_code", $code)
    return $exception
}

function New-ApiContext {
    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.CookieContainer = [System.Net.CookieContainer]::new()
    $client = [System.Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromSeconds($RunTimeoutSeconds + 30)
    return [pscustomobject]@{ Handler = $handler; Client = $client }
}

function Invoke-Api(
    [object]$ApiContext,
    [string]$Method,
    [string]$Url,
    [object]$Payload = $null,
    [hashtable]$Headers = @{}
) {
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), $Url)
    try {
        if ($null -ne $Payload) {
            $body = $Payload | ConvertTo-Json -Depth 32 -Compress
            $request.Content = [System.Net.Http.StringContent]::new($body, [Text.Encoding]::UTF8, "application/json")
        }
        foreach ($header in $Headers.GetEnumerator()) {
            [void]$request.Headers.TryAddWithoutValidation([string]$header.Key, [string]$header.Value)
        }
        $response = $ApiContext.Client.SendAsync($request).GetAwaiter().GetResult()
        try {
            $responseBody = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            if (-not $response.IsSuccessStatusCode) {
                throw (New-HttpFailure $Method ([int]$response.StatusCode) $responseBody)
            }
            if ([string]::IsNullOrWhiteSpace($responseBody)) { return $null }
            return $responseBody | ConvertFrom-Json
        } finally {
            $response.Dispose()
        }
    } finally {
        $request.Dispose()
    }
}

function Get-Csrf([object]$ApiContext) {
    $cookies = $ApiContext.Handler.CookieContainer.GetCookies([Uri]$JavaBaseUrl)
    $cookie = $cookies | Where-Object Name -eq "foodmate_csrf" | Select-Object -First 1
    if ($null -eq $cookie) { throw "foodmate_csrf cookie is missing after registration" }
    return $cookie.Value
}

function Wait-HttpReady([string]$Name, [string]$Url, [int]$TimeoutSeconds = 90) {
    $deadline = (Get-Date).ToUniversalTime().AddSeconds($TimeoutSeconds)
    $last = "unknown readiness failure"
    do {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 10
            if ($response.StatusCode -eq 200) { return }
            $last = "HTTP $($response.StatusCode)"
        } catch { $last = Get-SafeSummary $_ }
        Start-Sleep -Seconds 2
    } while ((Get-Date).ToUniversalTime() -lt $deadline)
    throw "$Name readiness did not recover: $last"
}

function Read-Sse(
    [object]$ApiContext,
    [string]$Url,
    [string]$LastEventId,
    [int]$TimeoutSeconds,
    [string[]]$StopOnEventTypes = @()
) {
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Get, $Url)
    $response = $null
    $reader = $null
    $events = [System.Collections.Generic.List[object]]::new()
    $eventId = $null
    $eventType = $null
    $dataLines = [System.Collections.Generic.List[string]]::new()
    try {
        if (-not [string]::IsNullOrWhiteSpace($LastEventId)) {
            [void]$request.Headers.TryAddWithoutValidation("Last-Event-ID", $LastEventId)
        }
        $response = $ApiContext.Client.SendAsync(
            $request,
            [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead
        ).GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            throw (New-HttpFailure "GET" ([int]$response.StatusCode) $body)
        }
        $reader = [IO.StreamReader]::new($response.Content.ReadAsStreamAsync().GetAwaiter().GetResult())
        $deadline = (Get-Date).ToUniversalTime().AddSeconds($TimeoutSeconds)
        $lineTask = $null
        while ((Get-Date).ToUniversalTime() -lt $deadline) {
            if ($null -eq $lineTask) { $lineTask = $reader.ReadLineAsync() }
            if (-not $lineTask.Wait(250)) { continue }
            $line = $lineTask.Result
            $lineTask = $null
            if ($null -eq $line) { break }
            if ($line.StartsWith("id:")) { $eventId = $line.Substring(3).Trim(); continue }
            if ($line.StartsWith("event:")) { $eventType = $line.Substring(6).Trim(); continue }
            if ($line.StartsWith("data:")) { [void]$dataLines.Add($line.Substring(5).TrimStart()); continue }
            if ($line -ne "") { continue }
            if (-not [string]::IsNullOrWhiteSpace($eventId) -and -not [string]::IsNullOrWhiteSpace($eventType)) {
                $payload = $null
                $rawData = ($dataLines -join "`n")
                if (-not [string]::IsNullOrWhiteSpace($rawData)) {
                    try { $payload = $rawData | ConvertFrom-Json } catch { $payload = $null }
                }
                [void]$events.Add([pscustomobject]@{
                        sse_event_id = $eventId
                        event_type = $eventType
                        payload = $payload
                    })
                if ($StopOnEventTypes -contains $eventType) { break }
            }
            $eventId = $null
            $eventType = $null
            $dataLines.Clear()
        }
    } finally {
        if ($null -ne $reader) { $reader.Dispose() }
        if ($null -ne $response) { $response.Dispose() }
        $request.Dispose()
    }
    return $events.ToArray()
}

function Assert-UniqueSseIds([object[]]$Events) {
    if ($Events.Count -eq 0) { throw "AgentRun SSE returned no persisted events" }
    $ids = @($Events | ForEach-Object { [string]$_.sse_event_id })
    if (@($ids | Select-Object -Unique).Count -ne $ids.Count) {
        throw "AgentRun SSE returned duplicate event ids"
    }
    return $ids
}

function Get-ContainerState([string]$ContainerName) {
    $state = & docker inspect --format '{{.State.Status}}' $ContainerName 2>$null
    if ($LASTEXITCODE -ne 0) { return $null }
    return (($state | Out-String).Trim())
}

function Get-ContainerExitCode([string]$ContainerName) {
    $exitCode = & docker inspect --format '{{.State.ExitCode}}' $ContainerName 2>$null
    if ($LASTEXITCODE -ne 0) { return $null }
    return [int](($exitCode | Out-String).Trim())
}

function Wait-RocketMqInit([int]$TimeoutSeconds = 240) {
    $deadline = (Get-Date).ToUniversalTime().AddSeconds($TimeoutSeconds)
    do {
        $state = Get-ContainerState "foodmate-rocketmq-init"
        if ($state -eq "exited") {
            $exitCode = Get-ContainerExitCode "foodmate-rocketmq-init"
            if ($exitCode -ne 0) { throw "RocketMQ topic initialization failed with exit code $exitCode" }
            return
        }
        if ($null -eq $state) { throw "RocketMQ topic initialization container is missing" }
        Start-Sleep -Seconds 1
    } while ((Get-Date).ToUniversalTime() -lt $deadline)
    throw "RocketMQ topic initialization did not finish within $TimeoutSeconds seconds"
}

function Ensure-AgentRuntimeStarted([int]$TimeoutSeconds = 30) {
    $state = Get-ContainerState "foodmate-agent-runtime"
    if ($null -eq $state) { throw "Agent Runtime container is missing" }
    if ($state -eq "created" -or $state -eq "exited") {
        & docker start foodmate-agent-runtime | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Agent Runtime container failed to start" }
    }

    $deadline = (Get-Date).ToUniversalTime().AddSeconds($TimeoutSeconds)
    do {
        $state = Get-ContainerState "foodmate-agent-runtime"
        if ($state -eq "running" -or $state -eq "restarting") { return }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date).ToUniversalTime() -lt $deadline)
    throw "Agent Runtime container did not enter running state within $TimeoutSeconds seconds"
}

function Start-AgentRuntime {
    & docker compose @composeArgs up -d --force-recreate agent-runtime | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Agent Runtime recreation failed" }
    # Compose 可能先创建目标容器，再异步等待一次性 RocketMQ 初始化容器；
    # 这里显式等待依赖终态，再启动并确认目标容器，避免把 created 当成可用服务。
    Wait-RocketMqInit
    Ensure-AgentRuntimeStarted
}

function Set-DeterministicRuntime {
    foreach ($name in $runtimeEnvironmentNames) {
        $previousRuntimeEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
    }
    $values = @{
        "FOODMATE_DOCKER_MODEL_TIER_HIGH" = "deterministic:local"
        "FOODMATE_DOCKER_MODEL_TIER_STANDARD" = "deterministic:local"
        "FOODMATE_DOCKER_MODEL_TIER_ECONOMY" = "deterministic:local"
        "FOODMATE_DOCKER_MODEL_TIER_EVAL" = "deterministic:local"
        "FOODMATE_DOCKER_MODEL_FALLBACK_ENABLED" = "false"
        "FOODMATE_DOCKER_PAID_EXECUTION_ENABLED" = "false"
        "FOODMATE_DOCKER_PAID_REQUIRE_CLOUD" = "false"
    }
    foreach ($entry in $values.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, "Process")
    }
    Start-AgentRuntime
    $script:runtimeEnvironmentChanged = $true
}

function Restore-RuntimeEnvironment {
    if (-not $script:runtimeEnvironmentChanged) { return }
    foreach ($name in $runtimeEnvironmentNames) {
        [Environment]::SetEnvironmentVariable($name, $previousRuntimeEnvironment[$name], "Process")
    }
    try {
        Start-AgentRuntime
    } catch { Add-CleanupError "runtime configuration restore failed" }
}

try {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw "Docker CLI is required" }
    if (-not (Test-Path -LiteralPath $composeFile -PathType Leaf)) { throw "Docker Compose file is missing" }
    if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) { throw "project .env is missing" }
    if ($RunTimeoutSeconds -lt 60 -or $RunTimeoutSeconds -gt 600) {
        throw "RunTimeoutSeconds must be between 60 and 600"
    }

    & docker compose @composeArgs config --quiet
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose configuration is invalid" }
    Set-DeterministicRuntime
    Wait-HttpReady "Java" "$JavaBaseUrl/actuator/health/readiness"
    Wait-HttpReady "agent-runtime" "http://127.0.0.1:9002/foodmate/internal/health/ready"

    $context = New-ApiContext
    $suffix = [Guid]::NewGuid().ToString("N").Substring(0, 12)
    $username = "det_e2e_$suffix"
    $registerPayload = @{
        username = $username
        email = "$username@example.com"
        password = "FoodMateE2e!123"
        nickname = "Deterministic E2E"
    }
    $register = Invoke-Api $context "POST" "$JavaBaseUrl/api/auth/register" $registerPayload
    $csrf = Get-Csrf $context
    $registerData = Get-Field $register @("data")
    $userId = [string](Get-Field $registerData @("user_id", "userId"))
    if ([string]::IsNullOrWhiteSpace($userId)) { throw "registration did not return a user id" }

    $prompt = "给我一句健康饮食建议"
    $runPayload = @{ prompt = $prompt }
    $runHeaders = @{ "X-CSRF-Token" = $csrf }
    $runResponse = Invoke-Api $context "POST" "$JavaBaseUrl/api/chat/runs" $runPayload $runHeaders
    $runData = Get-Field $runResponse @("data")
    $report.run_id = [string](Get-Field $runData @("run_id", "runId"))
    $report.session_id = [string](Get-Field $runData @("session_id", "sessionId"))
    if ([string]::IsNullOrWhiteSpace($report.run_id) -or [string]::IsNullOrWhiteSpace($report.session_id)) {
        throw "ChatRun identifiers are missing"
    }

    $events = @(Read-Sse -ApiContext $context -Url "$JavaBaseUrl/api/agent-runs/$($report.run_id)/stream" -LastEventId "0" -TimeoutSeconds $RunTimeoutSeconds -StopOnEventTypes @("run.completed", "run.failed", "run.cancelled"))
    $eventIds = @(Assert-UniqueSseIds $events)
    $terminalEvents = @($events | Where-Object { @("run.completed", "run.failed", "run.cancelled", "run.superseded") -contains $_.event_type })
    $completedEvents = @($events | Where-Object event_type -eq "run.completed")
    if ($completedEvents.Count -ne 1 -or $terminalEvents.Count -ne 1) {
        throw "AgentRun did not produce exactly one completed terminal event"
    }
    if ([string]::IsNullOrWhiteSpace([string](Get-Field $completedEvents[0].payload @("answer")))) {
        throw "run.completed answer is empty"
    }
    if ($eventIds.Count -lt 2) { throw "AgentRun SSE stream is too short for replay assertion" }
    $beforeTerminalId = $eventIds[$eventIds.Count - 2]
    $replayEvents = @(Read-Sse -ApiContext $context -Url "$JavaBaseUrl/api/agent-runs/$($report.run_id)/stream" -LastEventId $beforeTerminalId -TimeoutSeconds $RunTimeoutSeconds -StopOnEventTypes @("run.completed", "run.failed", "run.cancelled"))
    $replayIds = @(Assert-UniqueSseIds $replayEvents)
    if (@($replayEvents | Where-Object event_type -eq "run.completed").Count -ne 1) {
        throw "Last-Event-ID replay did not return the terminal event"
    }
    if ($replayIds[-1] -ne $eventIds[-1]) { throw "Last-Event-ID replay ended at a different event" }

    $statusResponse = Invoke-Api -ApiContext $context -Method "GET" -Url "$JavaBaseUrl/api/chat/runs/$($report.run_id)"
    $statusData = Get-Field $statusResponse @("data")
    $status = ([string](Get-Field $statusData @("status"))).ToLowerInvariant()
    if ($status -notin @("completed", "succeeded", "success")) { throw "server run status is not terminal success: $status" }
    $eventResponse = Invoke-Api -ApiContext $context -Method "GET" -Url "$JavaBaseUrl/api/chat/runs/$($report.run_id)/events"
    $persistedEvents = @(Get-Field $eventResponse @("data"))
    if ($persistedEvents.Count -lt $events.Count) { throw "persisted event query returned fewer events than SSE" }
    $report.api = [ordered]@{
        user_id = $userId
        run_status = $status
        persisted_event_count = $persistedEvents.Count
    }
    $report.sse = [ordered]@{
        event_count = $events.Count
        first_event_id = $eventIds[0]
        last_event_id = $eventIds[-1]
        terminal = "run.completed"
        replay_event_count = $replayEvents.Count
        replay_terminal_count = @($replayEvents | Where-Object event_type -eq "run.completed").Count
    }
    $report.status = "passed"
} catch {
    $report.status = "failed"
    $report.error_code = Get-ErrorCode $_
    $report.error_summary = Get-SafeSummary $_
} finally {
    if (-not $KeepData -and $null -ne $context) {
        try { $csrfForCleanup = Get-Csrf $context } catch { $csrfForCleanup = $null }
        if (-not [string]::IsNullOrWhiteSpace([string]$report.session_id) -and $null -ne $csrfForCleanup) {
            try {
                [void](Invoke-Api -ApiContext $context -Method "DELETE" -Url "$JavaBaseUrl/api/sessions/$($report.session_id)" -Headers @{ "X-CSRF-Token" = $csrfForCleanup })
                $report.cleanup.session_soft_deleted = $true
            } catch { Add-CleanupError "session cleanup failed" }
        }
        $context.Client.Dispose()
    }
    Restore-RuntimeEnvironment
    $report.finished_at = (Get-Date).ToUniversalTime().ToString("o")
}

$report.cleanup.errors = @($report.cleanup.errors)
Write-Output ($report | ConvertTo-Json -Depth 24)
if ($report.status -eq "failed") { exit 1 }
