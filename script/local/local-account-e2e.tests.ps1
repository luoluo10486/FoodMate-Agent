$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$scriptPath = Join-Path $repoRoot "script/local/local-account-e2e.ps1"
$scriptText = Get-Content -Raw -LiteralPath $scriptPath

if ($scriptText -notmatch "api/auth/register") { throw "local account E2E must cover registration" }
if ($scriptText -notmatch "api/auth/login") { throw "local account E2E must cover login" }
if ($scriptText -notmatch "api/auth/logout") { throw "local account E2E must cover logout" }
if ($scriptText -notmatch "api/auth/refresh") { throw "local account E2E must cover refresh token rotation" }
if ($scriptText -notmatch "api/users/me/profile") { throw "local account E2E must cover profile read and update" }
if ($scriptText -notmatch "api/users/me/sessions") { throw "local account E2E must cover session listing and revocation" }
if ($scriptText -notmatch "api/users/me/password") { throw "local account E2E must cover password change" }
if ($scriptText -notmatch "api/users/me/deletion") { throw "local account E2E must clean up through the account deletion contract" }
if ($scriptText -notmatch "foodmate_csrf") { throw "local account E2E must verify the CSRF cookie" }
if ($scriptText -notmatch "foodmate_refresh") { throw "local account E2E must verify the refresh cookie" }
if ($scriptText -notmatch "DELETE_MY_ACCOUNT") { throw "local account E2E must use the explicit account deletion confirmation" }
if ($scriptText -match "(?i)ExecutePaid|FOODMATE_E2E_ADMIN|API_KEY|Start-Job|ForEach-Object.*parallel|WarmupSeconds|SteadySeconds|reboot") {
    throw "local account E2E must remain local and bounded"
}

Write-Output "local_account_e2e_contract=passed"
