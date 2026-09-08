$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$scriptPath = Join-Path $repoRoot "script/local/real-memory-e2e.ps1"
$scriptText = Get-Content -Raw -LiteralPath $scriptPath

if ($scriptText -notmatch 'api/memories') { throw "R6 script must use the real memory API" }
if ($scriptText -notmatch 'memory.update|memory.delete') { throw "R6 script must verify memory mutation actions" }
if ($scriptText -notmatch 'expires_at>CURRENT_TIMESTAMP') { throw "R6 script must verify expiration filtering" }
if ($scriptText -notmatch 'confirmation_status=.confirmed.') { throw "R6 script must verify confirmed memory filtering" }
if ($scriptText -notmatch 'operation_audits') { throw "R6 script must verify operation audit facts" }
if ($scriptText -notmatch 'FOODMATE_E2E_ADMIN_USERNAME|FOODMATE_E2E_ADMIN_PASSWORD') { throw "R6 script must read credentials from the process environment" }
if ($scriptText -match '(?i)Start-Job|ForEach-Object.*parallel|WarmupSeconds|SteadySeconds|reboot|acknowledge[-_ ]?loss|api[_ -]?key') { throw "R6 script must stay a bounded business-path check" }

Write-Output "real_memory_e2e_contract=passed"
