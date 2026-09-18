$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$scriptPath = Join-Path $repoRoot "script/local/deterministic-agent-e2e.ps1"
$scriptText = Get-Content -Raw -LiteralPath $scriptPath

if ($scriptText -notmatch 'deterministic:local') { throw "deterministic Agent E2E must pin all Chat tiers to the local provider" }
if ($scriptText -notmatch 'FOODMATE_DOCKER_PAID_EXECUTION_ENABLED') { throw "deterministic Agent E2E must explicitly disable paid execution" }
if ($scriptText -notmatch 'api/auth/register') { throw "deterministic Agent E2E must create an isolated account" }
if ($scriptText -notmatch 'api/chat/runs') { throw "deterministic Agent E2E must use the real ChatRun creation API" }
if ($scriptText -notmatch 'api/agent-runs/.*/stream') { throw "deterministic Agent E2E must use the persisted AgentRun SSE API" }
if ($scriptText -notmatch 'Last-Event-ID') { throw "deterministic Agent E2E must verify SSE cursor replay" }
if ($scriptText -notmatch 'run.completed') { throw "deterministic Agent E2E must assert the successful terminal event" }
if ($scriptText -notmatch 'api/sessions/.+DELETE|api/sessions/\$\(') { throw "deterministic Agent E2E must retain session cleanup" }
if ($scriptText -notmatch 'Wait-RocketMqInit') { throw "deterministic Agent E2E must wait for RocketMQ initialization" }
if ($scriptText -notmatch 'Ensure-AgentRuntimeStarted') { throw "deterministic Agent E2E must verify Agent Runtime startup" }
if ($scriptText -notmatch 'docker start foodmate-agent-runtime') { throw "deterministic Agent E2E must start a created Agent Runtime container" }
if ($scriptText -match '(?i)ExecutePaid|API_KEY|password\s*=\s*Read-Host|Start-Job|ForEach-Object.*parallel|WarmupSeconds|SteadySeconds|reboot|acknowledge[-_ ]?loss') {
    throw "deterministic Agent E2E must stay local, bounded and free of credential/pressure-test behavior"
}

$topicScriptPath = Join-Path $repoRoot "docker/rocketmq/init-topics.sh"
$topicScriptText = Get-Content -Raw -LiteralPath $topicScriptPath
if ($topicScriptText -notmatch 'getConsumerConfig') { throw "RocketMQ initialization must check existing consumer groups before updating them" }
if ($topicScriptText -notmatch 'Consumer group') { throw "RocketMQ initialization must document the idempotent group path" }

Write-Output "deterministic_agent_e2e_contract=passed"
