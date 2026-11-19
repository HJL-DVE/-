#============================================================
# 项目根目录改名脚本
#
# 用途：把项目根目录从旧名改为新名。
# 背景：目录被 IntelliJ IDEA 占用时无法重命名，需先关闭 IDE。
#
# 用法（在项目根目录下执行）：
#   powershell -ExecutionPolicy Bypass -File scripts/rename-project-dir.ps1
#============================================================
[CmdletBinding()]
param(
    [string]$NewName = '奶茶店会员与储值营销系统'
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$parent = Split-Path -Parent $root
$currentName = Split-Path -Leaf $root

Write-Host "当前项目根目录: $root"
Write-Host "目标名称      : $NewName"

if ($currentName -eq $NewName) {
    Write-Host '目录名已是目标名称，无需修改。' -ForegroundColor Green
    exit 0
}

$target = Join-Path $parent $NewName
if (Test-Path -LiteralPath $target) {
    Write-Host "目标路径已存在，脚本中止：$target" -ForegroundColor Red
    exit 1
}

Write-Host '检查是否有进程占用该目录...'
$blockers = @()
foreach ($p in (Get-Process -ErrorAction SilentlyContinue | Where-Object {
        $_.ProcessName -match 'idea64|jetbrains|studio64|devenv|^Code$' })) {
    $blockers += "$($p.ProcessName) (pid $($p.Id))"
}
if ($blockers.Count -gt 0) {
    Write-Host '检测到可能占用目录的 IDE 进程：' -ForegroundColor Yellow
    $blockers | ForEach-Object { Write-Host "  - $_" }
    Write-Host '请先关闭 IDE 后重新运行本脚本。' -ForegroundColor Yellow
}

try {
    Rename-Item -LiteralPath $root -NewName $NewName -ErrorAction Stop
    Write-Host "改名成功：$target" -ForegroundColor Green
    Write-Host ''
    Write-Host '后续步骤：'
    Write-Host '  1) 在 IDE 中重新打开新路径下的 backend 项目'
    Write-Host '  2) cd <new-path>\backend; mvn clean'
    Write-Host '  3) 重新编译并启动'
} catch {
    Write-Host "改名失败：$($_.Exception.Message)" -ForegroundColor Red
    Write-Host ''
    Write-Host '常见原因：' -ForegroundColor Yellow
    Write-Host '  - IntelliJ IDEA 正在打开该项目（最常见）'
    Write-Host '  - 终端当前目录位于项目内'
    Write-Host '  - 文件资源管理器正在浏览该目录'
    exit 1
}
