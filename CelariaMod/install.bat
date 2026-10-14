@echo off
rem Installs the Celaria cup mod into a Celaria folder (default: the Steam install).
rem usage: install.bat ["path\to\Celaria"]
setlocal
set GAME=%~1
if "%GAME%"=="" set GAME=D:\SteamLibrary\steamapps\common\Celaria
if not exist "%GAME%\Celaria.exe" (
	echo Celaria.exe not found in "%GAME%"
	exit /b 1
)
if not exist "%GAME%\winExt_orig.dll" (
	move "%GAME%\winExt.dll" "%GAME%\winExt_orig.dll" >nul || exit /b 1
)
copy /y "%~dp0out\winExt.dll" "%GAME%\winExt.dll" >nul || exit /b 1
echo Cup mod installed in "%GAME%". Log: celaria_mod.log next to Celaria.exe
