#!/bin/sh
# runs the test server (headless jar) in _tools/outils_test/modsrv, log in modsrv/srv.log
R="/c/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi"
cd "$R/_tools/outils_test/modsrv" && "$R/_tools/jdk-21.0.12.1+1/bin/java" -cp "$(cygpath -w "$R/Celaria-Server-main/CelariaServer.jar");$(cygpath -w "$R/Celaria-Server-main/lib/gson-2.11.0.jar")" server.main --headless --config server.conf > srv.log 2> srv.err
