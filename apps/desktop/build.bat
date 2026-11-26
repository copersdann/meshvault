@echo off
chcp 65001 >nul
setlocal
call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvarsall.bat" x64
if errorlevel 1 exit /b 1

set CMAKE=C:\tools-venv\Lib\site-packages\cmake\data\bin\cmake.exe
set NINJA=C:\tools-venv\Scripts\ninja.exe
set QT_PREFIX=C:\Qt\6.10.3\msvc2022_64
set BUILD_DIR=C:\tools\meshvault-build

"%CMAKE%" -S . -B "%BUILD_DIR%" -G Ninja -DCMAKE_PREFIX_PATH="%QT_PREFIX%" -DCMAKE_MAKE_PROGRAM="%NINJA%"
if errorlevel 1 exit /b 1

"%CMAKE%" --build "%BUILD_DIR%"
if errorlevel 1 exit /b 1

echo.
echo Built: %BUILD_DIR%\MeshVaultDesktop.exe
endlocal
