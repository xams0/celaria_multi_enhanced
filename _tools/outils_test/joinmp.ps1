# Launches the modded test game and joins the local test server (127.0.0.1:6599)
$d = "C:\Users\maxim\Desktop\Celaria Server Test V2 Claude Reverse Engi\Celaria_modtest"
$ui = "C:\Users\maxim\AppData\Local\Temp\claude\C--Users-maxim-Desktop-Celaria-Server-Test-V2-Claude-Reverse-Engi\e2f2646e-cc88-4d7e-b198-ed179ec629bf\scratchpad\ui.ps1"
Get-Process Celaria -ErrorAction SilentlyContinue | Where-Object { $_.Path -like "*Celaria_modtest*" } | Stop-Process -Force
Remove-Item "$d\celaria_mod.log" -ErrorAction SilentlyContinue
Start-Process -FilePath "$d\Celaria.exe" -ArgumentList "-nosteamrestart" -WorkingDirectory $d | Out-Null
Start-Sleep -Seconds 16
& $ui click 141 345 | Out-Null; Start-Sleep 2
& $ui click 140 610 | Out-Null; Start-Sleep 2
Add-Type -AssemblyName System.Windows.Forms
[System.Windows.Forms.SendKeys]::SendWait("127.0.0.1:6599"); Start-Sleep 1
& $ui key -key 13 -ms 80 | Out-Null
"joining"
