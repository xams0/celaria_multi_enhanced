param([int]$count = 20, [int]$intervalMs = 500, [string]$prefix = "b", [double]$scale = 0.4)
# Takes a burst of screenshots (scaled) into the scratchpad: <prefix>_00.png ...
Add-Type -AssemblyName System.Windows.Forms, System.Drawing
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$b = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
$bmp = New-Object System.Drawing.Bitmap $b.Width, $b.Height
$g = [System.Drawing.Graphics]::FromImage($bmp)
$w = [int]($b.Width * $scale); $h = [int]($b.Height * $scale)
$sw = [System.Diagnostics.Stopwatch]::StartNew()
for ($i = 0; $i -lt $count; $i++) {
  $t0 = $sw.ElapsedMilliseconds
  $g.CopyFromScreen($b.Location, [System.Drawing.Point]::Empty, $b.Size)
  $small = New-Object System.Drawing.Bitmap $bmp, $w, $h
  $small.Save((Join-Path $dir ("{0}_{1:D2}.png" -f $prefix, $i)))
  $small.Dispose()
  $wait = $intervalMs - ($sw.ElapsedMilliseconds - $t0)
  if ($wait -gt 0) { Start-Sleep -Milliseconds $wait }
}
"burst done in $($sw.ElapsedMilliseconds) ms"
