#!/bin/sh
# Builds "out/Celaria Lanppart3.exe": native stub (extraction + injection of the mod) + payload:
#   jre/ (jlink), launcher.jar, server/ (CelariaServer.jar + gson), mod/lanppart3.dll, inject.exe, defaults/maps/
# Needs: _tools/jdk-21.0.12.1+1, _tools/llvm-mingw, the server jar (_tools/build_server.sh) and the mod (CelariaMod/build.sh).
set -e
cd "$(dirname "$0")"
L="$(pwd)"
R="$(cd .. && pwd)"
J="$R/_tools/jdk-21.0.12.1+1/bin"
BIN="$R/_tools/llvm-mingw-20261006-ucrt-x86_64/bin"
CC="$BIN/i686-w64-mingw32-gcc"
WINDRES="$BIN/i686-w64-mingw32-windres"
BUILD="$L/build"
STAGE="$BUILD/stage"
rm -rf "$BUILD" && mkdir -p "$BUILD/classes" "$STAGE/server/lib" "$STAGE/mod" "$STAGE/defaults/maps" out

echo "== launcher (Java)"
"$J/javac" -encoding UTF-8 --release 21 -d "$BUILD/classes" java/src/lp3/*.java
VERSION="$(tr -d ' \r\n' < VERSION)"
printf '%s' "$VERSION" > "$BUILD/classes/version.txt"
printf 'Main-Class: lp3.Main\n' > "$BUILD/manifest.txt"
"$J/jar" cfm "$STAGE/launcher.jar" "$BUILD/manifest.txt" -C "$BUILD/classes" .

echo "== icon"
"$J/java" -cp "$STAGE/launcher.jar" lp3.Main --icon "$BUILD/icon.ico"

echo "== stub + packer (native)"
cp stub/app.rc "$BUILD/app.rc"
(cd "$BUILD" && "$WINDRES" app.rc -O coff -o app.res)
"$CC" -O2 -Wall -D_WIN32_WINNT=0x0A00 -municode -mwindows -o "$BUILD/stub.exe" stub/lanppart3.c "$BUILD/app.res" -lcabinet -lshell32
"$CC" -O2 -Wall -D_WIN32_WINNT=0x0A00 -municode -o "$BUILD/packer.exe" stub/packer.c -lcabinet
cp "$BUILD/stub.exe" "$STAGE/inject.exe"

echo "== server, mod, maps"
cp "$R/Celaria-Server-main/CelariaServer.jar" "$STAGE/server/"
cp "$R/Celaria-Server-main/lib/gson-2.11.0.jar" "$STAGE/server/lib/"
cp "$R/CelariaMod/out/lanppart3.dll" "$STAGE/mod/"
cp "$R/Celaria-Server-main/maps/"*.cmap "$STAGE/defaults/maps/"

echo "== Java runtime (jlink)"
"$J/jlink" --add-modules java.base,java.desktop,java.sql,java.logging,jdk.unsupported,jdk.crypto.ec \
	--strip-debug --no-header-files --no-man-pages --compress zip-6 --output "$STAGE/jre"

echo "== pack"
BUILD_ID="v$VERSION-$(date +%Y%m%d-%H%M%S)"
"$BUILD/packer.exe" "$(cygpath -w "$BUILD/stub.exe")" "$(cygpath -w "$STAGE")" "$BUILD_ID" "$(cygpath -w "$L/out/Celaria Lanppart3.exe")"
ls -la "$L/out/Celaria Lanppart3.exe"
echo "built out/Celaria Lanppart3.exe ($BUILD_ID)"
