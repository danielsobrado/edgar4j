param(
    [string]$ProjectName = "",
    [switch]$RemoveVolumes
)

$ErrorActionPreference = "Stop"

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$LockFile = Join-Path $PSScriptRoot "edgar4j.lock"

if ([string]::IsNullOrWhiteSpace($ProjectName)) {
    if (Test-Path -LiteralPath $LockFile) {
        $lock = Get-Content -LiteralPath $LockFile -Raw | ConvertFrom-Json
        $ProjectName = $lock.projectName
    } else {
        $ProjectName = "edgar4j"
    }
}

$composeArgs = @("compose", "-p", $ProjectName, "down")
if ($RemoveVolumes) {
    $composeArgs += "-v"
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

if (Test-Path -LiteralPath $LockFile) {
    Remove-Item -LiteralPath $LockFile -Force
}

Write-Host "EDGAR4J stopped for Docker Compose project '$ProjectName'."
if ($RemoveVolumes) {
    Write-Host "Docker volumes were removed."
}
