param(
    [string]$Model = "BAAI/bge-reranker-base",
    [string]$Host = "0.0.0.0",
    [int]$Port = 8001
)

$ErrorActionPreference = "Stop"

Write-Host "==> preparing reranker venv"
if (-not (Test-Path ".venv-reranker")) {
    python -m venv .venv-reranker
}

$pythonExe = Join-Path ".venv-reranker" "Scripts\python.exe"
if (-not (Test-Path $pythonExe)) {
    throw "python virtual environment is invalid: $pythonExe not found"
}

Write-Host "==> installing dependencies"
& $pythonExe -m pip install -r "scripts/requirements-reranker.txt"

$env:BGE_RERANK_MODEL = $Model
Write-Host "==> starting reranker service"
Write-Host "model=$Model host=$Host port=$Port"
& $pythonExe -m uvicorn "scripts.reranker-server:app" --host $Host --port $Port
