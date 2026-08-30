$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$scriptPath = Join-Path $repoRoot "script/local/siliconflow-docker-embedding-smoke.ps1"
$scriptText = Get-Content -Raw -LiteralPath $scriptPath

foreach ($required in @(
        'Get-AgentRuntimeRagConfig',
        'ConvertFrom-Json',
        'FOODMATE_RAG_EMBEDDING_PROFILE',
        'FOODMATE_RAG_EMBEDDING_MODEL',
        'Assert-ProfileConfiguration',
        'EmbeddingProfile'
    )) {
    if ($scriptText -notmatch [regex]::Escape($required)) {
        throw "Docker Embedding smoke is missing safe profile verification: $required"
    }
}
if ($scriptText -match '\$Profile\b') {
    throw "Docker Embedding smoke must not use the automatic PowerShell PROFILE variable as state"
}
if ($scriptText -match '(?i)FOODMATE_.*API_KEY\s*=\s*[^#]+') {
    throw "Docker Embedding smoke must not contain a credential value"
}
if ($scriptText -notmatch 'expected_model') {
    throw "Docker Embedding smoke must compare the configured model with the requested profile"
}

Write-Output "siliconflow_docker_embedding_smoke_contract=passed"
