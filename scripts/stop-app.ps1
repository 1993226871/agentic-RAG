$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

$targets = Get-CimInstance Win32_Process -Filter "name='java.exe'" | Where-Object {
    $_.CommandLine -and
    $_.CommandLine -like ("*" + $projectRoot + "*") -and
    (
        $_.CommandLine -like "*spring-boot:run*" -or
        $_.CommandLine -like "*com.agenticrag.App*"
    )
}

if (-not $targets) {
    Write-Host "No existing project process found."
    exit 0
}

$targets | ForEach-Object {
    Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    Write-Host ("Stopped PID=" + $_.ProcessId)
}
