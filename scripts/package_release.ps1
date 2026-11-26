param(
    [string]$Version = "0.1.1",
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [switch]$Shortcut
)

$ErrorActionPreference = "Stop"

$buildExe = "C:\tools\meshvault-build\MeshVaultDesktop.exe"
$windeployqt = "C:\Qt\6.10.3\msvc2022_64\bin\windeployqt.exe"
$out = Join-Path $Root "release\MeshVault-$Version-win64"
$zip = Join-Path $Root "release\MeshVault-$Version-win64.zip"

if (-not (Test-Path $buildExe)) { throw "Нет сборки: $buildExe" }
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Path $out -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $out "src\desktop") -Force | Out-Null

# Только программные файлы: сервер, CLI-скрипты, исходники десктопа, документация.
Copy-Item $buildExe $out
Copy-Item (Join-Path $Root "server\app") (Join-Path $out "server\app") -Recurse
Copy-Item (Join-Path $Root "server\pyproject.toml") (Join-Path $out "server\pyproject.toml")
Copy-Item (Join-Path $Root "scripts") (Join-Path $out "scripts") -Recurse
Copy-Item (Join-Path $Root "apps\desktop\src") (Join-Path $out "src\desktop\src") -Recurse
Copy-Item (Join-Path $Root "apps\desktop\qml") (Join-Path $out "src\desktop\qml") -Recurse
Copy-Item (Join-Path $Root "apps\desktop\CMakeLists.txt") (Join-Path $out "src\desktop\CMakeLists.txt")
Copy-Item (Join-Path $Root "apps\desktop\build.bat") (Join-Path $out "src\desktop\build.bat")
Copy-Item (Join-Path $Root "docs") (Join-Path $out "docs") -Recurse
Copy-Item (Join-Path $Root "README.md") $out
Copy-Item (Join-Path $Root ".env.example") $out

# Убираем мусор разработки и любые модели/данные, если попали по ошибке.
Get-ChildItem $out -Recurse -Directory -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -in @("__pycache__", ".pytest_cache", ".ruff_cache", "data", "models", ".venv") } |
    Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
Get-ChildItem $out -Recurse -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Extension -in @(".pyc", ".pdb", ".ilk", ".stl", ".3mf", ".obj", ".glb", ".ply", ".amf", ".gcode", ".db", ".zip") } |
    Remove-Item -Force -ErrorAction SilentlyContinue

@"
MESHVault_DATA_DIR=./data
MESHVault_API_TOKEN=
MESHVault_SECRET_KEY=change-me-in-production
MESHVault_THUMBNAIL_SIZE=512
MESHVault_MAX_UPLOAD_MB=512
MESHVault_IMPORT_DIR=
MESHVault_IMPORT_INTERVAL_MIN=5
OPENBLAS_NUM_THREADS=1
"@ | Set-Content (Join-Path $out ".env") -Encoding UTF8

@"
@echo off
chcp 65001 >nul
cd /d "%~dp0server"
if exist .venv\Scripts\python.exe goto ready
where uv >nul 2>nul
if %errorlevel%==0 (
    uv venv .venv
    uv pip install -e .
) else (
    py -3 -m venv .venv
    .venv\Scripts\python.exe -m pip install -e .
)
:ready
echo SERVER_READY
"@ | Set-Content (Join-Path $out "setup_server.bat") -Encoding UTF8

@"
@echo off
chcp 65001 >nul
cd /d "%~dp0server"
set OPENBLAS_NUM_THREADS=1
start "" /B .venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000
cd /d "%~dp0"
start "" MeshVaultDesktop.exe
"@ | Set-Content (Join-Path $out "start.bat") -Encoding UTF8
Copy-Item (Join-Path $out "start.bat") (Join-Path $out "Запуск.bat") -Force

Write-Host "Deploying Qt runtime..."
& $windeployqt --no-translations --compiler-runtime --qmldir (Join-Path $Root "apps\desktop\qml") (Join-Path $out "MeshVaultDesktop.exe") | Out-Null

Write-Host "Creating archive: $zip"
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path (Join-Path $out "*") -DestinationPath $zip -CompressionLevel Optimal -Force

if ($Shortcut) {
    $lnk = Join-Path ([Environment]::GetFolderPath("Desktop")) ("MeshVault $Version (test).lnk")
    $ws = New-Object -ComObject WScript.Shell
    $linkObj = $ws.CreateShortcut([string]$lnk)
    $linkObj.TargetPath = Join-Path $out "start.bat"
    $linkObj.WorkingDirectory = $out
    $linkObj.IconLocation = (Join-Path $out "MeshVaultDesktop.exe") + ",0"
    $linkObj.Description = "MeshVault $Version (тестовый релиз)"
    $linkObj.Save()
    Write-Host "Shortcut: $lnk"
}

Write-Host "RELEASE_OK $out"
Write-Host ("ZIP_SIZE_MB=" + [math]::Round((Get-Item $zip).Length / 1MB, 1))
