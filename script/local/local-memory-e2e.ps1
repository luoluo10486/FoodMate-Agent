[CmdletBinding()]
param(
    [string]$JavaBaseUrl = "http://127.0.0.1:8080",
    [switch]$KeepData
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http -ErrorAction Stop

$report = [ordered]@{
    status = "running"
    execution = "deterministic_local_rest"
    started_at = (Get-Date).ToUniversalTime().ToString("o")
    account = $null
    memory = $null
    steps = [ordered]@{
        candidate_visible = $false
        expired_hidden = $false
        confirmed_visible = $false
        updated_visible = $false
        deleted_hidden = $false
        audit_checked = $false
    }
    cleanup = [ordered]@{
        requested = (-not $KeepData)
        account_deletion_requested = $false
        account_deletion_completed = $false
        errors = @()
    }
    error_code = $null
    error_summary = $null
}

$context = $null
$username = $null
$password = "FoodMateMemoryE2e!123"
$userId = $null
$memoryId = $null
$expiredMemoryId = $null
$accountDeletionRequested = $false

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
    return "LOCAL_MEMORY_E2E_FAILED"
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
    $client.Timeout = [TimeSpan]::FromSeconds(45)
    return [pscustomobject]@{ Handler = $handler; Client = $client }
}

function Invoke-Api(
    [object]$ApiContext,
    [string]$Method,
    [string]$Url,
    [object]$Payload = $null,
    [hashtable]$Headers = @{}
) {
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), [System.Uri]$Url)
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

function Get-CookieValue([object]$ApiContext, [string]$Name) {
    $cookies = $ApiContext.Handler.CookieContainer.GetCookies([System.Uri]$JavaBaseUrl)
    $cookie = $cookies | Where-Object Name -eq $Name | Select-Object -First 1
    if ($null -eq $cookie) { return $null }
    return $cookie.Value
}

function Get-Csrf([object]$ApiContext) {
    $value = Get-CookieValue $ApiContext "foodmate_csrf"
    if ([string]::IsNullOrWhiteSpace([string]$value)) { throw "foodmate_csrf cookie is missing" }
    return [string]$value
}

function Wait-HttpReady([string]$Url) {
    $deadline = (Get-Date).ToUniversalTime().AddSeconds(90)
    do {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 10
            if ($response.StatusCode -eq 200) { return }
        } catch { }
        Start-Sleep -Seconds 2
    } while ((Get-Date).ToUniversalTime() -lt $deadline)
    throw "Java readiness did not recover"
}

function Invoke-Psql([string]$Sql) {
    $result = & docker exec foodmate-postgres psql --no-psqlrc -X -v ON_ERROR_STOP=1 -U postgres -d FoodMate -At -c $Sql 2>&1
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL command failed" }
    return (($result | Out-String).Trim())
}

function Wait-AccountDeletion([long]$JobId, [long]$ExpectedUserId) {
    # 注销由后端异步 Worker 执行，这里只在本地受控环境轮询最终事实。
    $lastState = "missing"
    for ($attempt = 0; $attempt -lt 45; $attempt++) {
        $state = Invoke-Psql "SELECT status || '|' || COALESCE((SELECT CASE WHEN is_deleted THEN 'true' ELSE 'false' END FROM users WHERE user_id=$ExpectedUserId),'false') FROM account_deletion_jobs WHERE deletion_job_id=$JobId"
        if (-not [string]::IsNullOrWhiteSpace($state)) { $lastState = $state }
        if ($state -eq "completed|true") {
            return [pscustomobject]@{ Status = "completed"; UserDeleted = $true }
        }
        if ($state -match '^failed\|') { throw "account deletion job failed: $state" }
        Start-Sleep -Seconds 2
    }
    throw "account deletion job did not complete within the local verification window: $lastState"
}

function Get-Memories([object]$ApiContext) {
    $response = Invoke-Api $ApiContext "GET" "$JavaBaseUrl/api/memories"
    return @((Get-Field $response @("data")))
}

function Find-Memory([object[]]$Records, [long]$ExpectedId) {
    return $Records | Where-Object { [long](Get-Field $_ @("memory_id", "memoryId")) -eq $ExpectedId } | Select-Object -First 1
}

function Request-AccountDeletion([object]$ApiContext) {
    $csrf = Get-Csrf $ApiContext
    $response = Invoke-Api $ApiContext "POST" "$JavaBaseUrl/api/users/me/deletion" @{
        confirmation = "DELETE_MY_ACCOUNT"
        current_password = $password
    } @{ "X-CSRF-Token" = $csrf }
    $data = Get-Field $response @("data")
    $jobId = Get-Field $data @("deletion_job_id", "deletionJobId")
    if ([string]::IsNullOrWhiteSpace([string]$jobId)) { throw "account deletion response is incomplete" }
    return [long]$jobId
}

try {
    Wait-HttpReady "$JavaBaseUrl/actuator/health/readiness"
    $suffix = [Guid]::NewGuid().ToString("N").Substring(0, 12)
    $username = "local_memory_$suffix"
    $email = "$username@example.com"
    $context = New-ApiContext

    $registerResponse = Invoke-Api $context "POST" "$JavaBaseUrl/api/auth/register" @{
        username = $username
        email = $email
        password = $password
        nickname = "Local Memory E2E"
    }
    $registered = Get-Field $registerResponse @("data")
    $userId = [long](Get-Field $registered @("user_id", "userId"))
    if ($userId -le 0) { throw "registration response is incomplete" }
    $report.account = [ordered]@{ id = [string]$userId; username = $username; registered = $true }
    $csrf = Get-Csrf $context

    $memoryId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() * 1000 + (Get-Random -Minimum 0 -Maximum 999)
    $expiredMemoryId = $memoryId + 1
    $memoryKey = "local-memory-" + [Guid]::NewGuid().ToString("N")
    $insertSql = "INSERT INTO user_memories(memory_id,user_id,memory_type,memory_key,memory_value,confidence,source,scope,confirmation_status,expires_at,source_message_ids,suppressed_source_message_ids,created_by,updated_by) VALUES ($memoryId,$userId,'preference','$memoryKey',jsonb_build_object('preference','local-old'),0.92,'agent','private','conflict',CURRENT_TIMESTAMP + INTERVAL '1 day','[]'::jsonb,'[]'::jsonb,$userId,$userId)"
    [void](Invoke-Psql $insertSql)
    $expiredSql = "INSERT INTO user_memories(memory_id,user_id,memory_type,memory_key,memory_value,confidence,source,scope,confirmation_status,expires_at,source_message_ids,suppressed_source_message_ids,created_by,updated_by) VALUES ($expiredMemoryId,$userId,'preference','$memoryKey-expired',jsonb_build_object('preference','expired'),0.92,'agent','private','confirmed',CURRENT_TIMESTAMP - INTERVAL '1 second','[]'::jsonb,'[]'::jsonb,$userId,$userId)"
    [void](Invoke-Psql $expiredSql)
    $report.memory = [ordered]@{ id = [string]$memoryId; expired_id = [string]$expiredMemoryId }

    $records = Get-Memories $context
    if ($null -eq (Find-Memory $records $memoryId)) { throw "memory candidate is not visible through the API" }
    if ($null -ne (Find-Memory $records $expiredMemoryId)) { throw "expired memory remained visible through the API" }
    $report.steps.candidate_visible = $true
    $report.steps.expired_hidden = $true

    $confirmResponse = Invoke-Api $context "POST" "$JavaBaseUrl/api/memories/$memoryId/confirm" $null @{ "X-CSRF-Token" = $csrf }
    $confirmed = Get-Field $confirmResponse @("data")
    if ([string](Get-Field $confirmed @("confirmation_status", "confirmationStatus")) -ne "confirmed") {
        throw "memory confirmation did not return confirmed status"
    }
    $report.steps.confirmed_visible = $true

    $updateResponse = Invoke-Api $context "PATCH" "$JavaBaseUrl/api/memories/$memoryId" @{
        memoryValue = '{"preference":"local-new"}'
        scope = "private"
    } @{ "X-CSRF-Token" = $csrf; "Idempotency-Key" = ("local-memory-update-" + [Guid]::NewGuid().ToString("N")) }
    $updated = Get-Field $updateResponse @("data")
    $updatedValue = [string](Get-Field $updated @("memory_value", "memoryValue")) | ConvertFrom-Json
    if ([string](Get-Field $updatedValue @("preference")) -ne "local-new") { throw "memory update response did not contain the new value" }
    $report.steps.updated_visible = $true

    [void](Invoke-Api $context "DELETE" "$JavaBaseUrl/api/memories/$memoryId" $null @{ "X-CSRF-Token" = $csrf })
    $remaining = Get-Memories $context
    if ($null -ne (Find-Memory $remaining $memoryId)) { throw "deleted memory remained visible through the API" }
    $deleted = Invoke-Psql "SELECT COUNT(*) FROM user_memories WHERE memory_id=$memoryId AND user_id=$userId AND is_deleted=TRUE"
    if ([int]$deleted -ne 1) { throw "memory delete did not persist the soft-delete fact" }
    $report.steps.deleted_hidden = $true

    $auditCount = Invoke-Psql "SELECT COUNT(*) FROM operation_audits WHERE operator_id=$userId AND target_type='memory' AND target_id='$memoryId' AND action IN ('memory.update','memory.delete') AND result='success'"
    if ([int]$auditCount -lt 2) { throw "memory update/delete audit facts are incomplete" }
    $report.steps.audit_checked = $true
    $report.status = "passed"
} catch {
    $report.status = "failed"
    $report.error_code = Get-ErrorCode $_
    $report.error_summary = Get-SafeSummary $_
} finally {
    if ($null -ne $userId -and $userId -gt 0) {
        try {
            [void](Invoke-Psql "UPDATE user_memories SET is_deleted=TRUE,deleted_at=COALESCE(deleted_at,CURRENT_TIMESTAMP),deleted_by=$userId,updated_at=CURRENT_TIMESTAMP,updated_by=$userId WHERE user_id=$userId AND memory_id IN ($memoryId,$expiredMemoryId)")
        } catch { Add-CleanupError ("memory cleanup failed: " + (Get-SafeSummary $_)) }
        if (-not $KeepData -and $null -ne $context -and -not $accountDeletionRequested) {
            try {
                $deletionJobId = Request-AccountDeletion $context
                $accountDeletionRequested = $true
                $report.cleanup.account_deletion_requested = $true
                $deletionState = Wait-AccountDeletion $deletionJobId $userId
                $report.cleanup.account_deletion_completed = $true
                $report.cleanup.deletion_job_id = [string]$deletionJobId
                $report.cleanup.deletion_status = [string]$deletionState.Status
            } catch { Add-CleanupError ("account cleanup failed: " + (Get-SafeSummary $_)) }
        }
    }
    if ($null -ne $context) { $context.Client.Dispose() }
    if ($report.status -eq "passed" -and @($report.cleanup.errors).Count -gt 0) {
        $report.status = "failed"
        $report.error_code = "LOCAL_MEMORY_E2E_CLEANUP_FAILED"
        $report.error_summary = "cleanup failed"
    }
    $report.cleanup.errors = @($report.cleanup.errors)
    $report.finished_at = (Get-Date).ToUniversalTime().ToString("o")
}

Write-Output ($report | ConvertTo-Json -Depth 32)
if ($report.status -eq "failed") { exit 1 }
