param(
    [string]$MongoUri = "mongodb://localhost:27018/edgar_staging_smoke_20260314c",
    [int]$Port = 8081,
    [string]$StdoutPath = "staging-logs/backfill-backend.out.log",
    [string]$StderrPath = "staging-logs/backfill-backend.err.log"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$javaHome = "C:\Program Files\OpenJDK\jdk-25"

$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"
$env:SERVER_PORT = "$Port"
$env:SPRING_MONGODB_URI = $MongoUri
$env:MONGO_URL = $MongoUri
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/edgar4j"
$env:SPRING_DATASOURCE_USERNAME = "edgar4j"
$env:SPRING_DATASOURCE_PASSWORD = "edgar4j"
$env:SPRING_DATA_REDIS_HOST = "localhost"
$env:SPRING_DATA_REDIS_PORT = "6380"
$env:REDIS_HOST = "localhost"
$env:REDIS_PORT = "6380"
$env:EDGAR4J_SECURITY_ENABLED = "false"
$env:SEC_USER_AGENT = "My Company sec-ops@mycompany.com"
$env:EDGAR4J_JOBS_TICKER_SYNC_ENABLED = "false"
$env:EDGAR4J_JOBS_SP500_SYNC_ENABLED = "false"
$env:EDGAR4J_JOBS_MARKET_DATA_SYNC_ENABLED = "false"
$env:EDGAR4J_JOBS_REALTIME_FILING_SYNC_ENABLED = "false"
$env:EDGAR4J_JOBS_FILING_SYNC_ENABLED = "false"
$env:EDGAR4J_JOBS_DATA_INTEGRITY_ENABLED = "false"
$env:EDGAR4J_STORAGE_DOWNLOAD_CACHE_PATH = "./data/download-cache-backfill-20260314d"

$tiingoEnvPath = Join-Path $repoRoot "tiingo.env"
if (Test-Path $tiingoEnvPath) {
    Get-Content $tiingoEnvPath | ForEach-Object {
        if ($_ -match '^(?<key>[^#=]+)=(?<value>.*)$') {
            $key = $matches["key"].Trim()
            $value = $matches["value"].Trim()
            if ($key -in @("TIINGO_API_TOKEN", "TIINGO_URL", "TIINGO_DATA_DIR")) {
                Set-Item -Path ("Env:" + $key) -Value $value
            }
        }
    }
}

$stdout = Join-Path $repoRoot $StdoutPath
$stderr = Join-Path $repoRoot $StderrPath

if (Test-Path $stdout) {
    Remove-Item $stdout -Force
}
if (Test-Path $stderr) {
    Remove-Item $stderr -Force
}

$process = Start-Process ".\mvnw.cmd" `
    -ArgumentList @("-q", "-DskipTests", "spring-boot:run") `
    -WorkingDirectory $repoRoot `
    -RedirectStandardOutput $stdout `
    -RedirectStandardError $stderr `
    -PassThru

$process.Id
