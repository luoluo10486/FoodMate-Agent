$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$scriptPath = Join-Path $repoRoot "script/local/local-session-chat-e2e.ps1"
$scriptText = Get-Content -Raw -LiteralPath $scriptPath

if ($scriptText -notmatch "api/auth/register") { throw "local session Chat E2E must create an isolated account" }
if ($scriptText -notmatch "api/sessions") { throw "local session Chat E2E must use the real session API" }
if ($scriptText -notmatch "api/sessions/.+/messages") { throw "local session Chat E2E must use the real message API" }
if ($scriptText -notmatch "archive") { throw "local session Chat E2E must cover session archive" }
if ($scriptText -notmatch "unarchive") { throw "local session Chat E2E must cover session unarchive" }
if ($scriptText -notmatch "api/sessions/search") { throw "local session Chat E2E must cover session search" }
if ($scriptText -notmatch "api/sessions/deleted") { throw "local session Chat E2E must cover the recycle bin" }
if ($scriptText -notmatch "restore") { throw "local session Chat E2E must cover session restore" }
if ($scriptText -notmatch "Wait-RunTerminal") { throw "local session Chat E2E must wait for the AgentRun terminal state" }
if ($scriptText -notmatch "api/chat/runs/.+/cancel") { throw "local session Chat E2E must retain bounded cleanup cancellation" }
if ($scriptText -match "(?i)ExecutePaid|FOODMATE_E2E_ADMIN|API_KEY|Start-Job|ForEach-Object.*parallel|WarmupSeconds|SteadySeconds|reboot") {
    throw "local session Chat E2E must remain local and bounded"
}

Write-Output "local_session_chat_e2e_contract=passed"
