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
    profile = $null
    sessions = $null
    refresh = $null
    logout = $false
    password = $false
    deletion = $null
    cleanup = [ordered]@{
        requested = (-not $KeepData)
        account_deletion_requested = $false
        errors = @()
    }
    error_code = $null
    error_summary = $null
}

$primaryContext = $null
$activeContext = $null
$secondContext = $null
$logoutContext = $null
$refreshContext = $null
$refreshReuseContext = $null
$newContext = $null
$username = $null
$initialPassword = "FoodMateE2e!123"
$updatedPassword = "FoodMateE2e!456"
$activePassword = $initialPassword
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
    return "LOCAL_ACCOUNT_E2E_FAILED"
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
    $requestUri = [System.Uri]$Url
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), $requestUri)
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

function Invoke-Login([object]$ApiContext, [string]$LoginName, [string]$Password) {
    [void](Invoke-Api -ApiContext $ApiContext -Method "POST" -Url "$JavaBaseUrl/api/auth/login" -Payload (@{
                username_or_email = $LoginName
                password = $Password
            }))
    return Get-Csrf $ApiContext
}

function Assert-Unauthorized([object]$ApiContext, [string]$Url, [string]$Method = "GET") {
    $unexpectedSuccess = $false
    try {
        [void](Invoke-Api -ApiContext $ApiContext -Method $Method -Url $Url)
        $unexpectedSuccess = $true
    } catch {
        $code = Get-ErrorCode $_
        if ($code -in @("AUTH_REQUIRED", "AUTH_REFRESH_TOKEN_INVALID", "HTTP_401")) { return }
        throw
    }
    if ($unexpectedSuccess) { throw "protected endpoint remained accessible: $Url" }
}

function Request-AccountDeletion([object]$ApiContext, [string]$Password) {
    $csrf = Get-Csrf $ApiContext
    $response = Invoke-Api -ApiContext $ApiContext -Method "POST" -Url "$JavaBaseUrl/api/users/me/deletion" -Payload (@{
        confirmation = "DELETE_MY_ACCOUNT"
        current_password = $Password
    }) -Headers @{ "X-CSRF-Token" = $csrf }
    $data = Get-Field $response @("data")
    $jobId = Get-Field $data @("deletion_job_id", "deletionJobId")
    if ([string]::IsNullOrWhiteSpace([string]$jobId)) { throw "account deletion response is incomplete" }
    return [long]$jobId
}

try {
    Wait-HttpReady "$JavaBaseUrl/actuator/health/readiness"
    $suffix = [Guid]::NewGuid().ToString("N").Substring(0, 12)
    $username = "local_account_$suffix"
    $email = "$username@example.com"
    $primaryContext = New-ApiContext
    $activeContext = $primaryContext

    $registerResponse = Invoke-Api -ApiContext $primaryContext -Method "POST" -Url "$JavaBaseUrl/api/auth/register" -Payload (@{
        username = $username
        email = $email
        password = $initialPassword
        nickname = "Local Account E2E"
    })
    $registered = Get-Field $registerResponse @("data")
    $registeredId = Get-Field $registered @("user_id", "userId")
    if ([string]::IsNullOrWhiteSpace([string]$registeredId)) { throw "registration response is incomplete" }
    $report.account = [ordered]@{
        id = [string]$registeredId
        username = $username
        registered = $true
    }

    $csrf = Get-Csrf $primaryContext
    $meResponse = Invoke-Api -ApiContext $primaryContext -Method "GET" -Url "$JavaBaseUrl/api/users/me"
    $me = Get-Field $meResponse @("data")
    if ([string](Get-Field $me @("username")) -ne $username) { throw "current user response did not match the registered account" }
    [void](Invoke-Api -ApiContext $primaryContext -Method "GET" -Url "$JavaBaseUrl/api/users/me/profile")

    $profileResponse = Invoke-Api -ApiContext $primaryContext -Method "PUT" -Url "$JavaBaseUrl/api/users/me/profile" -Payload (@{
        display_name = "Local Account E2E"
        gender = "male"
        height_cm = 175.5
        weight_kg = 70.2
        activity_level = "moderate"
        diet_goal = "maintain"
        calorie_target = 2200
        protein_target = 130
        allergens = @("peanut")
        dislikes = @("coriander")
        preferred_units = @{ weight = "g"; energy = "kcal" }
    }) -Headers @{ "X-CSRF-Token" = $csrf }
    $profile = Get-Field $profileResponse @("data")
    if ([string](Get-Field $profile @("display_name", "displayName")) -ne "Local Account E2E") {
        throw "profile update response did not contain the requested display name"
    }
    $report.profile = [ordered]@{
        updated = $true
        display_name = [string](Get-Field $profile @("display_name", "displayName"))
    }

    $secondContext = New-ApiContext
    [void](Invoke-Login $secondContext $username $initialPassword)
    $sessionsResponse = Invoke-Api -ApiContext $primaryContext -Method "GET" -Url "$JavaBaseUrl/api/users/me/sessions"
    $sessions = @(Get-Field $sessionsResponse @("data"))
    $secondarySession = $sessions | Where-Object {
        -not [bool](Get-Field $_ @("current")) -and
        -not [string]::IsNullOrWhiteSpace([string](Get-Field $_ @("auth_session_id", "authSessionId")))
    } | Select-Object -First 1
    if ($null -eq $secondarySession) { throw "session list did not expose the secondary login session" }
    $secondarySessionId = [long](Get-Field $secondarySession @("auth_session_id", "authSessionId"))
    [void](Invoke-Api -ApiContext $primaryContext -Method "DELETE" -Url "$JavaBaseUrl/api/users/me/sessions/$secondarySessionId" -Headers @{ "X-CSRF-Token" = $csrf })
    Assert-Unauthorized $secondContext "$JavaBaseUrl/api/users/me"
    $report.sessions = [ordered]@{
        listed = $sessions.Count
        current_found = (@($sessions | Where-Object { [bool](Get-Field $_ @("current")) })).Count -eq 1
        secondary_revoked = $true
    }

    $logoutContext = New-ApiContext
    $logoutCsrf = Invoke-Login $logoutContext $username $initialPassword
    [void](Invoke-Api -ApiContext $logoutContext -Method "POST" -Url "$JavaBaseUrl/api/auth/logout" -Headers @{ "X-CSRF-Token" = $logoutCsrf })
    Assert-Unauthorized $logoutContext "$JavaBaseUrl/api/users/me"
    $report.logout = $true

    $refreshContext = New-ApiContext
    [void](Invoke-Login $refreshContext $username $initialPassword)
    $oldRefresh = Get-CookieValue $refreshContext "foodmate_refresh"
    if ([string]::IsNullOrWhiteSpace([string]$oldRefresh)) { throw "refresh cookie is missing after login" }
    [void](Invoke-Api -ApiContext $refreshContext -Method "POST" -Url "$JavaBaseUrl/api/auth/refresh")
    $newRefresh = Get-CookieValue $refreshContext "foodmate_refresh"
    if ([string]::IsNullOrWhiteSpace([string]$newRefresh) -or $newRefresh -eq $oldRefresh) { throw "refresh token was not rotated" }
    $refreshReuseContext = New-ApiContext
    $refreshReuseContext.Handler.CookieContainer.SetCookies([System.Uri]$JavaBaseUrl, "foodmate_refresh=$oldRefresh")
    Assert-Unauthorized $refreshReuseContext "$JavaBaseUrl/api/auth/refresh" "POST"
    $report.refresh = [ordered]@{
        rotated = $true
        consumed_token_rejected = $true
    }

    $changePasswordResponse = Invoke-Api -ApiContext $primaryContext -Method "POST" -Url "$JavaBaseUrl/api/users/me/password" -Payload (@{
        current_password = $initialPassword
        new_password = $updatedPassword
    }) -Headers @{ "X-CSRF-Token" = $csrf }
    [void]$changePasswordResponse
    $activePassword = $updatedPassword
    $activeContext = $null
    Assert-Unauthorized $primaryContext "$JavaBaseUrl/api/users/me"

    $newContext = New-ApiContext
    $newCsrf = Invoke-Login $newContext $username $updatedPassword
    $newMeResponse = Invoke-Api -ApiContext $newContext -Method "GET" -Url "$JavaBaseUrl/api/users/me"
    $newMe = Get-Field $newMeResponse @("data")
    if ([string](Get-Field $newMe @("username")) -ne $username) { throw "new password login did not restore the account session" }
    $report.password = $true
    $activeContext = $newContext

    if (-not $KeepData) {
        $deletionJobId = Request-AccountDeletion $newContext $updatedPassword
        $accountDeletionRequested = $true
        $report.cleanup.account_deletion_requested = $true
        $report.deletion = [ordered]@{ job_id = [string]$deletionJobId; requested = $true }
        Assert-Unauthorized $newContext "$JavaBaseUrl/api/users/me"
    }
    $report.status = "passed"
} catch {
    $report.status = "failed"
    $report.error_code = Get-ErrorCode $_
    $report.error_summary = Get-SafeSummary $_
} finally {
    if (-not $KeepData -and -not $accountDeletionRequested -and -not [string]::IsNullOrWhiteSpace([string]$username)) {
        try {
            if ($null -eq $activeContext) {
                $activeContext = New-ApiContext
                [void](Invoke-Login $activeContext $username $activePassword)
            }
            $cleanupJobId = Request-AccountDeletion $activeContext $activePassword
            $accountDeletionRequested = $true
            $report.cleanup.account_deletion_requested = $true
            $report.deletion = [ordered]@{ job_id = [string]$cleanupJobId; requested = $true; cleanup = $true }
        } catch {
            Add-CleanupError ("account cleanup failed: " + (Get-SafeSummary $_))
        }
    }
    # 释放所有会话使用的客户端，避免重复执行 E2E 时累积连接资源。
    $contextsToDispose = @($primaryContext, $activeContext, $secondContext, $logoutContext, $refreshContext, $refreshReuseContext, $newContext)
    foreach ($context in $contextsToDispose) {
        if ($null -ne $context) { $context.Client.Dispose() }
    }
    if ($report.status -eq "passed" -and @($report.cleanup.errors).Count -gt 0) {
        $report.status = "failed"
        $report.error_code = "LOCAL_ACCOUNT_E2E_CLEANUP_FAILED"
        $report.error_summary = "cleanup failed"
    }
    $report.cleanup.errors = @($report.cleanup.errors)
    $report.finished_at = (Get-Date).ToUniversalTime().ToString("o")
}

Write-Output ($report | ConvertTo-Json -Depth 32)
if ($report.status -eq "failed") { exit 1 }
