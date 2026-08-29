[CmdletBinding()]
param(
    [string]$PythonPath = ""
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path

if ([string]::IsNullOrWhiteSpace($PythonPath)) {
    $PythonPath = Join-Path $repoRoot "agent-runtime/.venv/Scripts/python.exe"
}

if (-not (Test-Path -LiteralPath $PythonPath -PathType Leaf)) {
    throw "项目 Python 解释器不存在：$PythonPath"
}

foreach ($name in @("FOODMATE_RAG_EMBEDDING_BASE_URL", "FOODMATE_RAG_EMBEDDING_API_KEY")) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "$name 必须通过当前 PowerShell 进程显式注入；脚本不接受命令行密钥参数"
    }
}

$oldRunFlag = [Environment]::GetEnvironmentVariable("FOODMATE_RUN_REAL_EMBEDDING_TESTS")
$oldBytecodeFlag = [Environment]::GetEnvironmentVariable("PYTHONDONTWRITEBYTECODE")

try {
    [Environment]::SetEnvironmentVariable("FOODMATE_RUN_REAL_EMBEDDING_TESTS", "true")
    [Environment]::SetEnvironmentVariable("PYTHONDONTWRITEBYTECODE", "1")
    Push-Location (Join-Path $repoRoot "agent-runtime")
    try {
        & $PythonPath -m pytest -q -s tests/test_real_embedding_integration.py -p no:cacheprovider
        if ($LASTEXITCODE -ne 0) {
            throw "SiliconFlow Embedding smoke failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}
finally {
    [Environment]::SetEnvironmentVariable("FOODMATE_RUN_REAL_EMBEDDING_TESTS", $oldRunFlag)
    [Environment]::SetEnvironmentVariable("PYTHONDONTWRITEBYTECODE", $oldBytecodeFlag)
}
