param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$FilePath = ".\sample-data\rag-intro.txt",
    [int]$ChunkSize = 128
)

$ErrorActionPreference = "Stop"

function Step($msg) {
    Write-Host ("`n==> " + $msg) -ForegroundColor Cyan
}

if (-not (Test-Path $FilePath)) {
    throw "File not found: $FilePath"
}

Step "1) health check"
$health = Invoke-RestMethod -Uri "$BaseUrl/api/health" -Method Get
Write-Host ("health: " + ($health | ConvertTo-Json -Compress))

Step "2) auth login root"
$loginBody = @{ userId = "root"; password = "123456" } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -ContentType "application/json" -Body $loginBody
Write-Host ("login: " + ($login | ConvertTo-Json -Compress))
if (-not $login.ok) { throw "root login failed" }

Step "3) prepare file context"
$fullPath = (Resolve-Path $FilePath).Path
$md5 = (Get-FileHash -Path $fullPath -Algorithm MD5).Hash.ToLower()
$fileId = "root:$md5"
$bytes = [System.IO.File]::ReadAllBytes($fullPath)
$totalChunks = [Math]::Ceiling($bytes.Length / $ChunkSize)
Write-Host "fileId=$fileId, totalChunks=$totalChunks"

Step "4) init upload"
$initBody = @{ fileId = $fileId; totalChunks = $totalChunks } | ConvertTo-Json
$init = Invoke-RestMethod -Uri "$BaseUrl/api/upload/init" -Method Post -ContentType "application/json" -Body $initBody
Write-Host ("init: " + ($init | ConvertTo-Json -Compress))

Step "5) upload chunks"
$tmpDir = Join-Path $env:TEMP ("rag-chunks-" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $tmpDir | Out-Null
for ($i = 0; $i -lt $totalChunks; $i++) {
    $start = $i * $ChunkSize
    $len = [Math]::Min($ChunkSize, $bytes.Length - $start)
    $chunk = New-Object byte[] $len
    [Array]::Copy($bytes, $start, $chunk, 0, $len)
    $chunkPath = Join-Path $tmpDir ("chunk-" + $i + ".bin")
    [System.IO.File]::WriteAllBytes($chunkPath, $chunk)
    $out = & curl.exe -sS -X POST `
        -F "fileId=$fileId" `
        -F "totalChunks=$totalChunks" `
        -F "chunkIndex=$i" `
        -F "file=@$chunkPath;type=application/octet-stream" `
        "$BaseUrl/api/upload/chunk"
    if ($LASTEXITCODE -ne 0) {
        throw "chunk upload failed at index $i"
    }
    Write-Host ("chunk#$i => " + $out)
}
Remove-Item -Path $tmpDir -Recurse -Force

Step "6) ask scoped with retry"
$found = $false
for ($retry = 1; $retry -le 12; $retry++) {
    Start-Sleep -Seconds 2
    $askBody = @{
        userId = "root"
        password = "123456"
        fileMd5 = $md5
        query = "RAG why combine BM25 and vector retrieval?"
        topK = 3
    } | ConvertTo-Json
    $ask = Invoke-RestMethod -Uri "$BaseUrl/api/qa/ask-scoped" -Method Post -ContentType "application/json" -Body $askBody
    $contexts = @($ask.contexts)
    Write-Host ("retry#$retry contexts=" + $contexts.Count)
    if ($contexts.Count -gt 0) {
        $found = $true
        Write-Host ("answer: " + $ask.answer)
        break
    }
}

if (-not $found) {
    throw "No contexts retrieved after retries"
}

Step "PASS"
