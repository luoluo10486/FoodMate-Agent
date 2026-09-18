$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$scriptPath = Join-Path $repoRoot "script/local/local-memory-e2e.ps1"
$scriptText = Get-Content -Raw -LiteralPath $scriptPath

if ($scriptText -notmatch 'api/memories') { throw "local memory script must use the real memory API" }
if ($scriptText -notmatch '/confirm') { throw "local memory script must verify memory confirmation" }
if ($scriptText -notmatch 'PATCH') { throw "local memory script must verify memory update" }
if ($scriptText -notmatch 'DELETE') { throw "local memory script must verify memory deletion" }
if ($scriptText -notmatch 'expires_at.*CURRENT_TIMESTAMP') { throw "local memory script must verify expiration filtering" }
if ($scriptText -notmatch 'operation_audits') { throw "local memory script must verify operation audit facts" }
if ($scriptText -notmatch 'api/auth/register') { throw "local memory script must create an isolated local account" }
if ($scriptText -notmatch 'account_deletion_requested') { throw "local memory script must request account cleanup" }
if ($scriptText -notmatch 'Wait-AccountDeletion') { throw "local memory script must verify account deletion completion" }
if ($scriptText -notmatch 'account_deletion_completed') { throw "local memory script must record account deletion completion" }
if ($scriptText -match '(?i)Start-Job|ForEach-Object.*parallel|ExecutePaid|api[_ -]?key') {
    throw "local memory script must stay a bounded deterministic REST check"
}

Write-Output "local_memory_e2e_contract=passed"
