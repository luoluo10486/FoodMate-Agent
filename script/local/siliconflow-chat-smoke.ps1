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

foreach ($name in @(
        "FOODMATE_MODEL_PROVIDER_SILICONFLOW_BASE_URL",
        "FOODMATE_MODEL_PROVIDER_SILICONFLOW_API_KEY",
        "FOODMATE_MODEL_TIER_STANDARD",
        "FOODMATE_MODEL_TIER_EVAL",
        "FOODMATE_MODEL_PROVIDER_SILICONFLOW_INPUT_CNY_PER_MILLION_TOKENS",
        "FOODMATE_MODEL_PROVIDER_SILICONFLOW_OUTPUT_CNY_PER_MILLION_TOKENS",
        "FOODMATE_MODEL_PROVIDER_SILICONFLOW_PRICE_VERSION"
    )) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "$name 必须通过当前 PowerShell 进程显式注入；脚本不接受命令行密钥参数"
    }
}

$oldRunFlag = [Environment]::GetEnvironmentVariable("FOODMATE_RUN_REAL_CLOUD_TESTS")
$oldProvider = [Environment]::GetEnvironmentVariable("FOODMATE_REAL_CLOUD_PROVIDER")
$oldBytecodeFlag = [Environment]::GetEnvironmentVariable("PYTHONDONTWRITEBYTECODE")

try {
    [Environment]::SetEnvironmentVariable("FOODMATE_RUN_REAL_CLOUD_TESTS", "true")
    [Environment]::SetEnvironmentVariable("FOODMATE_REAL_CLOUD_PROVIDER", "siliconflow")
    [Environment]::SetEnvironmentVariable("PYTHONDONTWRITEBYTECODE", "1")
    Push-Location (Join-Path $repoRoot "agent-runtime")
    try {
        & $PythonPath -m pytest -q tests/test_real_cloud_integration.py -p no:cacheprovider
        if ($LASTEXITCODE -ne 0) {
            throw "SiliconFlow Chat smoke failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}
finally {
    [Environment]::SetEnvironmentVariable("FOODMATE_RUN_REAL_CLOUD_TESTS", $oldRunFlag)
    [Environment]::SetEnvironmentVariable("FOODMATE_REAL_CLOUD_PROVIDER", $oldProvider)
    [Environment]::SetEnvironmentVariable("PYTHONDONTWRITEBYTECODE", $oldBytecodeFlag)
}
