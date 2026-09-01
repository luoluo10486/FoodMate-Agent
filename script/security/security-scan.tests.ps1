$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$scan = Join-Path $repoRoot "script/security/security-scan.ps1"

if (-not (Test-Path -LiteralPath $scan -PathType Leaf)) {
    throw "security scan script does not exist"
}

$output = (& $scan 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) {
    throw "security scan must allow secrets in ignored local .env files: $output"
}
if ($output -notmatch "security_scan_status=passed") {
    throw "security scan did not report a passed status: $output"
}
if ($output -notmatch "ignored_local_secret_files=") {
    throw "security scan did not report ignored local secret files: $output"
}

Write-Output "security_scan_tests=passed"
