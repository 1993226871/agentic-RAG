@echo off
setlocal
set SCRIPT_DIR=%~dp0

echo.
echo ==> Stopping existing Agentic RAG Java processes...
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%stop-app.ps1"

echo.
echo ==> Restarting middleware and backend...
powershell -NoExit -ExecutionPolicy Bypass -File "%SCRIPT_DIR%up-all.ps1"
