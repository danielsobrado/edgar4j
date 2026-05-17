param(
    [string]$BackendBaseUrl = "http://localhost:8080",
    [string]$FrontendBaseUrl = "http://localhost:3000",
    [string]$Ticker = "AAPL",
    [string]$Username = "",
    [string]$Password = "",
    [int]$TimeoutSeconds = 90,
    [switch]$SkipDividendApi
)

$ErrorActionPreference = "Stop"

function New-AuthHeader {
    if ([string]::IsNullOrWhiteSpace($Username) -or [string]::IsNullOrWhiteSpace($Password)) {
        return @{}
    }

    $token = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${Username}:${Password}"))
    return @{ Authorization = "Basic $token" }
}

function Invoke-SmokeRequest {
    param(
        [string]$Name,
        [string]$Url,
        [int[]]$ExpectedStatus = @(200),
        [switch]$UseAuth
    )

    $headers = if ($UseAuth) { New-AuthHeader } else { @{} }
    $started = Get-Date
    $lastError = $null

    while (((Get-Date) - $started).TotalSeconds -lt $TimeoutSeconds) {
        try {
            $response = Invoke-WebRequest -Uri $Url -Headers $headers -UseBasicParsing -TimeoutSec 10
            if ($ExpectedStatus -contains [int]$response.StatusCode) {
                Write-Host "[PASS] $Name -> $($response.StatusCode)"
                return $response
            }
            $lastError = "Unexpected status $($response.StatusCode)"
        } catch {
            $lastError = $_.Exception.Message
        }

        Start-Sleep -Seconds 3
    }

    throw "[FAIL] $Name ($Url): $lastError"
}

$backend = $BackendBaseUrl.TrimEnd("/")
$frontend = $FrontendBaseUrl.TrimEnd("/")

Write-Host "Running EDGAR4J smoke tests"
Write-Host "Backend:  $backend"
Write-Host "Frontend: $frontend"

Invoke-SmokeRequest -Name "Backend liveness" -Url "$backend/actuator/health/liveness" | Out-Null
Invoke-SmokeRequest -Name "Backend readiness" -Url "$backend/actuator/health/readiness" | Out-Null
Invoke-SmokeRequest -Name "Frontend health" -Url "$frontend/health" | Out-Null
Invoke-SmokeRequest -Name "Frontend shell" -Url "$frontend/" | Out-Null

if (-not $SkipDividendApi) {
    Invoke-SmokeRequest -Name "Dividend metric catalog" -Url "$backend/api/dividend/metrics" -UseAuth | Out-Null
    Invoke-SmokeRequest -Name "Dividend sync status endpoint" -Url "$backend/api/dividend/$Ticker/sync" -ExpectedStatus @(200, 404) -UseAuth | Out-Null
}

Write-Host "[PASS] Smoke test completed"
