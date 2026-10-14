#!/bin/sh
# runs the test server (scratchpad/modsrv), output in modsrv/srv.log
SP="$(dirname "$0")"
R="/c/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi"
J="$R/_tools/jdk-21.0.12.1+1/bin"
CP="$(cygpath -w "$SP/build");$(cygpath -w "$R/Celaria-Server-main/lib/gson-2.11.0.jar")"
cd "$SP/modsrv" && "$J/java" -cp "$CP" HeadlessMain server.conf > srv.log 2> srv.err
