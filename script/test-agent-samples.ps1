param(
    [string[]]$AgentCodes,
    [string]$OutputPath = ".apboa\agent-sample-results.json",
    [string]$Username = "admin",
    [string]$Password = "Admin@123.com",
    [int]$TimeoutSeconds = 90
)

$ErrorActionPreference = "Stop"
$consoleUrl = "http://127.0.0.1:3060"
$runtimeUrl = "http://127.0.0.1:3061"

$login = Invoke-RestMethod "$consoleUrl/auth/login" -Method Post -ContentType "application/json" `
    -Body (@{ username = $Username; password = $Password } | ConvertTo-Json)
$token = $login.data.accessToken
$headers = @{ Authorization = "Bearer $token" }
$agents = (Invoke-RestMethod "$consoleUrl/agent/definition/page?current=1&size=250" -Headers $headers).data.records
$results = @()

foreach ($code in $AgentCodes) {
    $agent = $agents | Where-Object { $_.agentCode -eq $code } | Select-Object -First 1
    if ($null -eq $agent) {
        $results += [pscustomobject]@{
            code = $code; name = $null; status = "CONFIG_ERROR"; httpStatus = $null
            durationMs = 0; runFinished = $false; hasOutput = $false
            error = "Agent code is missing or not unique in the requested sample"
        }
        continue
    }

    $sessionId = $null
    $started = Get-Date
    try {
        $session = (Invoke-RestMethod "$consoleUrl/agent/chat/session" -Method Post -Headers $headers `
            -ContentType "application/json" -Body (@{
                agentId = $agent.id; title = "QA stability sample"; initWorkspace = $false
            } | ConvertTo-Json)).data
        $sessionId = $session.id
        $prompt = "Reply with exactly: STABILITY_OK. Do not call tools."
        Invoke-RestMethod "$consoleUrl/agent/chat/session/$sessionId/message" -Method Post -Headers $headers `
            -ContentType "application/json; charset=utf-8" `
            -Body (@{ role = "user"; content = $prompt } | ConvertTo-Json) | Out-Null

        $body = @{
            threadId = $sessionId
            runId = "qa_$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
            messages = @(@{ id = "qa-user"; role = "user"; content = $prompt })
            tools = @()
            context = @()
            state = @{}
            forwardedProps = @{
                agentId = $agent.id; agentCode = $agent.agentCode; fileIds = @()
                memoryActive = $false; planActive = $false; toolProcessActive = $false
                userInfo = $login.data.userDetail
            }
        } | ConvertTo-Json -Depth 8

        $response = Invoke-WebRequest "$runtimeUrl/runtime/agui/run/$($agent.agentCode)" -Method Post `
            -Headers @{ Authorization = "Bearer $token"; Accept = "text/event-stream" } `
            -ContentType "application/json; charset=utf-8" -Body $body -TimeoutSec $TimeoutSeconds
        $content = [string]$response.Content
        $runFinished = $content -match "RUN_FINISHED"
        $runError = $content -match "RUN_ERROR"
        $hasOutput = $content -match "TEXT_MESSAGE_CONTENT"
        $rawError = $content -match '\\"error\\"'
        $errorText = if ($runError -or $rawError) {
            (($content -split "`n") | Where-Object { $_ -match "RUN_ERROR|\\\\?`"error\\\\?`"" } | Select-Object -Last 1)
        } else { $null }
        $results += [pscustomobject]@{
            code = $agent.agentCode; name = $agent.name
            status = if ($response.StatusCode -eq 200 -and $runFinished -and -not $runError -and -not $rawError -and $hasOutput) {
                "PASS"
            } elseif ($response.StatusCode -eq 200 -and $runFinished -and -not $runError -and -not $rawError) {
                "EMPTY_RESULT"
            } else {
                "FAIL"
            }
            httpStatus = [int]$response.StatusCode
            durationMs = [int]((Get-Date) - $started).TotalMilliseconds
            runFinished = $runFinished
            hasOutput = $hasOutput
            error = $errorText
        }
    } catch {
        $statusCode = $null
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
        }
        $results += [pscustomobject]@{
            code = $code; name = if ($agent) { $agent.name } else { $null }
            status = "FAIL"; httpStatus = $statusCode
            durationMs = [int]((Get-Date) - $started).TotalMilliseconds
            runFinished = $false; hasOutput = $false; error = $_.Exception.Message
        }
    } finally {
        if ($sessionId) {
            try {
                Invoke-RestMethod "$consoleUrl/agent/chat/session/$sessionId" -Method Delete -Headers $headers | Out-Null
            } catch {
                # Cleanup failure is reported separately through server-log review.
            }
        }
    }
}

$results | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 $OutputPath
$results
