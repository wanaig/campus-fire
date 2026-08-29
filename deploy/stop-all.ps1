# 停止校园消防巡检系统的所有服务
# 用法：powershell -ExecutionPolicy Bypass -File deploy\stop-all.ps1 [-KeepDocker]
param([switch]$KeepDocker)

$Root = Split-Path -Parent $PSScriptRoot

function Stop-ByPort($port, $name) {
    $lines = netstat -ano | Select-String ":$port\s.*LISTENING"
    $pids = @()
    foreach ($line in $lines) { $parts = ($line.ToString() -split '\s+') | Where-Object { $_ }; $pids += $parts[-1] }
    $pids = $pids | Sort-Object -Unique
    foreach ($procId in $pids) {
        try { Stop-Process -Id $procId -Force -ErrorAction Stop; Write-Host "已停止 $name (PID $procId)" -ForegroundColor Green }
        catch {}
    }
    if (-not $pids) { Write-Host "$name 未在运行" -ForegroundColor DarkGray }
}

Write-Host "停止后端（8080）…"
Stop-ByPort 8080 '后端'
Write-Host "停止前端（5173）…"
Stop-ByPort 5173 '前端'

if (-not $KeepDocker) {
    Write-Host "停止 MySQL / Redis 容器…"
    docker compose -f (Join-Path $Root 'deploy\docker-compose.dev.yml') stop 2>$null | Out-Null
    Write-Host "容器已停止（数据保留在 deploy\data）" -ForegroundColor Green
} else {
    Write-Host "保留 Docker 容器运行" -ForegroundColor DarkGray
}
Write-Host "全部服务已停止" -ForegroundColor Cyan
