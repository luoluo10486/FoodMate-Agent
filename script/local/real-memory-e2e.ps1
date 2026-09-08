[CmdletBinding()]
param(
    [string]$JavaBaseUrl = "http://127.0.0.1:8080"
)

$ErrorActionPreference = "Stop"
# 显式加载 HTTP 类型，保证 Windows PowerShell 5.1 与 PowerShell 7 使用同一入口。
Add-Type -AssemblyName System.Net.Http -ErrorAction Stop
$AdminUsername = [Environment]::GetEnvironmentVariable("FOODMATE_E2E_ADMIN_USERNAME", "Process")
$AdminPassword = [Environment]::GetEnvironmentVariable("FOODMATE_E2E_ADMIN_PASSWORD", "Process")

$report = [ordered]@{
    status = "running"
    started_at = (Get-Date).ToUniversalTime().ToString("o")
    memory_id = $null
    steps = [ordered]@{
        inserted_visible = $false
        updated_visible = $false
        expired_hidden = $false
        deleted_hidden = $false
        context_filter_checked = $false
        audit_checked = $false
    }
    cleanup = [ordered]@{ attempted = $true; completed = $false }
    error_code = $null
    error_summary = $null
}
$context = $null
$userId = $null
$memoryId = $null

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
    if (-not [string]::IsNullOrWhiteSpace($AdminPassword)) { $message = $message.Replace($AdminPassword, "[redacted]") }
    if (-not [string]::IsNullOrWhiteSpace($AdminUsername)) { $message = $message.Replace($AdminUsername, "[redacted]") }
    $message = [regex]::Replace($message, '(?i)(api[_ -]?key|authorization|bearer|password|token)s*[:=]\s*\S+', '$1=[redacted]')
    $message = [regex]::Replace($message, '(?i)https?://\S+', "[url]")
    $message = [regex]::Replace($message, '\s+', " ").Trim()
    if ($message.Length -gt 256) { $message = $message.Substring(0, 256) }
    return $message
}

function Get-ErrorCode([object]$ErrorRecord) {
    $exception = if ($null -ne $ErrorRecord.Exception) { $ErrorRecord.Exception } else { $ErrorRecord }
    if ($null -ne $exception -and $null -ne $exception.Data -and $exception.Data.Contains("foodmate_error_code")) {
        return [string]$exception.Data["foodmate_error_code"]
    }
    return "MEMORY_E2E_FAILED"
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
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), $Url)
    try {
        if ($null -ne $Payload) {
            $body = $Payload | ConvertTo-Json -Depth 20 -Compress
            $request.Content = [System.Net.Http.StringContent]::new($body, [Text.Encoding]::UTF8, "application/json")
        }
        foreach ($header in $Headers.GetEnumerator()) {
            [void]$request.Headers.TryAddWithoutValidation([string]$header.Key, [string]$header.Value)
        }
        $response = $ApiContext.Client.SendAsync($request).GetAwaiter().GetResult()
        try {
            $responseBody = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            if (-not $response.IsSuccessStatusCode) { throw (New-HttpFailure $Method ([int]$response.StatusCode) $responseBody) }
            if ([string]::IsNullOrWhiteSpace($responseBody)) { return $null }
            return $responseBody | ConvertFrom-Json
        } finally { $response.Dispose() }
    } finally { $request.Dispose() }
}

function Get-Csrf([object]$ApiContext) {
    $cookies = $ApiContext.Handler.CookieContainer.GetCookies([Uri]$JavaBaseUrl)
    $cookie = $cookies | Where-Object Name -eq "foodmate_csrf" | Select-Object -First 1
    if ($null -eq $cookie) { throw "foodmate_csrf cookie is missing after login" }
    return $cookie.Value
}

function Invoke-Psql([string]$Sql) {
    $result = & docker exec foodmate-postgres psql --no-psqlrc -X -v ON_ERROR_STOP=1 -U postgres -d FoodMate -At -c $Sql 2>&1
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL command failed" }
    return (($result | Out-String).Trim())
}

function Assert-Value([object[]]$Records, [long]$ExpectedId, [string]$ExpectedValue) {
    $record = $Records | Where-Object { [long](Get-Field $_ @("memory_id", "memoryId")) -eq $ExpectedId } | Select-Object -First 1
    if ($null -eq $record) { throw "memory is not visible through the API" }
    $rawValue = [string](Get-Field $record @("memory_value", "memoryValue"))
    $value = $rawValue | ConvertFrom-Json
    if ([string](Get-Field $value @("preference")) -ne $ExpectedValue) { throw "memory value does not match the expected version" }
    return $record
}

function Get-Memories([object]$ApiContext) {
    $response = Invoke-Api $ApiContext "GET" "$JavaBaseUrl/api/memories"
    return @((Get-Field $response @("data")))
}

try {
    if ([string]::IsNullOrWhiteSpace($AdminUsername) -or [string]::IsNullOrWhiteSpace($AdminPassword)) {
        throw "FOODMATE_E2E_ADMIN_USERNAME and FOODMATE_E2E_ADMIN_PASSWORD are required"
    }
    $context = New-ApiContext
    $login = Invoke-Api $context "POST" "$JavaBaseUrl/api/auth/login" @{ username_or_email = $AdminUsername; password = $AdminPassword }
    $userId = [long](Get-Field (Get-Field $login @("data")) @("user_id", "userId"))
    if ($userId -le 0) { throw "login did not return a user id" }
    $csrf = Get-Csrf $context

    $memoryId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() * 1000 + (Get-Random -Minimum 0 -Maximum 999)
    $memoryKey = "codex_r6_memory_" + [guid]::NewGuid().ToString("N")
    $insertSql = "INSERT INTO user_memories(memory_id,user_id,memory_type,memory_key,memory_value,confidence,source,scope,confirmation_status,expires_at,source_message_ids,suppressed_source_message_ids,created_by,updated_by) VALUES ($memoryId,$userId,'preference','$memoryKey',jsonb_build_object('preference','r6-old'),0.99,'manual','private','confirmed',CURRENT_TIMESTAMP + INTERVAL '1 day','[]'::jsonb,'[]'::jsonb,$userId,$userId)"
    [void](Invoke-Psql $insertSql)
    $report.memory_id = $memoryId

    $records = Get-Memories $context
    [void](Assert-Value $records $memoryId "r6-old")
    $report.steps.inserted_visible = $true
    $eligible = Invoke-Psql "SELECT COUNT(*) FROM user_memories WHERE memory_id=$memoryId AND user_id=$userId AND is_deleted=FALSE AND confirmation_status='confirmed' AND (expires_at IS NULL OR expires_at>CURRENT_TIMESTAMP) AND memory_type IN ('preference','constraint','routine','cooking_skill','budget_habit','time_habit','interaction_preference','user_rule')"
    if ([int]$eligible -ne 1) { throw "new memory did not pass the Java context filter" }
    $report.steps.context_filter_checked = $true

    $updateKey = "codex-r6-memory-update-" + [guid]::NewGuid().ToString("N")
    $updated = Invoke-Api $context "PATCH" "$JavaBaseUrl/api/memories/$memoryId" @{ memoryValue = '{"preference":"r6-new"}'; scope = "private" } (@{ "X-CSRF-Token" = $csrf; "Idempotency-Key" = $updateKey })
    [void](Assert-Value @((Get-Field $updated @("data"))) $memoryId "r6-new")
    [void](Assert-Value (Get-Memories $context) $memoryId "r6-new")
    $report.steps.updated_visible = $true

    [void](Invoke-Psql "UPDATE user_memories SET expires_at=CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE memory_id=$memoryId AND user_id=$userId")
    if (@(Get-Memories $context | Where-Object { [long](Get-Field $_ @("memory_id", "memoryId")) -eq $memoryId }).Count -ne 0) { throw "expired memory remained visible through the API" }
    $expiredEligible = Invoke-Psql "SELECT COUNT(*) FROM user_memories WHERE memory_id=$memoryId AND user_id=$userId AND is_deleted=FALSE AND confirmation_status='confirmed' AND (expires_at IS NULL OR expires_at>CURRENT_TIMESTAMP)"
    if ([int]$expiredEligible -ne 0) { throw "expired memory passed the Java context filter" }
    $report.steps.expired_hidden = $true

    $deleteKey = "codex-r6-memory-delete-" + [guid]::NewGuid().ToString("N")
    [void](Invoke-Api $context "DELETE" "$JavaBaseUrl/api/memories/$memoryId" $null (@{ "X-CSRF-Token" = $csrf; "Idempotency-Key" = $deleteKey }))
    if (@(Get-Memories $context | Where-Object { [long](Get-Field $_ @("memory_id", "memoryId")) -eq $memoryId }).Count -ne 0) { throw "deleted memory remained visible through the API" }
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
    if ($null -ne $userId -and $userId -gt 0 -and $null -ne $memoryId) {
        try {
            [void](Invoke-Psql "UPDATE user_memories SET is_deleted=TRUE,deleted_at=COALESCE(deleted_at,CURRENT_TIMESTAMP),deleted_by=$userId,updated_at=CURRENT_TIMESTAMP,updated_by=$userId WHERE memory_id=$memoryId AND user_id=$userId")
            $report.cleanup.completed = $true
        } catch { $report.cleanup.completed = $false }
    }
    if ($null -ne $context) { $context.Client.Dispose() }
    $report.finished_at = (Get-Date).ToUniversalTime().ToString("o")
}

Write-Output ($report | ConvertTo-Json -Depth 20)
if ($report.status -eq "failed") { exit 1 }
