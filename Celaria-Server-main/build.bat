@echo off
rem Builds CelariaServer.jar (needs a JDK: JAVA_HOME or javac/jar on the PATH, and lib\gson-*.jar)
setlocal
cd /d "%~dp0"

set JAVAC=javac
set JAR=jar
if not "%JAVA_HOME%"=="" (
	set JAVAC="%JAVA_HOME%\bin\javac"
	set JAR="%JAVA_HOME%\bin\jar"
)

set GSON=
for %%f in (lib\gson-*.jar) do set GSON=%%f
if "%GSON%"=="" (
	echo Missing lib\gson-*.jar : download it from https://repo1.maven.org/maven2/com/google/code/gson/gson/
	exit /b 1
)

if exist build rmdir /s /q build
mkdir build
setlocal enabledelayedexpansion
for /r src %%f in (*.java) do (
	set "p=%%f"
	echo "!p:\=/!">> build\sources.txt
)
endlocal
%JAVAC% -nowarn -encoding ISO-8859-1 -cp "%GSON%" -d build @build\sources.txt
if errorlevel 1 exit /b 1
del build\sources.txt

echo Main-Class: server.main> build\manifest.txt
echo Class-Path: %GSON:\=/%>> build\manifest.txt
%JAR% cfm CelariaServer.jar build\manifest.txt -C build .
if errorlevel 1 exit /b 1
rmdir /s /q build

echo Built CelariaServer.jar - start it with: java -jar CelariaServer.jar
