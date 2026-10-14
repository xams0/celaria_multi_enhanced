#!/bin/sh
# Builds Celaria-Server-main/CelariaServer.jar with the portable JDK (same steps as build.bat)
R="/c/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi"
J="$R/_tools/jdk-21.0.12.1+1/bin"
cd "$R/Celaria-Server-main" || exit 1
rm -rf build && mkdir build
find src -name "*.java" > build/sources.txt
"$J/javac" -nowarn -encoding ISO-8859-1 -cp "lib/gson-2.11.0.jar" -d build @build/sources.txt 2>&1 | grep -v "^warning: \[options\]\|^1 warning\|^3 warnings" || true
[ -f build/server/main.class ] || { echo "BUILD FAILED"; exit 1; }
rm build/sources.txt
printf 'Main-Class: server.main\nClass-Path: lib/gson-2.11.0.jar\n' > build/manifest.txt
"$J/jar" cfm CelariaServer.jar build/manifest.txt -C build . && rm -rf build && echo "built CelariaServer.jar"
