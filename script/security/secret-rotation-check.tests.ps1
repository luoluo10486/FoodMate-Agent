$ErrorActionPreference = "Stop"

$target = Join-Path $PSScriptRoot "secret-rotation-check.ps1"
$names = @(
    "RUNTIME_SERVICE_JWT_ENABLED",
    "RUNTIME_JAVA_PUBLIC_KEYS",
    "RUNTIME_PYTHON_PUBLIC_KEYS",
    "RUNTIME_JAVA_KID",
    "RUNTIME_PYTHON_KID",
    "FOODMATE_RAG_MODE",
    "FOODMATE_RAG_EMBEDDING_PROVIDER",
    "FOODMATE_RAG_EMBEDDING_BASE_URL",
    "FOODMATE_RAG_EMBEDDING_API_KEY",
    "FOODMATE_RAG_EMBEDDING_MODEL",
    "FOODMATE_DOCKER_RAG_MODE",
    "FOODMATE_DOCKER_RAG_EMBEDDING_PROVIDER",
    "FOODMATE_DOCKER_RAG_EMBEDDING_BASE_URL",
    "FOODMATE_DOCKER_RAG_EMBEDDING_API_KEY",
    "FOODMATE_DOCKER_RAG_EMBEDDING_MODEL",
    "FOODMATE_DOCKER_RAG_MILVUS_URI",
    "FOODMATE_DOCKER_RAG_MILVUS_COLLECTION",
    "FOODMATE_DOCKER_RAG_BATCH_TOKEN_LIMIT",
    "FOODMATE_DOCKER_RAG_DAILY_TOKEN_LIMIT",
    "FOODMATE_DOCKER_RAG_BATCH_COST_LIMIT",
    "FOODMATE_DOCKER_RAG_DAILY_COST_LIMIT",
    "FOODMATE_DOCKER_RAG_PRICE_PER_MILLION_TOKENS",
    "FOODMATE_DOCKER_RAG_PRICE_VERSION",
    "FOODMATE_MODEL_PROVIDER_SILICONFLOW_API_KEY",
    "FOODMATE_DOCKER_MODEL_PROVIDER_SILICONFLOW_API_KEY"
)

function Invoke-Preflight([hashtable]$Values) {
    $old = @{}
    try {
        foreach ($name in $names) {
            $old[$name] = [Environment]::GetEnvironmentVariable($name)
            [Environment]::SetEnvironmentVariable($name, $null)
        }
        foreach ($entry in $Values.GetEnumerator()) {
            [Environment]::SetEnvironmentVariable($entry.Key, [string]$entry.Value)
        }
        try {
            $output = (& $target 2>&1 | Out-String)
            $exitCode = $LASTEXITCODE
        }
        catch {
            $output = $_ | Out-String
            $exitCode = 1
        }
        [pscustomobject]@{ ExitCode = $exitCode; Output = $output }
    }
    finally {
        foreach ($name in $names) {
            [Environment]::SetEnvironmentVariable($name, $old[$name])
        }
    }
}

function Assert-Failure([hashtable]$Values, [string]$ExpectedText) {
    $result = Invoke-Preflight $Values
    if ($result.ExitCode -eq 0 -or $result.Output -notmatch [regex]::Escape($ExpectedText)) {
        throw "Expected preflight failure '$ExpectedText', got exit=$($result.ExitCode)"
    }
}

function Assert-Success([hashtable]$Values) {
    $result = Invoke-Preflight $Values
    if ($result.ExitCode -ne 0 -or $result.Output -notmatch "secret_rotation_preflight=passed") {
        throw "Expected preflight success, got exit=$($result.ExitCode): $($result.Output)"
    }
}

$common = @{
    RUNTIME_SERVICE_JWT_ENABLED = "false"
    FOODMATE_RAG_MODE = "local"
    FOODMATE_RAG_EMBEDDING_PROVIDER = "openai-compatible"
    FOODMATE_RAG_EMBEDDING_BASE_URL = "https://embedding.example/v1"
    FOODMATE_RAG_EMBEDDING_MODEL = "BAAI/bge-m3"
    FOODMATE_RAG_MILVUS_URI = "http://milvus.example:19530"
    FOODMATE_RAG_MILVUS_COLLECTION = "test_collection"
    FOODMATE_RAG_BATCH_TOKEN_LIMIT = "1000"
    FOODMATE_RAG_DAILY_TOKEN_LIMIT = "10000"
    FOODMATE_RAG_BATCH_COST_LIMIT = "1"
    FOODMATE_RAG_DAILY_COST_LIMIT = "10"
    FOODMATE_RAG_PRICE_PER_MILLION_TOKENS = "1"
    FOODMATE_RAG_PRICE_VERSION = "test-v1"
}

$missing = @{}
$common.GetEnumerator() | ForEach-Object { $missing[$_.Key] = $_.Value }
$missing["FOODMATE_RAG_EMBEDDING_API_KEY"] = ""
Assert-Failure $missing "RAG embedding API key is missing"

$reused = @{}
$common.GetEnumerator() | ForEach-Object { $reused[$_.Key] = $_.Value }
$reused["FOODMATE_RAG_EMBEDDING_API_KEY"] = ""
$reused["FOODMATE_MODEL_PROVIDER_SILICONFLOW_API_KEY"] = "chat-only-test-key"
Assert-Failure $reused "RAG embedding API key is missing"

$dockerCommon = @{
    RUNTIME_SERVICE_JWT_ENABLED = "false"
    FOODMATE_DOCKER_RAG_MODE = "local"
    FOODMATE_DOCKER_RAG_EMBEDDING_PROVIDER = "openai-compatible"
    FOODMATE_DOCKER_RAG_EMBEDDING_BASE_URL = "https://embedding.example/v1"
    FOODMATE_DOCKER_RAG_EMBEDDING_MODEL = "BAAI/bge-m3"
    FOODMATE_DOCKER_RAG_MILVUS_URI = "http://milvus.example:19530"
    FOODMATE_DOCKER_RAG_MILVUS_COLLECTION = "test_collection"
    FOODMATE_DOCKER_RAG_BATCH_TOKEN_LIMIT = "1000"
    FOODMATE_DOCKER_RAG_DAILY_TOKEN_LIMIT = "10000"
    FOODMATE_DOCKER_RAG_BATCH_COST_LIMIT = "1"
    FOODMATE_DOCKER_RAG_DAILY_COST_LIMIT = "10"
    FOODMATE_DOCKER_RAG_PRICE_PER_MILLION_TOKENS = "1"
    FOODMATE_DOCKER_RAG_PRICE_VERSION = "test-v1"
    FOODMATE_DOCKER_RAG_EMBEDDING_API_KEY = "embedding-test-key"
}
$dockerMissing = @{}
$dockerCommon.GetEnumerator() | ForEach-Object { $dockerMissing[$_.Key] = $_.Value }
$dockerMissing["FOODMATE_DOCKER_RAG_EMBEDDING_API_KEY"] = ""
Assert-Failure $dockerMissing "Docker RAG embedding API key is missing"

$dockerReused = @{}
$dockerCommon.GetEnumerator() | ForEach-Object { $dockerReused[$_.Key] = $_.Value }
$dockerReused["FOODMATE_DOCKER_RAG_EMBEDDING_API_KEY"] = ""
$dockerReused["FOODMATE_DOCKER_MODEL_PROVIDER_SILICONFLOW_API_KEY"] = "chat-only-test-key"
Assert-Failure $dockerReused "Docker RAG embedding API key is missing"
Assert-Success $dockerCommon

Write-Output "secret_rotation_check_tests=passed"
