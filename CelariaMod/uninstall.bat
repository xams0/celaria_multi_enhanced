@echo off
rem Removes the Celaria cup mod (restores the original winExt.dll).
setlocal
set GAME=%~1
if "%GAME%"=="" set GAME=D:\SteamLibrary\steamapps\common\Celaria
if not exist "%GAME%\winExt_orig.dll" (
	echo The mod is not installed in "%GAME%"
	exit /b 1
)
move /y "%GAME%\winExt_orig.dll" "%GAME%\winExt.dll" >nul || exit /b 1
echo Cup mod removed from "%GAME%"
