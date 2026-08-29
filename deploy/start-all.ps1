# 校园消防设施巡检管理系统 - 一键启动
# 用法：在项目根目录执行  powershell -ExecutionPolicy Bypass -File deploy\start-all.ps1
# 说明：启动 MySQL/Redis(Docker) -> 后端(8080) -> 管理后台前端(5173)，并自动打开浏览器
param(
    [switch]$SkipFrontend   # 只启动数据库和后端
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot

function Write-Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Wait-Http($url, $name, $seconds) {
    foreach ($i in 1..$seconds) {
        Start-Sleep -Seconds 1
        try { Invoke-RestMethod $url -TimeoutSec 2 | Out-Null; return $true } catch {}
        Write-Progress -Activity "等待 $name" -Status "$i / $seconds 秒"
    }
    return $false
}

# ---------- 1. Docker 数据库 ----------
Write-Step "启动 MySQL / Redis（Docker）"
$dockerOk = $false
try { docker info 2>$null | Out-Null; $dockerOk = $true } catch {}
if (-not $dockerOk) {
    Write-Host "Docker 引擎未运行，正在启动 Docker Desktop…" -ForegroundColor Yellow
    $dd = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    if (Test-Path $dd) { Start-Process $dd } else { throw "未找到 Docker Desktop，请先安装或手动启动" }
    foreach ($i in 1..60) {
        Start-Sleep -Seconds 3
        try { docker info 2>$null | Out-Null; $dockerOk = $true; break } catch {}
    }
    if (-not $dockerOk) { throw "Docker 引擎启动超时" }
}
docker compose -f (Join-Path $Root 'deploy\docker-compose.dev.yml') up -d | Out-Null
Write-Host "等待 MySQL 就绪…"
$mysqlOk = $false
foreach ($i in 1..60) {
    Start-Sleep -Seconds 2
    $s = docker inspect --format '{{.State.Health.Status}}' campus-fire-mysql 2>$null
    if ($s -eq 'healthy') { $mysqlOk = $true; break }
}
if (-not $mysqlOk) { throw "MySQL 启动超时，请执行 docker logs campus-fire-mysql 查看原因" }
Write-Host "MySQL / Redis 已就绪" -ForegroundColor Green

# ---------- 2. 后端 ----------
Write-Step "启动后端服务（端口 8080）"
$backendDir = Join-Path $Root 'backend'
$jar = Join-Path $backendDir 'target\campus-fire-backend-0.1.0-SNAPSHOT.jar'
$srcNewer = $false
if (Test-Path $jar) {
    $latest = Get-ChildItem (Join-Path $backendDir 'src') -Recurse -File |
        Where-Object { $_.Extension -in '.java', '.yml', '.sql' } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($latest -and $latest.LastWriteTime -gt (Get-Item $jar).LastWriteTime) { $srcNewer = $true }
}
if ($srcNewer -or -not (Test-Path $jar)) {
    Write-Host "源码有更新或尚未打包，正在重新构建后端（首次约1-3分钟）…"
    Push-Location $backendDir
    try { mvn -q package -DskipTests; if ($LASTEXITCODE -ne 0) { throw "后端构建失败" } }
    finally { Pop-Location }
}
Start-Process -FilePath 'java' -ArgumentList '-jar', $jar -WorkingDirectory $backendDir `
    -WindowStyle Minimized
if (-not (Wait-Http 'http://localhost:8080/api/health' '后端' 60)) { throw "后端启动超时" }
Write-Host "后端已就绪：http://localhost:8080/api" -ForegroundColor Green

# ---------- 3. 管理后台前端 ----------
if (-not $SkipFrontend) {
    Write-Step "启动管理后台前端（端口 5173）"
    $webDir = Join-Path $Root 'admin-web'
    if (-not (Test-Path (Join-Path $webDir 'node_modules'))) {
        Write-Host "首次运行，安装前端依赖（约1-2分钟）…"
        Push-Location $webDir
        try { npm install --no-audit --no-fund; if ($LASTEXITCODE -ne 0) { throw "前端依赖安装失败" } }
        finally { Pop-Location }
    }
    Start-Process -FilePath 'cmd' -ArgumentList '/c', 'npm run dev' -WorkingDirectory $webDir `
        -WindowStyle Minimized
    if (-not (Wait-Http 'http://127.0.0.1:5173' '前端' 40)) { throw "前端启动超时" }
    Write-Host "管理后台已就绪：http://127.0.0.1:5173" -ForegroundColor Green
    Start-Process 'http://127.0.0.1:5173'
}

# ---------- 4. 信息汇总 ----------
Write-Host @"

============================================================
  系统已启动
============================================================
  管理后台      http://127.0.0.1:5173
  后端接口      http://localhost:8080/api

  管理员账号    admin / 123456
  保安（演示）  guard / Guard@123
  采集员（演示） collector / Collect@123

  微信小程序：用微信开发者工具导入 miniprogram 目录，
  修改 utils/config.js 中的接口地址后即可体验。
  停止系统请运行 deploy\stop-all.ps1
============================================================
"@ -ForegroundColor White
