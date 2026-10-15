param([string]$name = "Celaria")
# Backup of the project into Sauvegardes\<name>_<date>.zip (sources, launcher exe, test scripts, notes)
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$stamp = Get-Date -Format "yyyy-MM-dd_HHmm"
$stage = Join-Path $env:TEMP "claude\backup_stage"
if (Test-Path $stage) { Remove-Item -Recurse -Force $stage }
New-Item -ItemType Directory -Force $stage | Out-Null
Copy-Item -Recurse (Join-Path $root "Celaria-Server-main") $stage
Copy-Item -Recurse (Join-Path $root "CelariaMod") $stage
$l = Join-Path $stage "Lanppart3Launcher"
New-Item -ItemType Directory -Force (Join-Path $l "out") | Out-Null
foreach ($d in @("java", "stub", "devtest")) { Copy-Item -Recurse (Join-Path $root "Lanppart3Launcher\$d") (Join-Path $l $d) }
foreach ($f in @("build.sh", "README.md", "VERSION", "release_notes.md")) { Copy-Item (Join-Path $root "Lanppart3Launcher\$f") $l }
Copy-Item (Join-Path $root "Lanppart3Launcher\out\Celaria Lanppart3.exe") (Join-Path $l "out")
Copy-Item -Recurse (Join-Path $root "_tools\outils_test") (Join-Path $stage "outils_test")
foreach ($d in @("_tools\maps", "Maps_Lanppart3")) { if (Test-Path (Join-Path $root $d)) { Copy-Item -Recurse (Join-Path $root $d) (Join-Path $stage (Split-Path -Leaf $d)) } }
foreach ($f in @("_tools\build_server.sh", "_tools\release.mjs", "_tools\backup.ps1", "REVERSE_NOTES.md", "README.md", ".gitignore")) { Copy-Item (Join-Path $root $f) $stage }
$dst = Join-Path $root "Sauvegardes\$($name)_$stamp.zip"
Compress-Archive -Path "$stage\*" -DestinationPath $dst -Force
Get-Item $dst | Select-Object Name, Length
