[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^[A-Za-z0-9_]+$')]
    [string]$DatabaseName,

    [Parameter(Mandatory)]
    [ValidatePattern('^[A-Za-z0-9._-]+$')]
    [string]$Username,

    [string]$HostName = "127.0.0.1",
    [ValidateRange(1, 65535)]
    [int]$Port = 5432,
    [string]$BackupFile,
    [ValidatePattern('^[A-Za-z0-9_]+$')]
    [string]$RestoreDatabaseName,
    [switch]$Execute,
    [switch]$RunValidation
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$backupDirectory = (Resolve-Path (Join-Path $PSScriptRoot "backups")).Path
$validationFile = Join-Path $PSScriptRoot "validation.sql"

function Assert-LocalDatabase([string]$Name) {
    if ($Name -match '(?i)(^|_)(prod|production|stage|staging)($|_)') {
        throw "Refusing to operate on a production-like database name: $Name"
    }
}

function Resolve-BackupPath([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return Join-Path $backupDirectory ("{0}_{1}.dump" -f $DatabaseName, (Get-Date).ToUniversalTime().ToString("yyyyMMdd_HHmmss"))
    }
    $basePath = if ([System.IO.Path]::IsPathRooted($Value)) {
        $Value
    } else {
        Join-Path $backupDirectory $Value
    }
    $candidate = [System.IO.Path]::GetFullPath($basePath)
    if (-not $candidate.StartsWith($backupDirectory + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "BackupFile must stay under script/sql/FoodMate/backups"
    }
    return $candidate
}

function Require-Command([string]$Name) {
    if ($null -eq (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "$Name was not found on PATH"
    }
}

function Invoke-Pg([string]$Command, [string[]]$Arguments) {
    $output = & $Command @Arguments 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) {
        throw "$Command failed with exit code $LASTEXITCODE"
    }
    return $output.Trim()
}

function Test-DatabaseExists([string]$Name) {
    $query = "SELECT 1 FROM pg_database WHERE datname = '$Name'"
    $result = Invoke-Pg "psql" @(
        "--no-psqlrc", "--tuples-only", "--no-align", "--quiet",
        "--host", $HostName, "--port", $Port.ToString(), "--username", $Username,
        "--dbname", "postgres", "--command", $query
    )
    return $result.Trim() -eq "1"
}

Assert-LocalDatabase $DatabaseName
if ($RestoreDatabaseName) { Assert-LocalDatabase $RestoreDatabaseName }
if ($HostName -notin @("127.0.0.1", "localhost", "::1")) {
    throw "This local backup/restore entrypoint only permits localhost"
}
if ($RestoreDatabaseName -and $RestoreDatabaseName -eq $DatabaseName) {
    throw "RestoreDatabaseName must differ from DatabaseName"
}
if ($RunValidation -and -not $RestoreDatabaseName) {
    throw "RunValidation requires RestoreDatabaseName"
}
$backupPath = Resolve-BackupPath $BackupFile
if (-not (Test-Path -LiteralPath (Split-Path -Parent $backupPath) -PathType Container)) {
    throw "Backup directory does not exist"
}

Write-Output "database=$DatabaseName"
Write-Output "host=$HostName"
Write-Output "port=$Port"
Write-Output "backup_file=$backupPath"
Write-Output "restore_database=$(if ($RestoreDatabaseName) { $RestoreDatabaseName } else { '' })"
Write-Output "execute=$($Execute.ToString().ToLowerInvariant())"

if (-not $Execute) {
    Write-Output "backup_restore_preflight=passed"
    exit 0
}

Require-Command "pg_dump"
Require-Command "psql"
if ($RestoreDatabaseName) { Require-Command "createdb"; Require-Command "pg_restore" }
if (Test-Path -LiteralPath $backupPath) {
    throw "backup file already exists; refusing to overwrite or reuse it"
}
if (-not (Test-Path -LiteralPath $backupPath)) {
    if (-not $PSCmdlet.ShouldProcess($DatabaseName, "create PostgreSQL custom-format backup")) { exit 0 }
    Invoke-Pg "pg_dump" @(
        "--format=custom", "--no-owner", "--no-privileges", "--file", $backupPath,
        "--host", $HostName, "--port", $Port.ToString(), "--username", $Username,
        $DatabaseName
    ) | Out-Null
}
if (-not (Test-Path -LiteralPath $backupPath -PathType Leaf)) { throw "backup file was not created" }
$backupInfo = Get-Item -LiteralPath $backupPath
if ($backupInfo.Length -le 0) { throw "backup file is empty" }
$hash = (Get-FileHash -LiteralPath $backupPath -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Output "backup_bytes=$($backupInfo.Length)"
Write-Output "backup_sha256=$hash"

if ($RestoreDatabaseName) {
    if (Test-DatabaseExists $RestoreDatabaseName) {
        throw "restore database already exists; refusing to overwrite it"
    }
    if (-not $PSCmdlet.ShouldProcess($RestoreDatabaseName, "create and restore PostgreSQL backup")) { exit 0 }
    Invoke-Pg "createdb" @(
        "--host", $HostName, "--port", $Port.ToString(), "--username", $Username,
        $RestoreDatabaseName
    ) | Out-Null
    Invoke-Pg "pg_restore" @(
        "--exit-on-error", "--no-owner", "--no-privileges", "--host", $HostName,
        "--port", $Port.ToString(), "--username", $Username, "--dbname", $RestoreDatabaseName,
        $backupPath
    ) | Out-Null
    if ($RunValidation) {
        if (-not (Test-Path -LiteralPath $validationFile -PathType Leaf)) { throw "validation.sql was not found" }
        Invoke-Pg "psql" @(
            "--no-psqlrc", "--set", "ON_ERROR_STOP=1", "--host", $HostName,
            "--port", $Port.ToString(), "--username", $Username, "--dbname", $RestoreDatabaseName,
            "--file", $validationFile
        ) | Out-Null
        Write-Output "restore_validation=passed"
    }
}

Write-Output "backup_restore_status=passed"
