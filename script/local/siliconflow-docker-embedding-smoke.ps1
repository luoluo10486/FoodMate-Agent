[CmdletBinding()]
param(
    [ValidateSet("bge-m3", "qwen3-embedding-0.6b")]
    [string]$Profile = "bge-m3",
    [switch]$ExecuteRequest
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$composeFile = Join-Path $repoRoot "docker/compose.yml"
$envFile = Join-Path $repoRoot ".env"
$composeArgs = @("--env-file", $envFile, "-f", $composeFile)
# Compose reads this secret from the root .env and maps it to the container-only
# FOODMATE_RAG_EMBEDDING_API_KEY variable. The script never reads the value.
# The explicit execution contract is: docker compose exec -T agent-runtime python -c.
# The source variable is FOODMATE_DOCKER_RAG_EMBEDDING_API_KEY.

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker CLI 不存在，无法执行 Docker Runtime smoke"
}
if (-not (Test-Path -LiteralPath $composeFile -PathType Leaf)) {
    throw "Docker Compose 文件不存在：$composeFile"
}
if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
    throw "项目根目录 .env 不存在；请先准备本地 Docker 配置"
}

& docker compose @composeArgs config --quiet
if ($LASTEXITCODE -ne 0) {
    throw "Docker Compose 配置校验失败"
}

$agentPort = [Environment]::GetEnvironmentVariable("FOODMATE_AGENT_PORT")
if ([string]::IsNullOrWhiteSpace($agentPort)) {
    $agentPort = "9002"
}

function Test-AgentRuntimeReady {
    $uri = "http://localhost:$agentPort/foodmate/internal/health/ready"
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $uri -TimeoutSec 10
        if ($response.StatusCode -ne 200) {
            throw "HTTP $($response.StatusCode)"
        }
    }
    catch {
        throw "agent-runtime readiness 检查失败：$uri；$($_.Exception.Message)"
    }
}

Test-AgentRuntimeReady
Write-Output "docker_embedding_smoke_preflight=passed"
Write-Output "embedding_profile=$Profile"
Write-Output "agent_runtime_port=$agentPort"

if (-not $ExecuteRequest) {
    Write-Output "embedding_request=skipped"
    Write-Output "提示：只有显式传入 -ExecuteRequest 才会调用真实 Embedding 服务"
    exit 0
}

$pythonCode = @'
import json
import os
import sys
import time
import urllib.error
import urllib.request

profiles = {
    "bge-m3": "BAAI/bge-m3",
    "qwen3-embedding-0.6b": "Qwen/Qwen3-Embedding-0.6B",
}
profile = sys.argv[1]
expected_model = profiles[profile]
mode = os.environ.get("FOODMATE_RAG_MODE", "")
provider = os.environ.get("FOODMATE_RAG_EMBEDDING_PROVIDER", "")
base_url = os.environ.get("FOODMATE_RAG_EMBEDDING_BASE_URL", "").strip()
configured_model = os.environ.get("FOODMATE_RAG_EMBEDDING_MODEL", "").strip()
api_key = os.environ.get("FOODMATE_RAG_EMBEDDING_API_KEY", "").strip()

if mode != "local":
    raise SystemExit("FOODMATE_RAG_MODE must be local")
if provider != "openai-compatible":
    raise SystemExit("FOODMATE_RAG_EMBEDDING_PROVIDER must be openai-compatible")
if not base_url or not api_key:
    raise SystemExit("Embedding endpoint or API key is not configured in agent-runtime")
if configured_model != expected_model:
    raise SystemExit("Embedding model does not match the requested profile")

payload = json.dumps({
    "model": configured_model,
    "input": "FoodMate embedding smoke",
    "encoding_format": "float",
}).encode("utf-8")
request = urllib.request.Request(
    base_url.rstrip("/") + "/embeddings",
    data=payload,
    headers={
        "Authorization": "Bearer " + api_key,
        "Content-Type": "application/json",
    },
    method="POST",
)
timeout = float(os.environ.get("FOODMATE_RAG_ITEM_TIMEOUT_SECONDS", "20"))
started = time.perf_counter()
try:
    with urllib.request.urlopen(request, timeout=timeout) as response:
        body = json.loads(response.read().decode("utf-8"))
except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, ValueError) as exc:
    raise SystemExit("Embedding request failed: " + type(exc).__name__)

data = body.get("data")
if not isinstance(data, list) or not data or not isinstance(data[0], dict):
    raise SystemExit("Embedding response data is invalid")
vector = data[0].get("embedding")
if not isinstance(vector, list) or not vector:
    raise SystemExit("Embedding response vector is invalid")
if not all(isinstance(value, (int, float)) for value in vector):
    raise SystemExit("Embedding response vector contains invalid values")

usage = body.get("usage")
prompt_tokens = usage.get("prompt_tokens") if isinstance(usage, dict) else None
latency_ms = round((time.perf_counter() - started) * 1000, 2)
print("embedding_profile=" + profile)
print("embedding_model=" + configured_model)
print("embedding_dimension=" + str(len(vector)))
print("prompt_tokens=" + (str(prompt_tokens) if prompt_tokens is not None else "unknown"))
print("latency_ms=" + str(latency_ms))
print("embedding_smoke_status=passed")
'@

$encodedPythonCode = [Convert]::ToBase64String(
    [Text.Encoding]::UTF8.GetBytes($pythonCode)
)
$pythonBootstrap = "import base64,sys;exec(base64.b64decode(sys.argv[2]))"

# Pass only base64 through the Windows Docker CLI so Python source quotes are
# not re-parsed or stripped before they reach the container.
& docker compose @composeArgs exec -T agent-runtime python -c $pythonBootstrap $Profile $encodedPythonCode
if ($LASTEXITCODE -ne 0) {
    throw "Docker SiliconFlow Embedding smoke failed"
}
