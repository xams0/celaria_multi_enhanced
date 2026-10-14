# Relative mouse movement (raw input): mouse.ps1 -dx 20 -dy 0 -count 30 -ms 20
param([int]$dx = 0, [int]$dy = 0, [int]$count = 1, [int]$ms = 20)
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class M {
  [DllImport("user32.dll")] public static extern void mouse_event(uint f, int dx, int dy, uint d, IntPtr e);
}
"@
for ($i = 0; $i -lt $count; $i++) { [M]::mouse_event(1, $dx, $dy, 0, [IntPtr]::Zero); Start-Sleep -Milliseconds $ms }
