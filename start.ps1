#Requires -Version 5.1

$root = $PSScriptRoot

Write-Host ""
Write-Host "  Hero App - Dev Launcher" -ForegroundColor Cyan
Write-Host "  Press CTRL+C to stop both services" -ForegroundColor DarkGray
Write-Host ""

$backendDir  = Join-Path $root "hero-backend"
$frontendDir = Join-Path $root "hero-frontend"

# Free port 8280 if already in use
$stale = Get-NetTCPConnection -LocalPort 8280 -State Listen -ErrorAction SilentlyContinue |
         Select-Object -ExpandProperty OwningProcess
if ($stale) {
    Write-Host "  [!] Port 8280 in use (PID $stale) - freeing it..." -ForegroundColor Yellow
    $stale | ForEach-Object { taskkill /F /T /PID $_ 2>&1 | Out-Null }
    Start-Sleep -Seconds 1
}

$backendCmd = @"
`$host.UI.RawUI.WindowTitle = 'Hero - BACKEND'
Write-Host ''
Write-Host '  == BACKEND (Spring Boot) ==' -ForegroundColor Green
Write-Host ''
Set-Location '$backendDir'
.\mvnw.cmd spring-boot:run
"@

$frontendCmd = @"
`$host.UI.RawUI.WindowTitle = 'Hero - FRONTEND'
Write-Host ''
Write-Host '  == FRONTEND (Vite + React) ==' -ForegroundColor Blue
Write-Host ''
Set-Location '$frontendDir'
npm run dev
"@

$backendProc  = Start-Process powershell.exe -PassThru -ArgumentList "-NoExit", "-Command", $backendCmd
$frontendProc = Start-Process powershell.exe -PassThru -ArgumentList "-NoExit", "-Command", $frontendCmd

Write-Host "  Backend   => http://localhost:8280  (PID $($backendProc.Id))" -ForegroundColor Green
Write-Host "  Frontend  => http://localhost:5171  (PID $($frontendProc.Id))" -ForegroundColor Blue
Write-Host ""
Write-Host "  Both services are starting in separate windows." -ForegroundColor DarkGray
Write-Host "  Keep this window open. Press CTRL+C here to stop everything." -ForegroundColor DarkGray
Write-Host ""

try {
    while (!$backendProc.HasExited -and !$frontendProc.HasExited) {
        Start-Sleep -Milliseconds 500
    }
    if ($backendProc.HasExited)  { Write-Host "  [!] Backend  stopped unexpectedly." -ForegroundColor Red }
    if ($frontendProc.HasExited) { Write-Host "  [!] Frontend stopped unexpectedly." -ForegroundColor Red }
}
finally {
    Write-Host ""
    Write-Host "  Stopping all services..." -ForegroundColor Yellow
    taskkill /F /T /PID $backendProc.Id  2>&1 | Out-Null
    taskkill /F /T /PID $frontendProc.Id 2>&1 | Out-Null
    Write-Host "  Done." -ForegroundColor Yellow
    Write-Host ""
}
