[CmdletBinding()]
param(
    [string]$JavaBaseUrl = "http://127.0.0.1:8080",
    [switch]$KeepData
)

$ErrorActionPreference = "Stop"
# 某些 PowerShell 运行时不会自动加载 System.Net.Http，显式加载后再解析强类型参数。
Add-Type -AssemblyName System.Net.Http -ErrorAction Stop
$AdminUsername = [Environment]::GetEnvironmentVariable("FOODMATE_E2E_ADMIN_USERNAME", "Process")
$AdminPassword = [Environment]::GetEnvironmentVariable("FOODMATE_E2E_ADMIN_PASSWORD", "Process")

$report = [ordered]@{
    status = "running"
    started_at = (Get-Date).ToUniversalTime().ToString("o")
    search = @()
    food_log = $null
    nutrition_analysis = $null
    cleanup = [ordered]@{ requested = (-not $KeepData); food_log_deleted = $false; errors = @() }
    error_code = $null
    error_summary = $null
}
$context = $null
$foodLogId = $null
$foodLogRevision = $null

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
    return "NUTRITION_CATALOG_E2E_FAILED"
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
            $body = $Payload | ConvertTo-Json -Depth 24 -Compress
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

function Invoke-Login([object]$ApiContext) {
    if ([string]::IsNullOrWhiteSpace($AdminUsername) -or [string]::IsNullOrWhiteSpace($AdminPassword)) {
        throw "FOODMATE_E2E_ADMIN_USERNAME and FOODMATE_E2E_ADMIN_PASSWORD are required"
    }
    [void](Invoke-Api $ApiContext "POST" "$JavaBaseUrl/api/auth/login" @{ username_or_email = $AdminUsername; password = $AdminPassword })
    return Get-Csrf $ApiContext
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

function Search-And-Select([string]$Query, [string]$ExpectedPattern, [string]$ExpectedForm) {
    $encodedQuery = [Uri]::EscapeDataString($Query)
    $response = Invoke-Api $context "GET" "$JavaBaseUrl/api/nutrition-foods/search?query=$encodedQuery&limit=12"
    $candidates = @((Get-Field $response @("data")))
    if ($candidates.Count -eq 0) { throw "nutrition candidate search returned no result for $Query" }
    $selected = $candidates | Where-Object {
        [string](Get-Field $_ @("food_form", "foodForm")) -eq $ExpectedForm -and
        [string](Get-Field $_ @("standard_name", "standardName")) -match $ExpectedPattern
    } | Select-Object -First 1
    if ($null -eq $selected) { throw "nutrition candidate selection failed for $Query" }
    return [pscustomobject]@{
        query = $Query
        candidate_count = $candidates.Count
        nutrition_food_id = [string](Get-Field $selected @("nutrition_food_id", "nutritionFoodId"))
        standard_name = [string](Get-Field $selected @("standard_name", "standardName"))
        food_form = [string](Get-Field $selected @("food_form", "foodForm"))
    }
}

try {
    Wait-HttpReady "$JavaBaseUrl/actuator/health/readiness"
    $context = New-ApiContext
    $csrf = Invoke-Login $context
    $rice = Search-And-Select "米饭" "(?i)rice, white" "cooked"
    $chicken = Search-And-Select "鸡胸肉" "(?i)breast.*cooked.*roasted" "cooked"
    $report.search = @($rice, $chicken)
    $mealTime = (Get-Date).ToUniversalTime().ToString("o")
    $idempotencyKey = "codex-r1-nutrition-" + [guid]::NewGuid().ToString("N")
    $riceNutritionFoodId = [Convert]::ToInt64($rice.nutrition_food_id)
    $chickenNutritionFoodId = [Convert]::ToInt64($chicken.nutrition_food_id)
    $riceItem = @{ raw_name = "熟米饭"; amount = 150; unit = "g"; nutrition_food_id = $riceNutritionFoodId }
    $chickenItem = @{ raw_name = "烤鸡胸肉"; amount = 120; unit = "g"; nutrition_food_id = $chickenNutritionFoodId }
    $payloadItems = @($riceItem, $chickenItem)
    $payload = @{
        meal_time = $mealTime
        meal_type = "lunch"
        notes = "codex-r1-nutrition-catalog"
        items = $payloadItems
    }
    $created = Invoke-Api $context "POST" "$JavaBaseUrl/api/food-logs" $payload (@{ "X-CSRF-Token" = $csrf; "Idempotency-Key" = $idempotencyKey })
    $food = Get-Field $created @("data")
    $foodLogId = [string](Get-Field $food @("food_log_id", "foodLogId"))
    $foodLogRevision = [long](Get-Field $food @("revision"))
    $items = @((Get-Field $food @("items")))
    if ($items.Count -ne 2) { throw "nutrition food log item count is invalid" }
    if (@($items | Where-Object { [string](Get-Field $_ @("nutrition_status", "nutritionStatus")) -ne "matched" }).Count -ne 0) {
        throw "nutrition food log contains an unmatched item"
    }
    $selectedIds = @($rice.nutrition_food_id, $chicken.nutrition_food_id)
    foreach ($item in $items) {
        if ($selectedIds -notcontains [string](Get-Field $item @("nutrition_food_id", "nutritionFoodId"))) {
            throw "nutrition food log returned an unexpected catalog id"
        }
    }
    $report.food_log = [ordered]@{ id = $foodLogId; revision = $foodLogRevision; item_count = $items.Count; matched_item_count = 2 }
    $analysisResponse = Invoke-Api $context "GET" "$JavaBaseUrl/api/nutrition-analysis?range=today"
    $analysis = Get-Field $analysisResponse @("data")
    $totalItems = [int](Get-Field $analysis @("total_items", "totalItems"))
    $matchedItems = [int](Get-Field $analysis @("matched_items", "matchedItems"))
    if ($totalItems -lt 2 -or $matchedItems -lt 2) { throw "nutrition analysis did not include the selected food log" }
    $report.nutrition_analysis = [ordered]@{
        range = [string](Get-Field $analysis @("range"))
        total_items = $totalItems
        matched_items = $matchedItems
        coverage = [string](Get-Field $analysis @("coverage"))
    }
    $report.status = "passed"
} catch {
    $report.status = "failed"
    $report.error_code = Get-ErrorCode $_
    $report.error_summary = Get-SafeSummary $_
} finally {
    if (-not $KeepData -and $null -ne $context -and -not [string]::IsNullOrWhiteSpace($foodLogId) -and $foodLogRevision -gt 0) {
        try {
            $cleanupKey = "codex-r1-nutrition-cleanup-" + [guid]::NewGuid().ToString("N")
            [void](Invoke-Api $context "DELETE" "$JavaBaseUrl/api/food-logs/$foodLogId`?revision=$foodLogRevision" $null (@{ "X-CSRF-Token" = $csrf; "Idempotency-Key" = $cleanupKey }))
            $report.cleanup.food_log_deleted = $true
        } catch { Add-CleanupError "food log cleanup failed" }
    }
    if ($null -ne $context) { $context.Client.Dispose() }
    $report.finished_at = (Get-Date).ToUniversalTime().ToString("o")
}

$report.cleanup.errors = @($report.cleanup.errors)
Write-Output ($report | ConvertTo-Json -Depth 20)
if ($report.status -eq "failed") { exit 1 }
