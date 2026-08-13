param(
    [int]$Workers = 8,
    [int]$RequestsPerWorker = 20,
    [int]$PortA = 18080,
    [int]$PortB = 18081,
    [string]$JarPath = "foodmate-bootstrap/target/foodmate-bootstrap-0.1.0-SNAPSHOT.jar"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http
$startedPids = New-Object System.Collections.Generic.List[int]
$tempDir = Join-Path ([System.IO.Path]::GetTempPath()) ("foodmate-m1-6-" + [guid]::NewGuid().ToString("N"))
$username = "m16_script_" + [guid]::NewGuid().ToString("N")

function New-HttpContext {
    $handler = New-Object System.Net.Http.HttpClientHandler
    $handler.CookieContainer = New-Object System.Net.CookieContainer
    $client = New-Object System.Net.Http.HttpClient($handler)
    $client.Timeout = [TimeSpan]::FromSeconds(15)
    return [pscustomobject]@{ Handler = $handler; Client = $client }
}

function Post-Json($context, [string]$url, [hashtable]$payload, [string]$csrf = $null) {
    $body = $payload | ConvertTo-Json -Compress
    $content = New-Object System.Net.Http.StringContent($body, [Text.Encoding]::UTF8, "application/json")
    $request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Post, $url)
    $request.Content = $content
    if ($csrf) { $request.Headers.Add("X-CSRF-Token", $csrf) }
    $response = $context.Client.SendAsync($request).Result
    $text = $response.Content.ReadAsStringAsync().Result
    if (-not $response.IsSuccessStatusCode) {
        throw ("POST {0} returned {1}: {2}" -f $url, $response.StatusCode, $text)
    }
    return $text | ConvertFrom-Json
}

function Get-Json($context, [string]$url) {
    $response = $context.Client.GetAsync($url).Result
    $text = $response.Content.ReadAsStringAsync().Result
    if (-not $response.IsSuccessStatusCode) {
        throw ("GET {0} returned {1}: {2}" -f $url, $response.StatusCode, $text)
    }
    return $text | ConvertFrom-Json
}

function Get-Csrf($context, [int]$port) {
    $cookies = $context.Handler.CookieContainer.GetCookies([Uri]("http://127.0.0.1:{0}/" -f $port))
    $cookie = $cookies | Where-Object Name -eq "foodmate_csrf" | Select-Object -First 1
    if (-not $cookie) { throw "foodmate_csrf cookie is missing" }
    return $cookie.Value
}

function Wait-Liveness([int]$port) {
    $deadline = (Get-Date).AddSeconds(90)
    do {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri ("http://127.0.0.1:{0}/actuator/health/liveness" -f $port) -TimeoutSec 2
            if ($response.StatusCode -eq 200) { return }
        } catch { }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)
    throw "Java JVM on port $port did not become live"
}

function Start-Java([int]$port) {
    $occupied = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($occupied) {
        throw "Port $port is already listening; refusing to reuse or stop an existing process"
    }
    $resolvedJar = (Resolve-Path -LiteralPath $JarPath).Path
    $stdout = Join-Path $tempDir ("java-{0}.out.log" -f $port)
    $stderr = Join-Path $tempDir ("java-{0}.err.log" -f $port)
    $process = Start-Process -FilePath "java.exe" `
        -ArgumentList @(
            "-jar", $resolvedJar,
            "--spring.profiles.active=local",
            "--server.port=$port",
            "--foodmate.runtime.transport=http",
            "--foodmate.runtime.service-jwt.enabled=false",
            "--foodmate.runtime.admission.enabled=false"
        ) `
        -WorkingDirectory (Get-Location).Path `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -PassThru
    $startedPids.Add($process.Id)
    Wait-Liveness $port
    return $process
}

function Percentile([object[]]$values, [double]$percent) {
    if ($values.Count -eq 0) { return 0 }
    $rank = ($percent / 100) * ($values.Count - 1)
    $lower = [math]::Floor($rank)
    $upper = [math]::Ceiling($rank)
    if ($lower -eq $upper) { return [double]$values[$lower] }
    return [double]$values[$lower] + ($rank - $lower) * ([double]$values[$upper] - [double]$values[$lower])
}

function Remove-TestUser {
    $sql = @"
BEGIN;
UPDATE user_auth_sessions SET revoked_at=COALESCE(revoked_at,CURRENT_TIMESTAMP),updated_at=CURRENT_TIMESTAMP
WHERE user_id=(SELECT user_id FROM users WHERE username='$username');
UPDATE messages SET is_deleted=TRUE,deleted_at=COALESCE(deleted_at,CURRENT_TIMESTAMP),deleted_by=created_by,updated_at=CURRENT_TIMESTAMP
WHERE session_id IN (SELECT session_id FROM sessions WHERE user_id=(SELECT user_id FROM users WHERE username='$username'));
UPDATE sessions SET is_deleted=TRUE,deleted_at=COALESCE(deleted_at,CURRENT_TIMESTAMP),deleted_by=user_id,updated_at=CURRENT_TIMESTAMP
WHERE user_id=(SELECT user_id FROM users WHERE username='$username');
UPDATE user_profiles SET is_deleted=TRUE,deleted_at=COALESCE(deleted_at,CURRENT_TIMESTAMP),deleted_by=user_id,updated_at=CURRENT_TIMESTAMP
WHERE user_id=(SELECT user_id FROM users WHERE username='$username');
UPDATE users SET is_deleted=TRUE,deleted_at=COALESCE(deleted_at,CURRENT_TIMESTAMP),deleted_by=user_id,status='disabled',updated_at=CURRENT_TIMESTAMP
WHERE username='$username';
COMMIT;
"@
    try {
        docker exec foodmate-postgres psql -U postgres -d FoodMate -v ON_ERROR_STOP=1 -c $sql | Out-Null
    } catch {
        Write-Warning "Test user cleanup failed for ${username}: $($_.Exception.Message)"
    }
}

try {
    if ($Workers -lt 1 -or $RequestsPerWorker -lt 1) { throw "Workers and RequestsPerWorker must be positive" }
    New-Item -ItemType Directory -Force -Path $tempDir | Out-Null

    $instanceA = Start-Java $PortA
    $instanceB = Start-Java $PortB

    $context = New-HttpContext
    try {
        [void](Post-Json $context ("http://127.0.0.1:{0}/api/auth/register" -f $PortA) @{
                username = $username
                email = "$username@example.com"
                password = "password123"
            })
        [void](Post-Json $context ("http://127.0.0.1:{0}/api/auth/login" -f $PortA) @{
                username_or_email = $username
                password = "password123"
            })
        $csrf = Get-Csrf $context $PortA
        $session = Post-Json $context ("http://127.0.0.1:{0}/api/sessions" -f $PortA) @{
            title = "M1-6 dual JVM"
            mode = "chat"
        } $csrf
        $sessionId = $session.data.session_id
        $sessionsFromB = Get-Json $context ("http://127.0.0.1:{0}/api/sessions?page=1&size=50" -f $PortB)
        $sharedRead = @($sessionsFromB.data.items | Where-Object session_id -eq $sessionId).Count -eq 1
    } finally {
        $context.Client.Dispose()
    }

    $jobs = New-Object System.Collections.Generic.List[object]
    $started = [System.Diagnostics.Stopwatch]::StartNew()
    for ($worker = 0; $worker -lt $Workers; $worker++) {
        $jobs.Add((Start-Job -ScriptBlock {
                    param($workerId, $count, $user, $portA, $portB)
                    Add-Type -AssemblyName System.Net.Http
                    $handler = New-Object System.Net.Http.HttpClientHandler
                    $handler.CookieContainer = New-Object System.Net.CookieContainer
                    $client = New-Object System.Net.Http.HttpClient($handler)
                    $client.Timeout = [TimeSpan]::FromSeconds(15)
                    $samples = New-Object System.Collections.Generic.List[double]
                    $success = 0
                    $errors = 0
                    try {
                        $loginBody = @{ username_or_email = $user; password = "password123" } | ConvertTo-Json -Compress
                        $loginContent = New-Object System.Net.Http.StringContent($loginBody, [Text.Encoding]::UTF8, "application/json")
                        $login = $client.PostAsync(("http://127.0.0.1:{0}/api/auth/login" -f $portA), $loginContent).Result
                        if (-not $login.IsSuccessStatusCode) { throw "worker login failed: $($login.StatusCode)" }
                        for ($i = 0; $i -lt $count; $i++) {
                            $port = if ((($workerId + $i) % 2) -eq 0) { $portA } else { $portB }
                            $watch = [System.Diagnostics.Stopwatch]::StartNew()
                            try {
                                $response = $client.GetAsync(("http://127.0.0.1:{0}/api/sessions?page=1&size=50" -f $port)).Result
                                if ($response.IsSuccessStatusCode) { $success++ } else { $errors++ }
                            } catch { $errors++ }
                            $watch.Stop()
                            $samples.Add($watch.Elapsed.TotalMilliseconds)
                        }
                        [pscustomobject]@{ success = $success; errors = $errors; samples = @($samples) }
                    } catch {
                        [pscustomobject]@{ success = 0; errors = $count; samples = @(); worker_error = $_.Exception.Message }
                    } finally {
                        $client.Dispose()
                    }
                } -ArgumentList $worker, $RequestsPerWorker, $username, $PortA, $PortB))
    }
    Wait-Job -Job $jobs | Out-Null
    $results = Receive-Job -Job $jobs
    Remove-Job -Job $jobs -Force
    $started.Stop()

    $samples = @($results | ForEach-Object { $_.samples }) | Sort-Object
    $success = ($results | Measure-Object -Property success -Sum).Sum
    $errors = ($results | Measure-Object -Property errors -Sum).Sum
    $total = $Workers * $RequestsPerWorker

    Stop-Process -Id $instanceA.Id -Force
    $instanceA = Start-Java $PortA
    $afterRestart = New-HttpContext
    try {
        [void](Post-Json $afterRestart ("http://127.0.0.1:{0}/api/auth/login" -f $PortA) @{
                username_or_email = $username
                password = "password123"
            })
        $restartedSessions = Get-Json $afterRestart ("http://127.0.0.1:{0}/api/sessions?page=1&size=50" -f $PortA)
        $recoveredRead = @($restartedSessions.data.items | Where-Object session_id -eq $sessionId).Count -eq 1
    } finally {
        $afterRestart.Client.Dispose()
    }

    [pscustomobject]@{
        instances = "$PortA,$PortB"
        shared_postgres_read = $sharedRead
        total_requests = $total
        successful_requests = $success
        errors = $errors
        error_rate_percent = [math]::Round(($errors / $total) * 100, 3)
        throughput_requests_per_second = [math]::Round(($success / [math]::Max($started.Elapsed.TotalSeconds, 0.001)), 3)
        p50_ms = [math]::Round((Percentile $samples 50), 3)
        p95_ms = [math]::Round((Percentile $samples 95), 3)
        p99_ms = [math]::Round((Percentile $samples 99), 3)
        java_restart_liveness = $true
        postgres_read_after_restart = $recoveredRead
        scope = "local bounded authenticated session reads; not a production capacity result"
    } | ConvertTo-Json -Compress
} finally {
    Remove-TestUser
    foreach ($processId in $startedPids) {
        Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
    }
    Remove-Item -LiteralPath $tempDir -Recurse -Force -ErrorAction SilentlyContinue
}
