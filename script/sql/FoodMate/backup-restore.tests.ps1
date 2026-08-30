$ErrorActionPreference = "Stop"

$target = Join-Path $PSScriptRoot "backup-restore.ps1"
$scriptText = Get-Content -Raw -LiteralPath $target

foreach ($required in @(
        '[string]$DockerContainer',
        'docker cp',
        'docker exec',
        'pg_restore',
        'DropRestoreDatabaseAfterValidation'
    )) {
    if ($scriptText -notmatch [regex]::Escape($required)) {
        throw "backup-restore.ps1 is missing Docker restore contract: $required"
    }
}

if ($scriptText -notmatch 'DockerContainer.*foodmate-postgres') {
    throw "Docker restore contract must document the local PostgreSQL container default"
}

Write-Output "backup_restore_contract=passed"
