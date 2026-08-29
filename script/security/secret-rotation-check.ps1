[CmdletBinding()]
param(
    [switch]$Strict,
    [switch]$RequireJwtOverlap
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$failures = [System.Collections.Generic.List[string]]::new()
$skipped = [System.Collections.Generic.List[string]]::new()

function Test-NonEmpty([string]$Value) {
    return -not [string]::IsNullOrWhiteSpace($Value)
}

function Get-KeyRing([string]$Value) {
    if (-not (Test-NonEmpty $Value)) { return @() }
    $entries = @($Value -split "," | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    $keys = [System.Collections.Generic.List[string]]::new()
    foreach ($entry in $entries) {
        $parts = $entry.Split("=", 2)
        if ($parts.Count -ne 2 -or -not ($parts[0] -match '^[A-Za-z0-9._-]{1,64}$') -or -not (Test-NonEmpty $parts[1])) {
            return $null
        }
        $keys.Add($parts[0])
    }
    return @($keys | Select-Object -Unique)
}

function Get-EnvironmentValue([string]$Name) {
    return [Environment]::GetEnvironmentVariable($Name)
}

function Test-RagConfiguration([System.Collections.Generic.List[string]]$Failures) {
    $mode = (Get-EnvironmentValue "FOODMATE_RAG_MODE")
    if (-not (Test-NonEmpty $mode)) { $mode = "stub" }
    $mode = $mode.Trim().ToLowerInvariant()
    if ($mode -eq "stub") {
        return
    }
    if ($mode -ne "local") {
        $Failures.Add("RAG mode must be stub or local")
        return
    }

    $provider = (Get-EnvironmentValue "FOODMATE_RAG_EMBEDDING_PROVIDER")
    if (-not (Test-NonEmpty $provider)) { $provider = "openai-compatible" }
    $provider = $provider.Trim().ToLowerInvariant()
    if ($provider -notin @("deterministic", "openai-compatible")) {
        $Failures.Add("RAG embedding provider is invalid")
        return
    }

    $required = @(
        "FOODMATE_RAG_MILVUS_URI",
        "FOODMATE_RAG_MILVUS_COLLECTION",
        "FOODMATE_RAG_BATCH_TOKEN_LIMIT",
        "FOODMATE_RAG_DAILY_TOKEN_LIMIT",
        "FOODMATE_RAG_BATCH_COST_LIMIT",
        "FOODMATE_RAG_DAILY_COST_LIMIT",
        "FOODMATE_RAG_PRICE_PER_MILLION_TOKENS",
        "FOODMATE_RAG_PRICE_VERSION"
    )
    if ($provider -eq "openai-compatible") {
        $required += @(
            "FOODMATE_RAG_EMBEDDING_BASE_URL",
            "FOODMATE_RAG_EMBEDDING_API_KEY",
            "FOODMATE_RAG_EMBEDDING_MODEL"
        )
    }
    foreach ($name in $required) {
        if (-not (Test-NonEmpty (Get-EnvironmentValue $name))) {
            $display = $name -replace "^FOODMATE_", ""
            if ($name -eq "FOODMATE_RAG_EMBEDDING_API_KEY") {
                $Failures.Add("RAG embedding API key is missing")
            } else {
                $Failures.Add("RAG configuration is missing: $display")
            }
        }
    }

    $chatKeyNames = @(
        "FOODMATE_MODEL_PROVIDER_CLOUD_PRIMARY_API_KEY",
        "FOODMATE_MODEL_PROVIDER_CLOUD_BACKUP_API_KEY",
        "FOODMATE_MODEL_PROVIDER_SILICONFLOW_API_KEY",
        "FOODMATE_DOCKER_MODEL_PROVIDER_CLOUD_PRIMARY_API_KEY",
        "FOODMATE_DOCKER_MODEL_PROVIDER_CLOUD_BACKUP_API_KEY",
        "FOODMATE_DOCKER_MODEL_PROVIDER_SILICONFLOW_API_KEY"
    )
    $chatConfigured = @($chatKeyNames | Where-Object { Test-NonEmpty (Get-EnvironmentValue $_) }).Count -gt 0
    $embeddingConfigured = Test-NonEmpty (Get-EnvironmentValue "FOODMATE_RAG_EMBEDDING_API_KEY")
    if ($provider -eq "openai-compatible" -and $chatConfigured -and -not $embeddingConfigured) {
        $Failures.Add("RAG embedding credentials must be supplied separately from Chat credentials")
    }
}

Push-Location $repoRoot
try {
    $enabled = $env:RUNTIME_SERVICE_JWT_ENABLED -eq "true"
    $javaRing = Get-KeyRing $env:RUNTIME_JAVA_PUBLIC_KEYS
    $pythonRing = Get-KeyRing $env:RUNTIME_PYTHON_PUBLIC_KEYS

    if ($enabled) {
        if ($null -eq $javaRing -or $null -eq $pythonRing) {
            $failures.Add("service JWT public key ring has invalid kid=key entries")
        } elseif ($javaRing.Count -eq 0 -or $pythonRing.Count -eq 0) {
            $failures.Add("service JWT is enabled but one or more public key rings are empty")
        }

        foreach ($pair in @(
                @{ Name = "RUNTIME_JAVA_KID"; Ring = $javaRing },
                @{ Name = "RUNTIME_PYTHON_KID"; Ring = $pythonRing }
            )) {
            $currentKid = [Environment]::GetEnvironmentVariable($pair.Name)
            if ($pair.Ring -and $pair.Ring -notcontains $currentKid) {
                $failures.Add("$($pair.Name) is not present in its configured public key ring")
            }
        }
    } else {
        $skipped.Add("service JWT rotation: RUNTIME_SERVICE_JWT_ENABLED is not true")
    }

    if ($RequireJwtOverlap) {
        if (-not $enabled) {
            $failures.Add("JWT overlap was required while service JWT is disabled")
        } elseif ($null -eq $javaRing -or $null -eq $pythonRing -or $javaRing.Count -lt 2 -or $pythonRing.Count -lt 2) {
            $failures.Add("JWT overlap requires at least two public keys for both runtimes")
        }
    }

    Test-RagConfiguration $failures

    $scan = Join-Path $repoRoot "script/security/security-scan.ps1"
    if (Test-Path -LiteralPath $scan) {
        & $scan
        if ($LASTEXITCODE -ne 0) { $failures.Add("repository secret scan failed") }
    } else {
        $skipped.Add("repository secret scan: script not found")
    }

    if ($Strict -and $skipped.Count -gt 0) {
        foreach ($item in $skipped) { $failures.Add("strict mode: $item") }
    }

    Write-Output "service_jwt_enabled=$($enabled.ToString().ToLowerInvariant())"
    Write-Output "java_public_key_count=$($javaRing.Count)"
    Write-Output "python_public_key_count=$($pythonRing.Count)"
    $ragMode = Get-EnvironmentValue "FOODMATE_RAG_MODE"
    if (-not (Test-NonEmpty $ragMode)) { $ragMode = "stub" }
    Write-Output "rag_mode=$($ragMode.Trim().ToLowerInvariant())"
    Write-Output "rag_embedding_key_configured=$((Test-NonEmpty (Get-EnvironmentValue 'FOODMATE_RAG_EMBEDDING_API_KEY')).ToString().ToLowerInvariant())"
    Write-Output "skipped_checks=$($skipped.Count)"
    foreach ($item in $skipped) { Write-Warning $item }
    if ($failures.Count -gt 0) {
        foreach ($item in $failures) { Write-Error $item }
        exit 1
    }
    Write-Output "secret_rotation_preflight=passed"
} finally {
    Pop-Location
}
