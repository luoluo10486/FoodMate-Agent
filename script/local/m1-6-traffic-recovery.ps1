param(
    [int]$WarmupSeconds = 30,
    [int]$SteadySeconds = 120,
    [int]$Workers = 16,
    [string]$JavaBaseUrl = "http://127.0.0.1:8080",
    [string]$RuntimeBaseUrl = "http://127.0.0.1:9002",
    [switch]$EnableFaultInjection
)

$ErrorActionPreference = "Stop"

function Require-Positive([int]$value, [string]$name) {
    if ($value -lt 1) { throw "$name must be positive" }
}

function Get-Status([string]$name, [string]$url) {
    $stopwatch = [Diagnostics.Stopwatch]::StartNew()
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 5
        [pscustomobject]@{ component = $name; ready = $response.StatusCode -eq 200; latency_ms = [math]::Round($stopwatch.Elapsed.TotalMilliseconds, 3); detail = $response.Content }
    } catch {
        [pscustomobject]@{ component = $name; ready = $false; latency_ms = [math]::Round($stopwatch.Elapsed.TotalMilliseconds, 3); detail = $_.Exception.Message }
    }
}

function Wait-Ready([string]$name, [string]$url) {
    $deadline = (Get-Date).AddSeconds(90)
    do {
        $status = Get-Status $name $url
        if ($status.ready) { return $status }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "$name did not recover within 90 seconds: $($status.detail)"
}

Require-Positive $WarmupSeconds "WarmupSeconds"
Require-Positive $SteadySeconds "SteadySeconds"
Require-Positive $Workers "Workers"

$javaReady = "$JavaBaseUrl/actuator/health/readiness"
$javaMetrics = "$JavaBaseUrl/actuator/metrics"
$runtimeReady = "$RuntimeBaseUrl/foodmate/internal/health/ready"
$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) { throw "Docker CLI is required for PostgreSQL/Redis/RocketMQ checks" }

$before = @(
    Get-Status "java_readiness" $javaReady
    Get-Status "java_metrics" $javaMetrics
    Get-Status "python_readiness" $runtimeReady
)
$previousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$containers = docker ps --format '{{.Names}}' 2>$null
$dockerExitCode = $LASTEXITCODE
$ErrorActionPreference = $previousErrorActionPreference
if ($dockerExitCode -ne 0) { throw "Docker Desktop is unavailable; no traffic or fault injection was started" }
$required = @("foodmate-postgres", "foodmate-redis", "foodmate-rocketmq-namesrv", "foodmate-rocketmq-broker", "foodmate-rocketmq-proxy")
$missing = $required | Where-Object { $_ -notin @($containers) }
if ($missing) { throw "Required local dependencies are unavailable: $($missing -join ', ')" }
if (@($before | Where-Object { -not $_.ready }).Count -gt 0) { throw "Readiness prerequisite failed; no traffic or fault injection was started" }

$report = [ordered]@{
    run_id = "m16-" + [guid]::NewGuid().ToString("N")
    started_at = (Get-Date).ToUniversalTime().ToString("o")
    traffic = [ordered]@{ warmup_seconds = $WarmupSeconds; steady_seconds = $SteadySeconds; workers = $Workers; mode = "deterministic-local-runtime" }
    readiness_before = $before
    fault_injection = "not_requested"
    limitations = @("This entrypoint only performs safe preflight by default.", "Use existing HTTP/MQ M1-5 harnesses for real Agent/Proposal traffic; record only measured output.")
}

if ($EnableFaultInjection) {
    $report.fault_injection = "redis_restart"
    docker restart foodmate-redis | Out-Null
    $recovered = Wait-Ready "python_readiness_after_redis_restart" $runtimeReady
    $report.readiness_after_fault = $recovered
}

$report.finished_at = (Get-Date).ToUniversalTime().ToString("o")
$report | ConvertTo-Json -Depth 8
