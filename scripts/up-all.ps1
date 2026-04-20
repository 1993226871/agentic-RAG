param(
    [switch]$Rebuild,
    [switch]$ResetVolumes,
    [switch]$StartReranker,
    [switch]$NoBackend
)

$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

function Write-Step($msg) {
    Write-Host ("`n==> " + $msg) -ForegroundColor Cyan
}

function Require-Command($cmd) {
    if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
        throw "Missing command: $cmd. Install it and add to PATH first."
    }
}

function Start-NewPowerShell($title, $command) {
    $fullCommand = "Set-Location -LiteralPath '$ProjectRoot'; `$Host.UI.RawUI.WindowTitle = '$title'; $command"
    Start-Process powershell -ArgumentList @(
        "-NoExit",
        "-ExecutionPolicy", "Bypass",
        "-Command", $fullCommand
    ) | Out-Null
}

Require-Command "docker"
Require-Command "mvn"

Write-Step "Switch to project root"
Set-Location -LiteralPath $ProjectRoot

Write-Step "Validate Docker Compose config"
docker compose config | Out-Null

if ($ResetVolumes) {
    Write-Step "Reset containers and volumes (data will be removed)"
    docker compose down -v
}

$upArgs = @("compose", "up", "-d")
if ($Rebuild) {
    $upArgs += "--build"
}

Write-Step "Start middleware containers: redis/minio/es/rocketmq"
docker @upArgs

if (-not $NoBackend) {
    Write-Step "Start Spring Boot (new window)"
    Start-NewPowerShell "Agentic RAG - Spring Boot" "mvn spring-boot:run"
}

if ($StartReranker) {
    Write-Step "Start reranker service (new window)"
    Start-NewPowerShell "Agentic RAG - Reranker" ".\scripts\start-reranker.ps1"
}

Write-Step "Done"
Write-Host "Check middleware: docker compose ps"
Write-Host "Login page: http://localhost:8080/login.html"
Write-Host "Health API : http://localhost:8080/api/health"
Write-Host ""
Write-Host "Optional args:"
Write-Host "  -Rebuild       Build images before start"
Write-Host "  -ResetVolumes  Remove volumes first (danger)"
Write-Host "  -StartReranker Start reranker together"
Write-Host "  -NoBackend     Start only Docker middleware"
