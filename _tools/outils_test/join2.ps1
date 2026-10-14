# Launches the modded test game and joins the local test server (screen 2560x1440)
param([string]$server = "127.0.0.1:6599")
$d = "C:\Users\maxim\Desktop\Celaria Server Test V2 Claude Reverse Engi\Celaria_modtest"
$ui = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) "ui.ps1"
Get-Process Celaria -ErrorAction SilentlyContinue | Where-Object { $_.Path -like "*Celaria_modtest*" } | Stop-Process -Force
Start-Sleep 1
Copy-Item "C:\Users\maxim\Desktop\Celaria Server Test V2 Claude Reverse Engi\CelariaMod\out\winExt.dll" "$d\winExt.dll" -Force
Remove-Item "$d\celaria_mod.log" -ErrorAction SilentlyContinue
Start-Process -FilePath "$d\Celaria.exe" -ArgumentList "-nosteamrestart" -WorkingDirectory $d | Out-Null
Start-Sleep -Seconds 16
& $ui click 188 380 | Out-Null; Start-Sleep 2
& $ui click 188 760 | Out-Null; Start-Sleep 2
Add-Type -AssemblyName System.Windows.Forms
[System.Windows.Forms.SendKeys]::SendWait($server); Start-Sleep 1
& $ui key -key 13 -ms 80 | Out-Null
"joining"
