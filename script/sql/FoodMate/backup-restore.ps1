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
    # DockerContainer=foodmate-postgres is the default Compose service name;
    # when the PostgreSQL client tools are available only inside Docker.
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$')]
    [string]$DockerContainer = "",
    [switch]$Execute,
    [switch]$RunValidation,
    [switch]$DropRestoreDatabaseAfterValidation
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

function Invoke-DockerPg([string]$Command, [string[]]$Arguments) {
    $output = & docker exec $DockerContainer $Command @Arguments 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) {
        throw "docker exec $Command failed with exit code $LASTEXITCODE"
    }
    return $output.Trim()
}

function Invoke-DatabaseCommand([string]$Command, [string[]]$Arguments) {
    if ($DockerContainer) {
        return Invoke-DockerPg $Command $Arguments
    }
    return Invoke-Pg $Command $Arguments
}

function Copy-FromDocker([string]$ContainerPath, [string]$Destination) {
    $source = $DockerContainer + ":" + $ContainerPath
    & docker cp $source $Destination 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "docker cp from $DockerContainer failed with exit code $LASTEXITCODE"
    }
}

function Copy-ToDocker([string]$Source, [string]$ContainerPath) {
    $destination = $DockerContainer + ":" + $ContainerPath
    & docker cp $Source $destination 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "docker cp to $DockerContainer failed with exit code $LASTEXITCODE"
    }
}

function Remove-DockerFile([string]$ContainerPath) {
    if ($DockerContainer) {
        Invoke-DockerPg "rm" @("-f", "--", $ContainerPath) | Out-Null
    }
}

function Test-DatabaseExists([string]$Name) {
    $query = "SELECT 1 FROM pg_database WHERE datname = '$Name'"
    $result = Invoke-DatabaseCommand "psql" @(
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
if ($DropRestoreDatabaseAfterValidation -and -not $RunValidation) {
    throw "DropRestoreDatabaseAfterValidation requires RunValidation"
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
Write-Output "docker_container=$DockerContainer"
Write-Output "execute=$($Execute.ToString().ToLowerInvariant())"

if (-not $Execute) {
    Write-Output "backup_restore_preflight=passed"
    exit 0
}

if ($DockerContainer) {
    Require-Command "docker"
} else {
    Require-Command "pg_dump"
    Require-Command "psql"
    if ($RestoreDatabaseName) { Require-Command "createdb"; Require-Command "pg_restore" }
}
if (Test-Path -LiteralPath $backupPath) {
    throw "backup file already exists; refusing to overwrite or reuse it"
}
$runId = [Guid]::NewGuid().ToString("N")
$containerBackupPath = ""
$containerValidationPath = ""
$restoreCreated = $false

try {
    if (-not $PSCmdlet.ShouldProcess($DatabaseName, "create PostgreSQL custom-format backup")) { return }
    if ($DockerContainer) {
        $containerBackupPath = "/tmp/foodmate-backup-$runId.dump"
        Invoke-DockerPg "pg_dump" @(
            "--format=custom", "--no-owner", "--no-privileges", "--file", $containerBackupPath,
            "--host", $HostName, "--port", $Port.ToString(), "--username", $Username,
            $DatabaseName
        ) | Out-Null
        Copy-FromDocker $containerBackupPath $backupPath
    } else {
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
        if (-not $PSCmdlet.ShouldProcess($RestoreDatabaseName, "create and restore PostgreSQL backup")) { return }
        if ($DockerContainer) {
            Invoke-DockerPg "createdb" @(
                "--host", $HostName, "--port", $Port.ToString(), "--username", $Username,
                $RestoreDatabaseName
            ) | Out-Null
        } else {
            Invoke-Pg "createdb" @(
                "--host", $HostName, "--port", $Port.ToString(), "--username", $Username,
                $RestoreDatabaseName
            ) | Out-Null
        }
        $restoreCreated = $true
        $restoreArguments = @(
            "--exit-on-error", "--no-owner", "--no-privileges", "--host", $HostName,
            "--port", $Port.ToString(), "--username", $Username, "--dbname", $RestoreDatabaseName
        )
        if ($DockerContainer) {
            $restoreArguments += $containerBackupPath
            Invoke-DockerPg "pg_restore" $restoreArguments | Out-Null
        } else {
            $restoreArguments += $backupPath
            Invoke-Pg "pg_restore" $restoreArguments | Out-Null
        }
        if ($RunValidation) {
            if (-not (Test-Path -LiteralPath $validationFile -PathType Leaf)) { throw "validation.sql was not found" }
            if ($DockerContainer) {
                $containerValidationPath = "/tmp/foodmate-validation-$runId.sql"
                Copy-ToDocker $validationFile $containerValidationPath
                Invoke-DockerPg "psql" @(
                    "--no-psqlrc", "--set", "ON_ERROR_STOP=1", "--host", $HostName,
                    "--port", $Port.ToString(), "--username", $Username, "--dbname", $RestoreDatabaseName,
                    "--file", $containerValidationPath
                ) | Out-Null
            } else {
                Invoke-Pg "psql" @(
                    "--no-psqlrc", "--set", "ON_ERROR_STOP=1", "--host", $HostName,
                    "--port", $Port.ToString(), "--username", $Username, "--dbname", $RestoreDatabaseName,
                    "--file", $validationFile
                ) | Out-Null
            }
            Write-Output "restore_validation=passed"
        }
    }

    Write-Output "backup_restore_status=passed"
} finally {
    if ($DockerContainer -and $containerValidationPath) {
        Remove-DockerFile $containerValidationPath
    }
    if ($DockerContainer -and $containerBackupPath) {
        Remove-DockerFile $containerBackupPath
    }
    if ($DropRestoreDatabaseAfterValidation -and $restoreCreated -and (Test-DatabaseExists $RestoreDatabaseName)) {
        if ($PSCmdlet.ShouldProcess($RestoreDatabaseName, "drop the isolated restored database")) {
            Invoke-DatabaseCommand "dropdb" @(
                "--host", $HostName, "--port", $Port.ToString(), "--username", $Username,
                $RestoreDatabaseName
            ) | Out-Null
            Write-Output "restore_database_cleanup=passed"
        }
    }
}
