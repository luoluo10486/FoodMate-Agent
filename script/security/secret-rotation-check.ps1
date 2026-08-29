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
