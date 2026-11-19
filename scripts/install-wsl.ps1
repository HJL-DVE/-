# ============================================================
# WSL 3.0.5.0 Installer (auto-elevates to Administrator)
#
# Usage: right-click > Run with PowerShell
# ============================================================

$ErrorActionPreference = "Continue"
$msiPath = 'E:\Develop\wsl.3.0.5.0.x64.msi'

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host " WSL Installer" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

if (-not (Test-Path -LiteralPath $msiPath)) {
    Write-Host ("[ERROR] MSI not found: " + $msiPath) -ForegroundColor Red
    exit 1
}

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Host ""
    Write-Host "Administrator rights required. Requesting elevation..." -ForegroundColor Yellow
    Write-Host "(Click YES in the UAC dialog)" -ForegroundColor Yellow
    Write-Host ""
    $scriptPath = $MyInvocation.MyCommand.Path
    try {
        Start-Process -FilePath "powershell.exe" -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $scriptPath) -Verb RunAs -Wait
    } catch {
        Write-Host "[ERROR] Elevation was denied. Please run this script as Administrator manually." -ForegroundColor Red
        exit 1
    }
    exit 0
}

Write-Host "[1/4] Installing WSL 3.0.5.0 ..." -ForegroundColor Green
$proc = Start-Process -FilePath "msiexec.exe" -ArgumentList @("/i", $msiPath, "/quiet", "/norestart") -Wait -PassThru -WindowStyle Hidden

if ($proc.ExitCode -eq 0) {
    Write-Host "      [OK] Installed" -ForegroundColor Green
} elseif ($proc.ExitCode -eq 1618) {
    Write-Host "      [SKIP] Another installation is in progress" -ForegroundColor Yellow
} else {
    Write-Host ("      [FAIL] msiexec exit code: " + $proc.ExitCode) -ForegroundColor Red
    $logPath = Join-Path $env:TEMP "wsl_install.log"
    Start-Process -FilePath "msiexec.exe" -ArgumentList @("/i", $msiPath, "/quiet", "/norestart", "/l*v", $logPath) -Wait -WindowStyle Hidden
    Write-Host ("      Log: " + $logPath) -ForegroundColor Yellow
    exit 1
}

Write-Host "[2/4] Enabling Windows optional features ..." -ForegroundColor Green
$features = @("Microsoft-Windows-Subsystem-Linux", "VirtualMachinePlatform", "Microsoft-Hyper-V-All")
foreach ($name in $features) {
    try {
        $state = (Get-WindowsOptionalFeature -Online -FeatureName $name -ErrorAction Stop).State
        if ($state -ne "Enabled") {
            Write-Host ("      Enabling " + $name + " ...") -ForegroundColor Gray
            $r = Enable-WindowsOptionalFeature -Online -FeatureName $name -NoRestart -All -ErrorAction Stop
            Write-Host ("      " + $name + " done, RestartNeeded=" + $r.RestartNeeded) -ForegroundColor Gray
        } else {
            Write-Host ("      " + $name + " already enabled") -ForegroundColor Gray
        }
    } catch {
        Write-Host ("      Skip " + $name + " : " + $_.Exception.Message) -ForegroundColor DarkGray
    }
}

Write-Host "[3/4] Setting default WSL version to 2 ..." -ForegroundColor Green
try {
    & wsl.exe --set-default-version 2 2>&1 | ForEach-Object { Write-Host ("      " + $_) -ForegroundColor Gray }
} catch {
    Write-Host "      May require a reboot" -ForegroundColor Yellow
}

Write-Host "[4/4] Verifying ..." -ForegroundColor Green
Start-Sleep -Seconds 2
try {
    $ver = (& wsl.exe --version 2>&1 | Out-String)
    if ($ver -match "WSL version") {
        Write-Host "      [OK] WSL installed successfully" -ForegroundColor Green
    } else {
        Write-Host "      Installed. Run wsl --status after reboot." -ForegroundColor Yellow
    }
} catch {
    Write-Host "      Reboot recommended to verify" -ForegroundColor Yellow
}

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host " Done" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor White
Write-Host "  1. REBOOT your computer (required)" -ForegroundColor White
Write-Host "  2. Run: wsl --status" -ForegroundColor White
Write-Host "  3. Start Docker Desktop" -ForegroundColor White
Write-Host "  4. Return to TeaClub project for Docker Compose deployment" -ForegroundColor White
Write-Host ""
Write-Host "(Optional) install a Linux distro:" -ForegroundColor DarkGray
Write-Host "  wsl --install -d Ubuntu" -ForegroundColor DarkGray
Write-Host ""
Write-Host "Press any key to close..." -ForegroundColor DarkGray
try { $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown") } catch { }