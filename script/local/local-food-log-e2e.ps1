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
    search = $null
    food_log = $null
    nutrition_analysis = $null
    cleanup = [ordered]@{
        requested = (-not $KeepData)
        food_log_deleted = $false
        errors = @()
    }
    error_code = $null
    error_summary = $null
}

$context = $null
$foodLogId = $null
$foodLogRevision = 0
$csrf = $null

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
    return "LOCAL_FOOD_LOG_E2E_FAILED"
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

function Search-NutritionFood([object]$ApiContext, [string]$Query) {
    $encodedQuery = [Uri]::EscapeDataString($Query)
    $response = Invoke-Api $ApiContext "GET" "$JavaBaseUrl/api/nutrition-foods/search?query=$encodedQuery&limit=12"
    $candidates = @((Get-Field $response @("data")))
    $selected = $candidates | Where-Object {
        [string](Get-Field $_ @("food_form", "foodForm")) -eq "cooked" -and
        [string](Get-Field $_ @("standard_name", "standardName")) -match "(?i)rice, white"
    } | Select-Object -First 1
    if ($null -eq $selected) { throw "cooked rice candidate missing" }
    return [pscustomobject]@{
        candidate_count = $candidates.Count
        nutrition_food_id = [string](Get-Field $selected @("nutrition_food_id", "nutritionFoodId"))
        standard_name = [string](Get-Field $selected @("standard_name", "standardName"))
    }
}

try {
    Wait-HttpReady "$JavaBaseUrl/actuator/health/readiness"
    $context = New-ApiContext
    $suffix = [Guid]::NewGuid().ToString("N").Substring(0, 12)
    $username = "local_food_log_$suffix"
    $registerPayload = @{
        username = $username
        email = "$username@example.com"
        password = "FoodMateE2e!123"
        nickname = "Local Food Log E2E"
    }
    [void](Invoke-Api $context "POST" "$JavaBaseUrl/api/auth/register" $registerPayload)
    $csrf = Get-Csrf $context
    $headers = @{ "X-CSRF-Token" = $csrf }
    $selected = Search-NutritionFood $context "米饭"
    $report.search = $selected

    $payload = @{
        meal_time = (Get-Date).ToUniversalTime().ToString("o")
        meal_type = "lunch"
        notes = "local-food-log-e2e"
        items = @(@{
                raw_name = "熟米饭"
                amount = 150
                unit = "g"
                nutrition_food_id = [long]$selected.nutrition_food_id
            })
    }
    $createHeaders = $headers + @{ "Idempotency-Key" = "local-food-log-$suffix" }
    $created = Invoke-Api $context "POST" "$JavaBaseUrl/api/food-logs" $payload $createHeaders
    $food = Get-Field $created @("data")
    $foodLogId = [string](Get-Field $food @("food_log_id", "foodLogId"))
    $foodLogRevision = [long](Get-Field $food @("revision"))
    $items = @((Get-Field $food @("items")))
    if ([string]::IsNullOrWhiteSpace($foodLogId) -or $foodLogRevision -le 0 -or $items.Count -ne 1) {
        throw "food log response is incomplete"
    }

    $analysisResponse = Invoke-Api $context "GET" "$JavaBaseUrl/api/nutrition-analysis?range=today"
    $analysis = Get-Field $analysisResponse @("data")
    $matchedItems = [int](Get-Field $analysis @("matched_items", "matchedItems"))
    if ($matchedItems -lt 1) { throw "nutrition analysis did not include the created food log" }
    $report.food_log = [ordered]@{
        id = $foodLogId
        item_count = $items.Count
        revision = $foodLogRevision
        nutrition_status = [string](Get-Field $items[0] @("nutrition_status", "nutritionStatus"))
    }
    $report.nutrition_analysis = [ordered]@{ matched_items = $matchedItems }
    $report.status = "passed"
} catch {
    $report.status = "failed"
    $report.error_code = Get-ErrorCode $_
    $report.error_summary = Get-SafeSummary $_
} finally {
    if (-not $KeepData -and $null -ne $context -and -not [string]::IsNullOrWhiteSpace($foodLogId) -and $foodLogRevision -gt 0) {
        try {
            # 使用格式化字符串拼接查询参数，避免 PowerShell 把问号后的文本并入变量名。
            $cleanupUrl = "{0}/api/food-logs/{1}?revision={2}" -f $JavaBaseUrl, $foodLogId, $foodLogRevision
            $cleanupHeaders = $headers + @{ "Idempotency-Key" = "local-food-log-cleanup-$foodLogId" }
            [void](Invoke-Api $context "DELETE" $cleanupUrl $null $cleanupHeaders)
            $report.cleanup.food_log_deleted = $true
        } catch {
            Add-CleanupError ("food log cleanup failed: " + (Get-SafeSummary $_))
        }
    }
    if ($null -ne $context) { $context.Client.Dispose() }
    if ($report.status -eq "passed" -and @($report.cleanup.errors).Count -gt 0) {
        $report.status = "failed"
        $report.error_code = "LOCAL_FOOD_LOG_E2E_CLEANUP_FAILED"
        $report.error_summary = "cleanup failed"
    }
    $report.finished_at = (Get-Date).ToUniversalTime().ToString("o")
}

$report.cleanup.errors = @($report.cleanup.errors)
Write-Output ($report | ConvertTo-Json -Depth 20)
if ($report.status -eq "failed") { exit 1 }
