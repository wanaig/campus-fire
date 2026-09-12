#Requires -RunAsAdministrator

$ErrorActionPreference = 'Stop'
$ruleName = 'Campus Fire Backend (TCP 8080)'
$existingRule = Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue

if ($existingRule) {
    Set-NetFirewallRule -DisplayName $ruleName -Enabled True -Direction Inbound -Action Allow -Profile Any
    Write-Host "防火墙规则已启用：$ruleName" -ForegroundColor Green
    exit 0
}

New-NetFirewallRule `
    -DisplayName $ruleName `
    -Description '允许同一局域网内的手机访问校园消防巡检后端。' `
    -Direction Inbound `
    -Action Allow `
    -Protocol TCP `
    -LocalPort 8080 `
    -RemoteAddress LocalSubnet `
    -Profile Any | Out-Null

Write-Host "已允许本地子网访问 TCP 8080。" -ForegroundColor Green
