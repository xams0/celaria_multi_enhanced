#!/bin/sh
# dev: recompiles the launcher (Java) and runs it with the app folder extracted by the exe
R="/c/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi"
J="$R/_tools/jdk-21.0.12.1+1/bin"
APP="$(ls -d "$LOCALAPPDATA"/Lanppart3/app/*/ | head -1)"
powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='javaw.exe' or Name='java.exe'\" | Where-Object { \$_.CommandLine -match 'launcher.jar|lp3.Main' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }"
D="$R/Lanppart3Launcher/build/dev"
rm -rf "$D" && mkdir -p "$D" && "$J/javac" -encoding UTF-8 -d "$D" "$R/Lanppart3Launcher/java/src/lp3/"*.java || exit 1
cd "$APP" && "$J/javaw" -Dlp3.app="$(cygpath -w "$APP")" -cp "$(cygpath -w "$D")" lp3.Main &
sleep 4; echo "dev launcher started"
