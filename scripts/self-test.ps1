param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$FilePath = ".\sample-data\rag-intro.txt",
    [int]$ChunkSize = 60,
    [string]$Mode = "mock",
    [string]$Query = "RAG 为什么要同时使用 BM25 和向量检索"
)

$ErrorActionPreference = "Stop"

function Write-Step($text) {
    Write-Host ("`n==> " + $text) -ForegroundColor Cyan
}

if (-not (Test-Path $FilePath)) {
    throw "文件不存在: $FilePath"
}

$fileId = "selftest-" + [DateTimeOffset]::Now.ToUnixTimeMilliseconds()

Write-Step "1. 健康检查"
$health = Invoke-RestMethod -Uri "$BaseUrl/api/health" -Method Get
Write-Host ("Health: " + ($health | ConvertTo-Json -Compress))

$bytes = [System.IO.File]::ReadAllBytes((Resolve-Path $FilePath))
$totalChunks = [Math]::Ceiling($bytes.Length / $ChunkSize)

Write-Step "2. 初始化上传任务 fileId=$fileId totalChunks=$totalChunks"
$initBody = @{ fileId = $fileId; totalChunks = $totalChunks } | ConvertTo-Json
$initResp = Invoke-RestMethod -Uri "$BaseUrl/api/upload/init" -Method Post -ContentType "application/json" -Body $initBody
Write-Host ("Init: " + ($initResp | ConvertTo-Json -Compress))

Write-Step "3. 分片上传"
$handler = New-Object System.Net.Http.HttpClientHandler
$client = New-Object System.Net.Http.HttpClient($handler)
for ($i = 0; $i -lt $totalChunks; $i++) {
    $start = $i * $ChunkSize
    $length = [Math]::Min($ChunkSize, $bytes.Length - $start)
    $chunkBytes = New-Object byte[] $length
    [Array]::Copy($bytes, $start, $chunkBytes, 0, $length)

    $multipart = New-Object System.Net.Http.MultipartFormDataContent
    $multipart.Add((New-Object System.Net.Http.StringContent($fileId)), "fileId")
    $multipart.Add((New-Object System.Net.Http.StringContent("$totalChunks")), "totalChunks")
    $multipart.Add((New-Object System.Net.Http.StringContent("$i")), "chunkIndex")
    $fileContent = New-Object System.Net.Http.ByteArrayContent($chunkBytes)
    $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("application/octet-stream")
    $multipart.Add($fileContent, "file", "chunk-$i.bin")

    $response = $client.PostAsync("$BaseUrl/api/upload/chunk", $multipart).Result
    $body = $response.Content.ReadAsStringAsync().Result
    Write-Host ("Chunk#$i => " + $body)
}
$client.Dispose()

Write-Step "4. 查询分片状态（chunkIndex=1）"
$statusResp = Invoke-RestMethod -Uri "$BaseUrl/api/upload/status?fileId=$fileId&chunkIndex=1" -Method Get
Write-Host ("Status: " + ($statusResp | ConvertTo-Json -Compress))

if ($Mode -eq "mock") {
    Write-Step "5. mock模式触发一次异步消费"
    $consumeResp = Invoke-RestMethod -Uri "$BaseUrl/api/admin/consume-once" -Method Post
    Write-Host ("Consume: " + ($consumeResp | ConvertTo-Json -Compress))
} else {
    Write-Step "5. real模式等待RocketMQ异步消费"
    Start-Sleep -Seconds 3
}

Write-Step "6. 在线问答"
$askBody = @{ query = $Query; topK = 3 } | ConvertTo-Json
$askResp = Invoke-RestMethod -Uri "$BaseUrl/api/qa/ask" -Method Post -ContentType "application/json" -Body $askBody
Write-Host ("QA: " + ($askResp | ConvertTo-Json -Depth 6))

Write-Step "完成：系统链路已跑完"
