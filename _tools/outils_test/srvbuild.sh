#!/bin/sh
# rebuilds the test server classes (scratchpad/build) from CelariaServer.jar + HeadlessMain
SP="$(dirname "$0")"
R="/c/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi"
J="$R/_tools/jdk-21.0.12.1+1/bin"
rm -rf "$SP/build" && mkdir -p "$SP/build" && cd "$SP/build" && "$J/jar" xf "$(cygpath -w "$R/Celaria-Server-main/CelariaServer.jar")" && \
"$J/javac" -nowarn -encoding ISO-8859-1 -cp "$(cygpath -w "$SP/build");$(cygpath -w "$R/Celaria-Server-main/lib/gson-2.11.0.jar")" -d "$(cygpath -w "$SP/build")" "$(cygpath -w "$SP/test/HeadlessMain.java")" && echo "test server built"
