[CmdletBinding()]
param(
    [switch]$Strict,
    [switch]$RunNpmAudit,
    [switch]$RunPythonAudit,
    [switch]$RunMavenDependencyCheck
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$failures = [System.Collections.Generic.List[string]]::new()
$skipped = [System.Collections.Generic.List[string]]::new()

function Invoke-ProcessText([string]$FilePath, [string[]]$Arguments) {
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $FilePath
    $startInfo.WorkingDirectory = (Get-Location).Path
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    foreach ($argument in $Arguments) { [void]$startInfo.ArgumentList.Add($argument) }
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    [void]$process.Start()
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    [pscustomobject]@{ ExitCode = $process.ExitCode; Stdout = $stdout; Stderr = $stderr }
}

function Get-ScannableWorktreeFiles([string]$Root, [string[]]$TrackedFiles) {
    $extensions = @(
        '.c', '.cc', '.conf', '.config', '.cpp', '.cs', '.env', '.example', '.go', '.h',
        '.html', '.ini', '.java', '.js', '.json', '.md', '.properties', '.ps1', '.py',
        '.sql', '.sh', '.ts', '.tsx', '.txt', '.xml', '.yaml', '.yml'
    )
    $candidatePaths = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::OrdinalIgnoreCase
    )
    foreach ($relativePath in $TrackedFiles) { [void]$candidatePaths.Add($relativePath) }

    $untrackedResult = Invoke-ProcessText "git.exe" @("ls-files", "--others", "--exclude-standard")
    if ($untrackedResult.ExitCode -eq 0) {
        foreach ($relativePath in ($untrackedResult.Stdout -split "`r?`n" | Where-Object { $_ })) {
            [void]$candidatePaths.Add($relativePath)
        }
    }

    # Ignored environment files are deliberately added explicitly. They are the most
    # important local files for this check, but must never be added to Git.
    foreach ($relativePath in @('.env', '.env.local', 'docker/.env', 'agent-runtime/.env')) {
        if (Test-Path -LiteralPath (Join-Path $Root $relativePath)) {
            [void]$candidatePaths.Add($relativePath)
        }
    }

    foreach ($relativePath in $candidatePaths) {
        $fullPath = Join-Path $Root $relativePath
        if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) { continue }
        if ($fullPath -match '(?i)[\\/](\.git|node_modules|target|dist|__pycache__|\.pytest_cache)[\\/]') {
            continue
        }
        $extension = [System.IO.Path]::GetExtension($fullPath).ToLowerInvariant()
        if ($extensions -contains $extension -or [System.IO.Path]::GetFileName($fullPath) -like '.env*') {
            $relativePath
        }
    }
}

Push-Location $repoRoot
try {
    $trackedResult = Invoke-ProcessText "git.exe" @("ls-files")
    if ($trackedResult.ExitCode -ne 0) {
        throw "unable to enumerate tracked files"
    }
    $trackedFiles = @($trackedResult.Stdout -split "`r?`n" | Where-Object { $_ })

    # These patterns intentionally target high-confidence credential formats only. Generic
    # words such as password/secret are too noisy for an executable repository gate.
    $secretPatterns = @(
        '-----BEGIN (RSA|EC|OPENSSH|DSA|PRIVATE) KEY-----',
        '(?i)\bAKIA[0-9A-Z]{16}\b',
        '(?i)\bsk-[A-Za-z0-9]{24,}\b',
        '(?i)\b(?:ghp|github_pat)_[A-Za-z0-9_]{20,}\b',
        '(?i)\bAIza[0-9A-Za-z_-]{30,}\b'
    )
    $secretHits = @()
    foreach ($pattern in $secretPatterns) {
        $grepResult = Invoke-ProcessText "git.exe" @("grep", "-n", "-I", "-P", $pattern, "--")
        if ($grepResult.ExitCode -eq 0) {
            $secretHits += @($grepResult.Stdout -split "`r?`n" | Where-Object { $_ })
        }
    }
    $workingTreeSecretHits = [System.Collections.Generic.List[string]]::new()
    $scanFiles = @(Get-ScannableWorktreeFiles $repoRoot $trackedFiles | Select-Object -Unique)
    foreach ($relativePath in $scanFiles) {
        $fullPath = Join-Path $repoRoot $relativePath
        try {
            $content = [System.IO.File]::ReadAllText($fullPath)
        } catch [System.IO.IOException] {
            continue
        } catch [System.UnauthorizedAccessException] {
            continue
        }
        foreach ($pattern in $secretPatterns) {
            if ([regex]::IsMatch($content, $pattern)) {
                $workingTreeSecretHits.Add($relativePath)
                break
            }
        }
    }
    if ($secretHits.Count -gt 0) {
        $failures.Add("tracked secret pattern hits: $($secretHits.Count)")
    }
    if ($workingTreeSecretHits.Count -gt 0) {
        $failures.Add("working tree secret pattern hits: $($workingTreeSecretHits.Count)")
    }

    $trackedEnvFiles = @(
        $trackedFiles |
            Where-Object { $_ -match '(^|/)\.env($|\.)' -and $_ -notmatch '(^|/)\.env\.example$' }
    )
    if ($trackedEnvFiles.Count -gt 0) {
        $failures.Add("tracked environment files: $($trackedEnvFiles.Count)")
    }

    if ($RunNpmAudit) {
        $npm = Get-Command npm.cmd -ErrorAction SilentlyContinue
        if ($null -eq $npm) {
            $skipped.Add("npm audit: npm.cmd not found")
        } elseif (-not (Test-Path -LiteralPath (Join-Path $repoRoot "foodmate-ui/package-lock.json"))) {
            $skipped.Add("npm audit: package-lock.json not found")
        } else {
            Push-Location (Join-Path $repoRoot "foodmate-ui")
            try {
                $auditOutput = (& npm.cmd audit --omit=dev --audit-level=high --package-lock-only 2>&1 | Out-String)
                $npmExitCode = $LASTEXITCODE
                if ($npmExitCode -ne 0) {
                    if ($auditOutput -match "(?i)(audit endpoint returned an error|not[_ ]implemented|404\s+Not\s+Found.*npm/v1/security|eai_again|enotfound|econnrefused|etimedout|network request failed)") {
                        $skipped.Add("npm audit: registry advisory endpoint unavailable")
                    } else {
                        $failures.Add("npm audit reported high-or-critical findings")
                    }
                }
            } finally {
                Pop-Location
            }
        }
    }

    if ($RunPythonAudit) {
        $python = Join-Path $repoRoot "agent-runtime/.venv/Scripts/python.exe"
        if (-not (Test-Path -LiteralPath $python)) {
            $skipped.Add("Python audit: agent-runtime/.venv interpreter not found")
        } else {
            & $python -c "import importlib.util; raise SystemExit(0 if importlib.util.find_spec('pip_audit') else 2)"
            if ($LASTEXITCODE -ne 0) {
                $skipped.Add("Python audit: pip-audit is not installed in agent-runtime/.venv")
            } else {
                & $python -m pip_audit --local --format json *> $null
                if ($LASTEXITCODE -ne 0) {
                    $failures.Add("pip-audit reported findings or could not complete")
                }
            }
        }
    }

    if ($RunMavenDependencyCheck) {
        $maven = Join-Path $repoRoot "mvnw.cmd"
        if (-not (Test-Path -LiteralPath $maven)) {
            $skipped.Add("OWASP dependency check: mvnw.cmd not found")
        } else {
            & $maven -B -ntp org.owasp:dependency-check-maven:12.1.0:check `
                "-DautoUpdate=true" "-DfailBuildOnCVSS=7" "-DskipTests=true" *> $null
            if ($LASTEXITCODE -ne 0) {
                $failures.Add("OWASP dependency-check reported findings or could not complete")
            }
        }
    }

    if ($Strict -and $skipped.Count -gt 0) {
        foreach ($item in $skipped) { $failures.Add("strict mode: $item") }
    }

    Write-Output "tracked_secret_scan_hits=$($secretHits.Count)"
    Write-Output "working_tree_secret_scan_hits=$($workingTreeSecretHits.Count)"
    Write-Output "tracked_env_files=$($trackedEnvFiles.Count)"
    Write-Output "skipped_checks=$($skipped.Count)"
    foreach ($item in $skipped) { Write-Warning $item }
    if ($failures.Count -gt 0) {
        foreach ($item in $failures) { Write-Error $item }
        exit 1
    }
    Write-Output "security_scan_status=passed"
} finally {
    Pop-Location
}
