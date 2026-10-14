param([string]$prefix = "st", [int]$count = 24, [int]$cols = 4, [string]$out = "sheet.png")
Add-Type -AssemblyName System.Drawing
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$files = 0..($count - 1) | ForEach-Object { Join-Path $dir ("{0}_{1:D2}.png" -f $prefix, $_) } | Where-Object { Test-Path $_ }
$first = [System.Drawing.Image]::FromFile($files[0]); $tw = [int]($first.Width * 0.6); $th = [int]($first.Height * 0.6); $first.Dispose()
$rows = [math]::Ceiling($files.Count / $cols)
$sheet = New-Object System.Drawing.Bitmap ($tw * $cols), ($th * $rows); $g = [System.Drawing.Graphics]::FromImage($sheet)
$font = New-Object System.Drawing.Font "Arial", 14
for ($i = 0; $i -lt $files.Count; $i++) { $img = [System.Drawing.Image]::FromFile($files[$i]); $x = ($i % $cols) * $tw; $y = [math]::Floor($i / $cols) * $th; $g.DrawImage($img, $x, $y, $tw, $th); $g.DrawString("$i", $font, [System.Drawing.Brushes]::Red, $x + 2, $y + 2); $img.Dispose() }
$sheet.Save((Join-Path $dir $out)); "sheet $($files.Count)"
