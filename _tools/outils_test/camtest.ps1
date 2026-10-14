# Spectator camera test during a round of the bots (the local player is observer)
$sp = Split-Path -Parent $MyInvocation.MyCommand.Path
$log = "C:\Users\maxim\Desktop\Celaria Server Test V2 Claude Reverse Engi\Celaria_modtest\celaria_mod.log"
$srv = "$sp\modsrv\srv.log"
function ViewNow { $l = Select-String -Path $log -Pattern "spectator view: (\d)|automatic spectator view" | Select-Object -Last 1; if ($l -eq $null) { return 0 }; if ($l.Line -match "automatic") { return 1 }; return [int]$l.Matches[0].Groups[1].Value }
function SetView([int]$want) { $cur = ViewNow; $n = ($want - $cur + 4) % 4; for ($i = 0; $i -lt $n; $i++) { & "$sp\ui.ps1" key -key 86 -ms 50 | Out-Null; Start-Sleep -Milliseconds 300 } }
$n0 = (Select-String -Path $srv -Pattern "Map Loaded" -SimpleMatch).Count
$sw = [Diagnostics.Stopwatch]::StartNew()
while ($sw.Elapsed.TotalSeconds -lt 120 -and (Select-String -Path $srv -Pattern "Map Loaded" -SimpleMatch).Count -eq $n0) { Start-Sleep -Milliseconds 100 }
"round started after $($sw.Elapsed.TotalSeconds) s"
Start-Sleep -Milliseconds 9000
SetView 1
& "$sp\burst.ps1" -count 3 -intervalMs 700 -prefix c1 -scale 0.3 | Out-Null
SetView 2
Start-Sleep -Milliseconds 300
& "$sp\burst.ps1" -count 2 -intervalMs 600 -prefix c2 -scale 0.3 | Out-Null
& "$sp\mouse.ps1" -dx 25 -dy 0 -count 36 -ms 15
& "$sp\burst.ps1" -count 1 -intervalMs 100 -prefix c3 -scale 0.3 | Out-Null
& "$sp\mouse.ps1" -dx 0 -dy 15 -count 20 -ms 15
& "$sp\burst.ps1" -count 1 -intervalMs 100 -prefix c4 -scale 0.3 | Out-Null
SetView 3
Start-Sleep -Milliseconds 300
& "$sp\burst.ps1" -count 1 -intervalMs 100 -prefix c5 -scale 0.3 | Out-Null
& "$sp\ui.ps1" scankey -key 90 -ms 1200 | Out-Null
& "$sp\burst.ps1" -count 1 -intervalMs 100 -prefix c6 -scale 0.3 | Out-Null
& "$sp\ui.ps1" scankey -key 68 -ms 1200 | Out-Null
& "$sp\burst.ps1" -count 1 -intervalMs 100 -prefix c7 -scale 0.3 | Out-Null
& "$sp\mouse.ps1" -dx -25 -dy 0 -count 36 -ms 15
& "$sp\burst.ps1" -count 1 -intervalMs 100 -prefix c8 -scale 0.3 | Out-Null
Add-Type -AssemblyName System.Drawing
$names = @("c1_00", "c1_01", "c1_02", "c2_00", "c2_01", "c3_00", "c4_00", "c5_00", "c6_00", "c7_00", "c8_00")
$files = $names | ForEach-Object { "$sp\$_.png" }
$f0 = [System.Drawing.Image]::FromFile($files[0]); $w = $f0.Width; $h = $f0.Height; $f0.Dispose()
$s = New-Object System.Drawing.Bitmap ($w * 4), ($h * 3); $g = [System.Drawing.Graphics]::FromImage($s); $font = New-Object System.Drawing.Font "Arial", 16
for ($i = 0; $i -lt $files.Count; $i++) { $img = [System.Drawing.Image]::FromFile($files[$i]); $g.DrawImage($img, ($i % 4) * $w, [math]::Floor($i / 4) * $h, $w, $h); $g.DrawString($names[$i], $font, [System.Drawing.Brushes]::Red, ($i % 4) * $w + 4, [math]::Floor($i / 4) * $h + 4); $img.Dispose() }
$s.Save("$sp\sheet_c.png")
Select-String -Path $log -Pattern "spectator view|automatic" | Select-Object -Last 6 | ForEach-Object { $_.Line }
