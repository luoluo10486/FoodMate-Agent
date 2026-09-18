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
    meal_plan = $null
    shopping_list = $null
    progress = $null
    cleanup = [ordered]@{
        requested = (-not $KeepData)
        meal_plan_deleted = $false
        deleted_plan_not_found = $false
        errors = @()
    }
    error_code = $null
    error_summary = $null
}

$context = $null
$mealPlanId = $null
$mealPlanRevision = 0
$mealPlanDeleted = $false
$headers = @{}

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
    return "LOCAL_MEAL_PLAN_E2E_FAILED"
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

function New-RequestHeaders([string]$IdempotencyKey = $null) {
    $result = @{} + $headers
    if (-not [string]::IsNullOrWhiteSpace($IdempotencyKey)) {
        $result["Idempotency-Key"] = $IdempotencyKey
    }
    return $result
}

function New-RevisionUrl([string]$Path, [long]$Revision) {
    return [string]::Concat(([string]$JavaBaseUrl).TrimEnd('/'), $Path, "?revision=", [string]$Revision)
}

try {
    Wait-HttpReady "$JavaBaseUrl/actuator/health/readiness"
    $context = New-ApiContext
    $suffix = [Guid]::NewGuid().ToString("N").Substring(0, 12)
    $username = "local_meal_plan_$suffix"
    $registerPayload = [ordered]@{
        username = $username
        email = "$username@example.com"
        password = "FoodMateE2e!123"
        nickname = "Local Meal Plan E2E"
    }
    [void](Invoke-Api $context "POST" "$JavaBaseUrl/api/auth/register" $registerPayload)
    $headers = @{ "X-CSRF-Token" = (Get-Csrf $context) }

    $daysPlanJson = @'
[
  {
    "breakfast": {
      "name": "Oatmeal Berry Bowl",
      "ingredients": [
        { "name": "Oatmeal", "amount": 60, "unit": "g" },
        { "name": "Blueberry", "amount": 80, "unit": "g" }
      ]
    },
    "lunch": {
      "name": "Chicken Quinoa Bowl",
      "ingredients": [
        { "name": "Chicken Breast", "amount": 150, "unit": "g" },
        { "name": "Quinoa", "amount": 80, "unit": "g" }
      ]
    },
    "dinner": {
      "name": "Tomato Tofu Soup",
      "ingredients": [
        { "name": "Tofu", "amount": 200, "unit": "g" },
        { "name": "Tomato", "amount": 150, "unit": "g" }
      ]
    }
  }
]
'@
    $parsedDaysPlan = $daysPlanJson | ConvertFrom-Json
    $daysPlan = @($parsedDaysPlan)
    if ($parsedDaysPlan.PSObject.Properties.Name -contains "value" -and $parsedDaysPlan.PSObject.Properties.Name -contains "Count") {
        $daysPlan = @($parsedDaysPlan.value)
    }

    $createPayload = [ordered]@{
        plan_name = "Local Meal Plan E2E"
        people = 1
        days = 1
        budget = 120
        calorie_target = 2200
        protein_target = 130
        allergens = @()
        dislikes = @()
        days_plan = $daysPlan
    }
    $createdResponse = Invoke-Api $context "POST" "$JavaBaseUrl/api/meal-plans" $createPayload (New-RequestHeaders "local-meal-plan-create-$suffix")
    $created = Get-Field $createdResponse @("data")
    $mealPlanId = [string](Get-Field $created @("meal_plan_id", "mealPlanId"))
    $mealPlanRevision = [long](Get-Field $created @("revision"))
    $createdDays = @((Get-Field $created @("days_plan", "daysPlan")))
    if ([string]::IsNullOrWhiteSpace($mealPlanId) -or $mealPlanRevision -ne 1 -or $createdDays.Count -ne 1) {
        throw "meal plan create response is incomplete"
    }
    $report.meal_plan = [ordered]@{
        id = $mealPlanId
        status_after_create = [string](Get-Field $created @("status"))
        revision_after_create = $mealPlanRevision
        days = $createdDays.Count
    }

    $readResponse = Invoke-Api $context "GET" "$JavaBaseUrl/api/meal-plans/$mealPlanId"
    $readPlan = Get-Field $readResponse @("data")
    if ([string](Get-Field $readPlan @("meal_plan_id", "mealPlanId")) -ne $mealPlanId) {
        throw "meal plan detail did not return the created plan"
    }

    $listResponse = Invoke-Api $context "GET" "$JavaBaseUrl/api/meal-plans"
    $listedPlans = @((Get-Field $listResponse @("data")))
    $listedPlan = $listedPlans | Where-Object { [string](Get-Field $_ @("meal_plan_id", "mealPlanId")) -eq $mealPlanId } | Select-Object -First 1
    if ($null -eq $listedPlan) { throw "meal plan list did not include the created plan" }

    $updatePayload = [ordered]@{
        plan_name = "Local Meal Plan E2E Updated"
        people = 1
        days = 1
        budget = 120
        calorie_target = 2200
        protein_target = 130
        allergens = @()
        dislikes = @()
        days_plan = $daysPlan
    }
    $updatedResponse = Invoke-Api -ApiContext $context -Method "PATCH" -Url (New-RevisionUrl "/api/meal-plans/$mealPlanId" $mealPlanRevision) -Payload $updatePayload -Headers (New-RequestHeaders "local-meal-plan-update-$suffix")
    $updated = Get-Field $updatedResponse @("data")
    $mealPlanRevision = [long](Get-Field $updated @("revision"))
    if ([string](Get-Field $updated @("status")) -ne "draft" -or $mealPlanRevision -ne 2) {
        throw "meal plan update did not return draft revision 2"
    }
    $report.meal_plan.status_after_update = [string](Get-Field $updated @("status"))
    $report.meal_plan.revision_after_update = $mealPlanRevision

    $validatedResponse = Invoke-Api -ApiContext $context -Method "POST" -Url (New-RevisionUrl "/api/meal-plans/$mealPlanId/validate" $mealPlanRevision) -Payload $null -Headers (New-RequestHeaders "local-meal-plan-validate-$suffix")
    $validated = Get-Field $validatedResponse @("data")
    $mealPlanRevision = [long](Get-Field $validated @("revision"))
    $validation = Get-Field $validated @("validation")
    $report.meal_plan.validation = $validation
    if ([string](Get-Field $validated @("status")) -ne "validated" -or -not [bool](Get-Field $validation @("valid"))) {
        throw "meal plan validation did not return validated=true"
    }

    $savedResponse = Invoke-Api -ApiContext $context -Method "POST" -Url (New-RevisionUrl "/api/meal-plans/$mealPlanId/save" $mealPlanRevision) -Payload $null -Headers (New-RequestHeaders "local-meal-plan-save-$suffix")
    $saved = Get-Field $savedResponse @("data")
    $mealPlanRevision = [long](Get-Field $saved @("revision"))
    if ([string](Get-Field $saved @("status")) -ne "saved") {
        throw "meal plan save did not return saved status"
    }
    $report.meal_plan.status_after_save = [string](Get-Field $saved @("status"))
    $report.meal_plan.revision_after_save = $mealPlanRevision

    $shoppingResponse = Invoke-Api -ApiContext $context -Method "POST" -Url "$JavaBaseUrl/api/meal-plans/$mealPlanId/shopping-list" -Payload $null -Headers $headers
    $shopping = Get-Field $shoppingResponse @("data")
    $shoppingItems = @((Get-Field $shopping @("items")))
    $shoppingListId = [string](Get-Field $shopping @("shopping_list_id", "shoppingListId"))
    if ([string]::IsNullOrWhiteSpace($shoppingListId) -or $shoppingItems.Count -lt 1) {
        throw "shopping list response is incomplete"
    }
    $shoppingItem = $shoppingItems | Select-Object -First 1
    $shoppingItemId = [string](Get-Field $shoppingItem @("shopping_list_item_id", "shoppingListItemId"))
    if ([string]::IsNullOrWhiteSpace($shoppingItemId)) { throw "shopping list item id is missing" }

    $readShoppingResponse = Invoke-Api $context "GET" "$JavaBaseUrl/api/meal-plans/$mealPlanId/shopping-list"
    $readShopping = Get-Field $readShoppingResponse @("data")
    if ([string](Get-Field $readShopping @("shopping_list_id", "shoppingListId")) -ne $shoppingListId) {
        throw "shopping list read did not return the created list"
    }

    $updatedShoppingResponse = Invoke-Api $context "PATCH" "$JavaBaseUrl/api/meal-plans/$mealPlanId/shopping-list/items/$shoppingItemId" ([ordered]@{ purchased = $true }) (New-RequestHeaders "local-shopping-item-$suffix")
    $updatedShopping = Get-Field $updatedShoppingResponse @("data")
    $updatedItems = @((Get-Field $updatedShopping @("items")))
    $updatedItem = $updatedItems | Where-Object { [string](Get-Field $_ @("shopping_list_item_id", "shoppingListItemId")) -eq $shoppingItemId } | Select-Object -First 1
    if ($null -eq $updatedItem -or -not [bool](Get-Field $updatedItem @("purchased"))) {
        throw "shopping item purchased state was not persisted"
    }
    $report.shopping_list = [ordered]@{
        id = $shoppingListId
        item_count = $shoppingItems.Count
        toggled_item_id = $shoppingItemId
        toggled_purchased = $true
    }

    $progressResponse = Invoke-Api $context "GET" "$JavaBaseUrl/api/meal-plans/$mealPlanId/progress"
    $progress = Get-Field $progressResponse @("data")
    $progressPlanId = [string](Get-Field $progress @("meal_plan_id", "mealPlanId"))
    $executableCount = [int](Get-Field $progress @("executable_meal_count", "executableMealCount"))
    $completedCount = [int](Get-Field $progress @("completed_meal_count", "completedMealCount"))
    if ($progressPlanId -ne $mealPlanId -or $executableCount -lt 3 -or $completedCount -lt 0) {
        throw "meal plan progress response is incomplete"
    }
    $report.progress = [ordered]@{
        executable_meal_count = $executableCount
        completed_meal_count = $completedCount
    }

    $deleteRequestUrl = [string]::Concat(([string]$JavaBaseUrl).TrimEnd('/'), "/api/meal-plans/", [string]$mealPlanId, "?revision=", [string]$mealPlanRevision)
    $deleteUri = [System.Uri]$deleteRequestUrl
    [void](Invoke-Api -ApiContext $context -Method "DELETE" -Url $deleteUri.AbsoluteUri -Payload $null -Headers (New-RequestHeaders "local-meal-plan-delete-$suffix"))
    $mealPlanDeleted = $true
    $report.cleanup.meal_plan_deleted = $true

    $deletedRevision = $mealPlanRevision + 1
    $restoredResponse = Invoke-Api -ApiContext $context -Method "POST" -Url (New-RevisionUrl "/api/meal-plans/$mealPlanId/restore" $deletedRevision) -Payload $null -Headers (New-RequestHeaders "local-meal-plan-restore-$suffix")
    $restored = Get-Field $restoredResponse @("data")
    $mealPlanRevision = [long](Get-Field $restored @("revision"))
    if ([bool](Get-Field $restored @("deleted")) -or [string](Get-Field $restored @("status")) -ne "saved") {
        throw "meal plan restore did not return an active saved plan"
    }
    $mealPlanDeleted = $false
    $report.meal_plan.status_after_restore = [string](Get-Field $restored @("status"))
    $report.meal_plan.revision_after_restore = $mealPlanRevision

    $restoredShoppingResponse = Invoke-Api -ApiContext $context -Method "POST" -Url "$JavaBaseUrl/api/meal-plans/$mealPlanId/shopping-list" -Payload $null -Headers $headers
    $restoredShopping = Get-Field $restoredShoppingResponse @("data")
    $restoredShoppingItems = @((Get-Field $restoredShopping @("items")))
    if ($restoredShoppingItems.Count -lt 1) { throw "shopping list was not regenerated after restore" }

    $deleteRequestUrl = [string]::Concat(([string]$JavaBaseUrl).TrimEnd('/'), "/api/meal-plans/", [string]$mealPlanId, "?revision=", [string]$mealPlanRevision)
    $deleteUri = [System.Uri]$deleteRequestUrl
    [void](Invoke-Api -ApiContext $context -Method "DELETE" -Url $deleteUri.AbsoluteUri -Payload $null -Headers (New-RequestHeaders "local-meal-plan-final-delete-$suffix"))
    $mealPlanDeleted = $true
    $report.cleanup.meal_plan_deleted = $true

    $deletedRevision = $mealPlanRevision + 1
    try {
        [void](Invoke-Api $context "GET" "$JavaBaseUrl/api/meal-plans/$mealPlanId")
        throw "deleted meal plan remained readable"
    } catch {
        $deleteVerificationCode = Get-ErrorCode $_
        if ($deleteVerificationCode -notin @("NOT_FOUND", "HTTP_404")) { throw }
        $report.cleanup.deleted_plan_not_found = $true
    }
    $report.status = "passed"
} catch {
    $report.status = "failed"
    $report.error_code = Get-ErrorCode $_
    $report.error_summary = Get-SafeSummary $_
} finally {
    if (-not $KeepData -and $null -ne $context -and -not $mealPlanDeleted -and -not [string]::IsNullOrWhiteSpace($mealPlanId) -and $mealPlanRevision -gt 0) {
        try {
            $cleanupRequestUrl = [string]::Concat(([string]$JavaBaseUrl).TrimEnd('/'), "/api/meal-plans/", [string]$mealPlanId, "?revision=", [string]$mealPlanRevision)
            $cleanupUri = [System.Uri]$cleanupRequestUrl
            [void](Invoke-Api -ApiContext $context -Method "DELETE" -Url $cleanupUri.AbsoluteUri -Payload $null -Headers (New-RequestHeaders "local-meal-plan-cleanup-$mealPlanId"))
            $report.cleanup.meal_plan_deleted = $true
        } catch {
            Add-CleanupError ("meal plan cleanup failed: " + (Get-SafeSummary $_))
        }
    }
    if ($null -ne $context) { $context.Client.Dispose() }
    if ($report.status -eq "passed" -and @($report.cleanup.errors).Count -gt 0) {
        $report.status = "failed"
        $report.error_code = "LOCAL_MEAL_PLAN_E2E_CLEANUP_FAILED"
        $report.error_summary = "cleanup failed"
    }
    $report.finished_at = (Get-Date).ToUniversalTime().ToString("o")
}

$report.cleanup.errors = @($report.cleanup.errors)
Write-Output ($report | ConvertTo-Json -Depth 32)
if ($report.status -eq "failed") { exit 1 }
