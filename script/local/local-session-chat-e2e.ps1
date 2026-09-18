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
    session = $null
    message = $null
    search = $null
    run = $null
    cleanup = [ordered]@{
        requested = (-not $KeepData)
        session_deleted = $false
        errors = @()
    }
    error_code = $null
    error_summary = $null
}

$context = $null
$headers = @{}
$username = $null
$sessionId = $null
$messageId = $null
$runId = $null
$sessionDeleted = $false

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
    return "LOCAL_SESSION_CHAT_E2E_FAILED"
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

function Assert-Condition([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Get-PageItems([object]$Response) {
    $data = Get-Field $Response @("data")
    return @(Get-Field $data @("items"))
}

function Find-Session([object[]]$Items, [string]$WantedSessionId) {
    return $Items | Where-Object {
        [string](Get-Field $_ @("session_id", "sessionId")) -eq $WantedSessionId
    } | Select-Object -First 1
}

function Find-Message([object[]]$Items, [string]$WantedMessageId) {
    return $Items | Where-Object {
        [string](Get-Field $_ @("message_id", "messageId")) -eq $WantedMessageId
    } | Select-Object -First 1
}

function Wait-RunTerminal([object]$ApiContext, [string]$WantedRunId) {
    $terminalStatuses = @("completed", "succeeded", "success", "failed", "cancelled", "canceled", "superseded")
    $deadline = (Get-Date).ToUniversalTime().AddSeconds(90)
    $lastStatus = "unknown"
    do {
        $response = Invoke-Api -ApiContext $ApiContext -Method "GET" -Url "$JavaBaseUrl/api/chat/runs/$WantedRunId"
        $data = Get-Field $response @("data")
        $lastStatus = ([string](Get-Field $data @("status"))).ToLowerInvariant()
        if ($terminalStatuses -contains $lastStatus) { return $lastStatus }
        Start-Sleep -Seconds 2
    } while ((Get-Date).ToUniversalTime() -lt $deadline)
    return $lastStatus
}

function Try-CancelRun([object]$ApiContext, [string]$WantedRunId, [hashtable]$RequestHeaders) {
    if ([string]::IsNullOrWhiteSpace($WantedRunId)) { return }
    try {
        [void](Invoke-Api -ApiContext $ApiContext -Method "POST" -Url "$JavaBaseUrl/api/chat/runs/$WantedRunId/cancel" -Payload @{ reason = "local_session_chat_cleanup" } -Headers $RequestHeaders)
    } catch { }
}

try {
    Wait-HttpReady "$JavaBaseUrl/actuator/health/readiness"
    $context = New-ApiContext
    $suffix = [Guid]::NewGuid().ToString("N").Substring(0, 12)
    $username = "local_session_chat_$suffix"
    $title = "Local Session Chat $suffix"
    $renamedTitle = "Renamed Session Chat $suffix"
    $messageContent = "Workspace Chat message $suffix"
    $updatedContent = "Workspace Chat updated message $suffix"

    [void](Invoke-Api -ApiContext $context -Method "POST" -Url "$JavaBaseUrl/api/auth/register" -Payload (@{
                username = $username
                email = "$username@example.com"
                password = "FoodMateE2e!123"
                nickname = "Local Session Chat E2E"
            }))
    $headers = @{ "X-CSRF-Token" = (Get-Csrf $context) }

    $createdResponse = Invoke-Api -ApiContext $context -Method "POST" -Url "$JavaBaseUrl/api/sessions" -Payload (@{
            title = $title
            mode = "chat"
        }) -Headers $headers
    $created = Get-Field $createdResponse @("data")
    $sessionId = [string](Get-Field $created @("session_id", "sessionId"))
    Assert-Condition (-not [string]::IsNullOrWhiteSpace($sessionId)) "session creation response is incomplete"
    $report.session = [ordered]@{
        id = $sessionId
        title = [string](Get-Field $created @("title"))
        mode = [string](Get-Field $created @("mode"))
        status = [string](Get-Field $created @("status"))
    }

    $listResponse = Invoke-Api -ApiContext $context -Method "GET" -Url "$JavaBaseUrl/api/sessions?page=1&size=50" -Headers $headers
    $listed = Find-Session (Get-PageItems $listResponse) $sessionId
    Assert-Condition ($null -ne $listed) "created session is missing from active session list"

    [void](Invoke-Api -ApiContext $context -Method "PATCH" -Url "$JavaBaseUrl/api/sessions/$sessionId" -Payload @{ title = $renamedTitle } -Headers $headers)
    $renamedQuery = [Uri]::EscapeDataString($renamedTitle)
    $renamedListResponse = Invoke-Api -ApiContext $context -Method "GET" -Url "$JavaBaseUrl/api/sessions?page=1&size=50&q=$renamedQuery" -Headers $headers
    $renamed = Find-Session (Get-PageItems $renamedListResponse) $sessionId
    Assert-Condition ($null -ne $renamed) "renamed session is missing from query result"
    Assert-Condition ([string](Get-Field $renamed @("title")) -eq $renamedTitle) "session rename was not persisted"

    [void](Invoke-Api -ApiContext $context -Method "POST" -Url "$JavaBaseUrl/api/sessions/$sessionId/archive" -Headers $headers)
    $archivedResponse = Invoke-Api -ApiContext $context -Method "GET" -Url "$JavaBaseUrl/api/sessions?page=1&size=50&status=archived" -Headers $headers
    $archived = Find-Session (Get-PageItems $archivedResponse) $sessionId
    Assert-Condition ($null -ne $archived) "archived session is missing from archived list"
    Assert-Condition ([string](Get-Field $archived @("status")) -eq "archived") "session archive status was not persisted"

    [void](Invoke-Api -ApiContext $context -Method "POST" -Url "$JavaBaseUrl/api/sessions/$sessionId/unarchive" -Headers $headers)
    $activeResponse = Invoke-Api -ApiContext $context -Method "GET" -Url "$JavaBaseUrl/api/sessions?page=1&size=50&status=active" -Headers $headers
    Assert-Condition ($null -ne (Find-Session (Get-PageItems $activeResponse) $sessionId)) "unarchived session is missing from active list"

    $messageResponse = Invoke-Api -ApiContext $context -Method "POST" -Url "$JavaBaseUrl/api/sessions/$sessionId/messages" -Payload (@{
            role = "user"
            content = $messageContent
        }) -Headers $headers
    $message = Get-Field $messageResponse @("data")
    $messageId = [string](Get-Field $message @("message_id", "messageId"))
    $runId = [string](Get-Field $message @("agent_run_id", "agentRunId"))
    Assert-Condition (-not [string]::IsNullOrWhiteSpace($messageId)) "message creation response is incomplete"
    Assert-Condition (-not [string]::IsNullOrWhiteSpace($runId)) "message creation did not return an AgentRun id"
    Assert-Condition ([string](Get-Field $message @("role")) -eq "user") "created message role is not user"

    $runStatus = Wait-RunTerminal $context $runId
    Assert-Condition (@("completed", "succeeded", "success", "failed", "cancelled", "canceled", "superseded") -contains $runStatus) "AgentRun did not reach a terminal status: $runStatus"
    $report.run = [ordered]@{ id = $runId; status = $runStatus }

    $messagesResponse = Invoke-Api -ApiContext $context -Method "GET" -Url "$JavaBaseUrl/api/sessions/$sessionId/messages?page=1&size=100" -Headers $headers
    $persistedMessage = Find-Message (Get-PageItems $messagesResponse) $messageId
    Assert-Condition ($null -ne $persistedMessage) "created message is missing from session history"

    $updatedResponse = Invoke-Api -ApiContext $context -Method "PATCH" -Url "$JavaBaseUrl/api/sessions/$sessionId/messages/$messageId" -Payload @{ content = $updatedContent } -Headers $headers
    $updated = Get-Field $updatedResponse @("data")
    Assert-Condition ([string](Get-Field $updated @("message_id", "messageId")) -eq $messageId) "message update returned a different message"
    Assert-Condition ([string](Get-Field $updated @("content")) -eq $updatedContent) "message update was not persisted"

    $searchQuery = [Uri]::EscapeDataString($updatedContent)
    $searchResponse = Invoke-Api -ApiContext $context -Method "GET" -Url "$JavaBaseUrl/api/sessions/search?q=$searchQuery&page=1&size=50" -Headers $headers
    $searchItems = Get-PageItems $searchResponse
    $searchMatch = Find-Session $searchItems $sessionId
    Assert-Condition ($null -ne $searchMatch) "updated message content is missing from session search"
    $report.search = [ordered]@{
        query = $updatedContent
        match_count = $searchItems.Count
        session_found = $true
    }

    [void](Invoke-Api -ApiContext $context -Method "DELETE" -Url "$JavaBaseUrl/api/sessions/$sessionId/messages/$messageId" -Headers $headers)
    $afterMessageDeleteResponse = Invoke-Api -ApiContext $context -Method "GET" -Url "$JavaBaseUrl/api/sessions/$sessionId/messages?page=1&size=100" -Headers $headers
    Assert-Condition ($null -eq (Find-Message (Get-PageItems $afterMessageDeleteResponse) $messageId)) "deleted message is still visible in session history"
    $report.message = [ordered]@{
        id = $messageId
        created = $true
        updated = $true
        deleted = $true
    }

    [void](Invoke-Api -ApiContext $context -Method "DELETE" -Url "$JavaBaseUrl/api/sessions/$sessionId" -Headers $headers)
    $sessionDeleted = $true
    $activeAfterDeleteResponse = Invoke-Api -ApiContext $context -Method "GET" -Url "$JavaBaseUrl/api/sessions?page=1&size=50" -Headers $headers
    Assert-Condition ($null -eq (Find-Session (Get-PageItems $activeAfterDeleteResponse) $sessionId)) "deleted session is still visible in active list"
    $deletedResponse = Invoke-Api -ApiContext $context -Method "GET" -Url "$JavaBaseUrl/api/sessions/deleted?page=1&size=50" -Headers $headers
    Assert-Condition ($null -ne (Find-Session (Get-PageItems $deletedResponse) $sessionId)) "deleted session is missing from recycle bin"

    [void](Invoke-Api -ApiContext $context -Method "POST" -Url "$JavaBaseUrl/api/sessions/$sessionId/restore" -Headers $headers)
    $sessionDeleted = $false
    $restoredResponse = Invoke-Api -ApiContext $context -Method "GET" -Url "$JavaBaseUrl/api/sessions?page=1&size=50&status=active" -Headers $headers
    $restored = Find-Session (Get-PageItems $restoredResponse) $sessionId
    Assert-Condition ($null -ne $restored) "restored session is missing from active list"
    Assert-Condition ([string](Get-Field $restored @("status")) -eq "active") "restored session status is not active"

    [void](Invoke-Api -ApiContext $context -Method "DELETE" -Url "$JavaBaseUrl/api/sessions/$sessionId" -Headers $headers)
    $sessionDeleted = $true
    $finalDeletedResponse = Invoke-Api -ApiContext $context -Method "GET" -Url "$JavaBaseUrl/api/sessions/deleted?page=1&size=50" -Headers $headers
    Assert-Condition ($null -ne (Find-Session (Get-PageItems $finalDeletedResponse) $sessionId)) "final deleted session is missing from recycle bin"
    $report.session.status = "deleted_and_restored_and_deleted"
    $report.cleanup.session_deleted = $true
    $report.status = "passed"
} catch {
    $report.status = "failed"
    $report.error_code = Get-ErrorCode $_
    $report.error_summary = Get-SafeSummary $_
} finally {
    if (-not $KeepData -and $null -ne $context -and -not [string]::IsNullOrWhiteSpace([string]$sessionId) -and -not $sessionDeleted) {
        try {
            Try-CancelRun $context $runId $headers
            $activeResponse = Invoke-Api -ApiContext $context -Method "GET" -Url "$JavaBaseUrl/api/sessions?page=1&size=100" -Headers $headers
            if ($null -ne (Find-Session (Get-PageItems $activeResponse) $sessionId)) {
                [void](Invoke-Api -ApiContext $context -Method "DELETE" -Url "$JavaBaseUrl/api/sessions/$sessionId" -Headers $headers)
            }
            $sessionDeleted = $true
            $report.cleanup.session_deleted = $true
        } catch {
            Add-CleanupError ("session cleanup failed: " + (Get-SafeSummary $_))
        }
    }
    if ($null -ne $context) { $context.Client.Dispose() }
    if ($report.status -eq "passed" -and @($report.cleanup.errors).Count -gt 0) {
        $report.status = "failed"
        $report.error_code = "LOCAL_SESSION_CHAT_E2E_CLEANUP_FAILED"
        $report.error_summary = "cleanup failed"
    }
    $report.cleanup.errors = @($report.cleanup.errors)
    $report.finished_at = (Get-Date).ToUniversalTime().ToString("o")
}

Write-Output ($report | ConvertTo-Json -Depth 32)
if ($report.status -eq "failed") { exit 1 }
