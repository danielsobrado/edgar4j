param(
    [ValidateSet("high", "low")]
    [string]$Profile = "high",
    [int]$BackendPort = 8080,
    [int]$FrontendPort = 3000,
    [string]$ProjectName = "edgar4j",
    [switch]$NoBuild,
    [switch]$Force
)

$ErrorActionPreference = "Stop"

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$LockFile = Join-Path $PSScriptRoot "edgar4j.lock"

function Get-RunningComposeServices {
    param([string]$Name)

    $services = & docker compose -p $Name ps --status running --services 2>$null
    if ($LASTEXITCODE -ne 0 -or $null -eq $services) {
        return @()
    }

    return @($services | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

if (Test-Path -LiteralPath $LockFile) {
    $lock = Get-Content -LiteralPath $LockFile -Raw | ConvertFrom-Json
    $runningServices = Get-RunningComposeServices -Name $lock.projectName

    if ($runningServices.Count -gt 0 -and -not $Force) {
        throw "EDGAR4J is already running for Compose project '$($lock.projectName)'. Run scripts\stop.ps1 first, or pass -Force to refresh the lock."
    }

    Remove-Item -LiteralPath $LockFile -Force
}

$env:BACKEND_PORT = [string]$BackendPort
$env:FRONTEND_PORT = [string]$FrontendPort

$composeArgs = @("compose", "-p", $ProjectName, "--profile", $Profile, "up", "-d")
if (-not $NoBuild) {
    $composeArgs += "--build"
}

Push-Location $RepoRoot
try {
    & docker @composeArgs
    if ($LASTEXITCODE -ne 0) {
        throw "docker $($composeArgs -join ' ') failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

$lockData = [ordered]@{
    projectName = $ProjectName
    profile = $Profile
    backendPort = $BackendPort
    frontendPort = $FrontendPort
    startedAt = (Get-Date).ToString("o")
}

$lockData | ConvertTo-Json | Set-Content -LiteralPath $LockFile -Encoding UTF8

Write-Host "EDGAR4J started with Docker Compose profile '$Profile'."
Write-Host "Frontend: http://localhost:$FrontendPort"
Write-Host "Backend:  http://localhost:$BackendPort"
Write-Host "Lock:     $LockFile"
